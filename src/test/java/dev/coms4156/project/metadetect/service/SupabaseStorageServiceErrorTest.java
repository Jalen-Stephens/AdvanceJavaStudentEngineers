package dev.coms4156.project.metadetect.service;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Unit-level error-path coverage for {@link SupabaseStorageService} that does
 * not rely on network sockets. Uses an ExchangeFunction stub to force 5xx
 * responses and exercise the WebClientResponseException catch blocks.
 */
class SupabaseStorageServiceErrorTest {

  /** Builds a WebClient that always returns the provided status/body. */
  private static WebClient errorClient(HttpStatus status, String body) {
    ExchangeFunction fn = request -> Mono.just(
        ClientResponse.create(status)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .body(body)
            .build()
    );
    return WebClient.builder().exchangeFunction(fn).build();
  }

  @Test
  void uploadObject_5xx_throwsRuntimeWithStatus() {
    WebClient client = errorClient(HttpStatus.INTERNAL_SERVER_ERROR, "{\"err\":true}");
    SupabaseStorageService svc = new SupabaseStorageService(
        client, "https://example.test", "bucket", 900, "anon"
    );

    byte[] bytes = "bytes".getBytes();
    assertThrows(RuntimeException.class, () -> svc.uploadObject(
        new java.io.ByteArrayInputStream(bytes),
        bytes.length,
        MediaType.IMAGE_PNG_VALUE,
        "user/a.png",
        "jwt"
    ));
  }

  @Test
  void deleteObject_5xx_rethrowsRuntime() {
    WebClient client = errorClient(HttpStatus.INTERNAL_SERVER_ERROR, "{\"err\":true}");
    SupabaseStorageService svc = new SupabaseStorageService(
        client, "https://example.test", "bucket", 900, "anon"
    );

    assertThrows(RuntimeException.class, () -> svc.deleteObject("user/a.png", "jwt"));
  }
}
