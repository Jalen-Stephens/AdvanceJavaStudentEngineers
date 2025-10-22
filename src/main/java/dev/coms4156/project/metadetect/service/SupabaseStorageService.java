package dev.coms4156.project.metadetect.supabase;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Minimal Supabase Storage client (private bucket).
 * Uses Supabase REST endpoints:
 *   - Upload:  POST  /storage/v1/object/{bucket}/{path}
 *   - Sign:    POST  /storage/v1/object/sign/{bucket}/{path} { "expiresIn": "secconds" }
 */
@Service
public class SupabaseStorageService {

  private final WebClient supabase;
  private final String projectBase; // e.g., https://xyz.supabase.co
  private final String bucket;
  private final int signedUrlTtlSeconds;

  /**
   * Constructs a Supabase-backed storage service responsible for binary uploads
   * and issuing signed download URLs for restricted access. This abstraction
   * hides HTTP details behind a WebClient and ensures correct bucket scoping.
   *
   * @param supabaseWebClient preconfigured WebClient bound to the Supabase project
   * @param projectBase base Supabase project URL (without trailing slash)
   * @param bucket name of the Supabase storage bucket used for images
   * @param signedUrlTtlSeconds lifetime (in seconds) of signed access URLs
   */
  public SupabaseStorageService(
      WebClient supabaseWebClient,
      @Value("${metadetect.supabase.url}") String projectBase,
      @Value("${metadetect.supabase.storageBucket}") String bucket,
      @Value("${metadetect.supabase.signedUrlTtlSeconds}") int signedUrlTtlSeconds
  ) {
    this.supabase = supabaseWebClient;
    this.projectBase = projectBase.endsWith("/")
      ? projectBase.substring(0, projectBase.length() - 1) : projectBase;
    this.bucket = bucket;
    this.signedUrlTtlSeconds = signedUrlTtlSeconds;
  }


  /** Uploads bytes to /storage/v1/object/{bucket}/{path}. Returns the full storage path used. */
  public String uploadObject(byte[] content,
                             String contentType,
                             String storagePath,
                             String userBearerJwt) {
    Assert.hasText(storagePath, "storagePath required");
    String url = projectBase + "/storage/v1/object/" + bucket + "/" + storagePath;

    MultipartBodyBuilder mp = new MultipartBodyBuilder();
    mp.part("file", content)
        .filename(filenameFromPath(storagePath))
        .contentType(MediaType.parseMediaType(contentType != null
          ? contentType : MediaType.APPLICATION_OCTET_STREAM_VALUE));

    supabase.post()
      .uri(url)
      .header(HttpHeaders.AUTHORIZATION, "Bearer " + userBearerJwt)
      .contentType(MediaType.MULTIPART_FORM_DATA)
      .body(BodyInserters.fromMultipartData(mp.build()))
      .retrieve()
      .bodyToMono(String.class)
      .timeout(Duration.ofSeconds(20))
        .block();

    return bucket + "/" + storagePath; // canonical path
  }

  /** Creates a signed URL for a private object. Returns an absolute https URL. */
  public String createSignedUrl(String storagePath, String userBearerJwt) {
    String url = projectBase + "/storage/v1/object/sign/" + bucket + "/" + storagePath;

    String bodyJson = "{\"expiresIn\":" + signedUrlTtlSeconds + "}";
    String relativeSigned = supabase.post()
        .uri(url)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userBearerJwt)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(bodyJson.getBytes(StandardCharsets.UTF_8))
        .retrieve()
        .bodyToMono(String.class)
        .timeout(Duration.ofSeconds(10))
        .map(json -> extractSignedUrlFromJson(json))
        .block();

    // Supabase returns a *relative* signed URL; prefix with project base
    if (relativeSigned != null && relativeSigned.startsWith("/")) {
      return projectBase + relativeSigned;
    }
    return relativeSigned;
  }

  // Very small JSON utility (response like {"signedURL":"/storage/v1/object/sign/..."} )
  private static String extractSignedUrlFromJson(String json) {

    if (json == null) {
      return null;
    }

    int i = json.indexOf("\"signedURL\"");

    if (i < 0) {
      return null;
    }

    int colon = json.indexOf(':', i);
    int quote1 = json.indexOf('"', colon + 1);
    int quote2 = json.indexOf('"', quote1 + 1);

    if (quote1 < 0 || quote2 < 0) {
      return null;
    }

    return json.substring(quote1 + 1, quote2);
  }

  private static String filenameFromPath(String path) {
    int slash = path.lastIndexOf('/');
    return slash >= 0 ? path.substring(slash + 1) : path;
  }
}
