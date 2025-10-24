package dev.coms4156.project.metadetect.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

/**
 * Minimal client for Supabase Storage (private bucket).
 * Endpoints used:
 * - Upload: PUT  /storage/v1/object/{bucket}/{path}
 * - Sign:   POST /storage/v1/object/sign/{bucket}/{path}  body: {"expiresIn": seconds}
 * - Delete: DELETE /storage/v1/object/{bucket}/{path}
 * Notes:
 * - This service expects a WebClient already pointed at the project base URL and
 *   with sane timeouts. It supplies auth headers per request.
 * - For deletes, some gateways reject a DELETE with Content-Type, so we strip it.
 */
@Service
public class SupabaseStorageService {

  private static final Logger log = LoggerFactory.getLogger(SupabaseStorageService.class);

  private final WebClient supabase;
  private final String projectBase;       // e.g., https://xyz.supabase.co
  private final String bucket;            // e.g., metadetect-images
  private final int signedUrlTtlSeconds;  // e.g., 600
  private final String supabaseAnonKey;   // required by Storage API

  // Variant of WebClient that removes Content-Type on DELETE requests.
  private final WebClient supabaseNoCtOnDelete;

  /**
   * Constructs a Supabase Storage adapter used for upload/sign/delete operations.
   *
   * @param supabaseWebClient base WebClient for the Supabase project
   * @param projectBase Supabase project URL (no trailing slash required)
   * @param bucket storage bucket name
   * @param signedUrlTtlSeconds TTL, in seconds, for signed download URLs
   * @param supabaseAnonKey anon/service key sent as `apikey` to Storage API
   */
  public SupabaseStorageService(
      WebClient supabaseWebClient,
      @Value("${metadetect.supabase.url}") String projectBase,
      @Value("${metadetect.supabase.storageBucket}") String bucket,
      @Value("${metadetect.supabase.signedUrlTtlSeconds}") int signedUrlTtlSeconds,
      @Value("${metadetect.supabase.anonKey}") String supabaseAnonKey
  ) {
    this.supabase = supabaseWebClient;
    this.projectBase = projectBase;
    this.bucket = bucket;
    this.signedUrlTtlSeconds = signedUrlTtlSeconds;
    this.supabaseAnonKey = supabaseAnonKey;

    // Filter that removes Content-Type header for DELETE requests.
    ExchangeFilterFunction stripCtOnDelete = (req, next) -> {
      if (req.method() == HttpMethod.DELETE) {
        ClientRequest mutated = ClientRequest.from(req)
            .headers(h -> h.remove(HttpHeaders.CONTENT_TYPE))
            .build();
        return next.exchange(mutated);
      }
      return next.exchange(req);
    };

    this.supabaseNoCtOnDelete = supabase.mutate()
      .filter(stripCtOnDelete)
      .build();
  }

  /**
   * Uploads bytes to: PUT /storage/v1/object/{bucket}/{path}.
   * Path handling:
   * - Each segment is URL-encoded (defensive against spaces or special chars).
   * - Caller provides the logical `objectPath`, e.g. "userId/imageId--filename".
   *
   * @param bytes file contents
   * @param contentType MIME type (defaults to application/octet-stream)
   * @param objectPath storage key within the bucket
   * @param bearerJwt caller's user JWT for RLS/policy checks
   * @return the objectPath that was written
   */
  public String uploadObject(byte[] bytes,
                             String contentType,
                             String objectPath,
                             String bearerJwt) {

    // URL-encode segments to avoid 400s on special characters.
    String encoded = UriComponentsBuilder.newInstance()
        .pathSegment(objectPath.split("/"))
        .build()
        .encode()
        .toUriString()
        .substring(1); // drop leading '/'

    String url = projectBase + "/storage/v1/object/" + bucket + "/" + encoded;

    try {
      supabase
        .put()
        .uri(url)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerJwt)
        .header("apikey", supabaseAnonKey)
        .header("x-upsert", "true") // allow overwrite if the same key is reused
        .contentType(MediaType.parseMediaType(
          contentType == null || contentType.isBlank()
            ? MediaType.APPLICATION_OCTET_STREAM_VALUE
            : contentType))
        .bodyValue(bytes)
        .retrieve()
        .bodyToMono(String.class)
        .onErrorResume(WebClientResponseException.class, ex -> {
          log.error("Supabase upload failed: status={}, body={}",
              ex.getStatusCode(), ex.getResponseBodyAsString());
          return Mono.error(ex);
        })
          .block();

      return objectPath;
    } catch (WebClientResponseException e) {
      // Upstream logs include status/body; rethrow concise summary.
      throw new RuntimeException("Supabase upload failed: " + e.getStatusCode(), e);
    }
  }

  /**
   * Creates a signed URL via.
   * POST /storage/v1/object/sign/{bucket}/{path} body: {"expiresIn": seconds}
   *
   * @param storagePath object key inside the bucket
   * @param userBearerJwt caller's user JWT for Storage policy
   * @return absolute https URL suitable for direct client download
   */
  public String createSignedUrl(String storagePath, String userBearerJwt) {
    String url = projectBase + "/storage/v1/object/sign/" + bucket + "/" + storagePath;
    String bodyJson = "{\"expiresIn\":" + signedUrlTtlSeconds + "}";

    String signedFromApi = supabase.post()
        .uri(url)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userBearerJwt)
        .header("apikey", supabaseAnonKey)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(bodyJson.getBytes(StandardCharsets.UTF_8))
        .retrieve()
        .bodyToMono(String.class)
        .timeout(Duration.ofSeconds(10))
        .map(SupabaseStorageService::extractSignedUrlFromJson)
        .block();

    // Supabase returns a path like "/storage/v1/object/sign/..."; make it absolute.
    return projectBase + "/storage/v1" + signedFromApi;
  }

  /**
   * Extracts "signedURL" from a minimal Supabase JSON response.
   * Expected shape: {"signedURL":"/storage/v1/object/sign/..."}
   * This avoids pulling a JSON library into this tiny class.
   */
  private static String extractSignedUrlFromJson(String json) {
    if (json == null) {
      return null;
    }
    int i = json.indexOf("\"signedURL\"");
    if (i < 0) {
      return null;
    }
    int colon = json.indexOf(':', i);
    int q1 = json.indexOf('"', colon + 1);
    int q2 = json.indexOf('"', q1 + 1);
    if (q1 < 0 || q2 < 0) {
      return null;
    }
    return json.substring(q1 + 1, q2);
  }

  /**
   * Deletes one object via: DELETE /storage/v1/object/{bucket}/{path}.
   * If the object is missing (404), deletion is considered idempotent and
   * succeeds silently. Other errors are logged and rethrown.
   *
   * @param objectPath object key to delete
   * @param bearer caller's user JWT for Storage policy
   */
  public void deleteObject(String objectPath, String bearer) {
    if (objectPath == null || objectPath.isBlank()) {
      return;
    }

    String url = projectBase + "/storage/v1/object/" + bucket + "/" + objectPath;

    try {
      supabaseNoCtOnDelete
        .delete()
        .uri(url)
        .headers(h -> {
          h.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
          h.set("apikey", supabaseAnonKey);
        })
        .retrieve()
        .bodyToMono(Void.class)
          .block();
    } catch (WebClientResponseException e) {
      if (e.getStatusCode().value() != 404) {
        log.error("Supabase delete failed {} {} body={}",
            e.getStatusCode(), e.getMessage(), e.getResponseBodyAsString());
        throw new RuntimeException("Supabase delete failed: " + e.getStatusCode(), e);
      }
      // else: 404 treated as successful idempotent delete
    }
  }
}
