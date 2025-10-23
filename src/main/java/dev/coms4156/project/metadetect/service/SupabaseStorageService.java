package dev.coms4156.project.metadetect.supabase;

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
 * Minimal Supabase Storage client (private bucket).
 * Upload: PUT /storage/v1/object/{bucket}/{path}
 * Sign:   POST /storage/v1/object/sign/{bucket}/{path}  { "expiresIn": "seconds" }
 */
@Service
public class SupabaseStorageService {

  private static final Logger log = LoggerFactory.getLogger(SupabaseStorageService.class);

  private final WebClient supabase;
  private final String projectBase;         // e.g. https://xyz.supabase.co
  private final String bucket;              // e.g. metadetect-images
  private final int signedUrlTtlSeconds;    // e.g. 600
  private final String supabaseAnonKey;     // required for Storage API

  private final WebClient supabaseNoCtOnDelete;


  /**
   * Constructs a Supabase-backed storage service for image uploads and signed URL retrieval.
   *
   * @param supabaseWebClient configured WebClient pointing at the Supabase REST endpoint
   * @param projectBase the Supabase base URL (project URL)
   * @param bucket the storage bucket name used for persistence
   * @param signedUrlTtlSeconds TTL in seconds for generated signed download URLs
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

  /** Uploads bytes to /storage/v1/object/{bucket}/{path}. Returns the path used. */
  public String uploadObject(byte[] bytes,
                             String contentType,
                             String objectPath,   // "<userId>/<imageId>--<filename>"
                             String bearerJwt) {

    // URL-encode each path segment to avoid 400s from special chars
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
        .header("x-upsert", "true") // allow overwrite if same key
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
      return objectPath; // we uploaded to this path
    } catch (WebClientResponseException e) {
      // Re-throw with a concise message; upstream logs already have details
      throw new RuntimeException("Supabase upload failed: " + e.getStatusCode(), e);
    }
  }

  /** Creates a signed URL for a private object. Returns an absolute https URL. */
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
    // System.out.println(signedFromApi);

    return projectBase + "/storage/v1" + signedFromApi;
  }


  // Supabase returns: {"signedURL":"/storage/v1/object/sign/..."}
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

  /** Deletes one object via POST /storage/v1/object/{bucket}/remove. */
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
    }
  }
}
