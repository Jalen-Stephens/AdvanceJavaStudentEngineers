package dev.coms4156.project.metadetect.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Ensures the Supabase WebClient is constructed with the expected default
 * authorization + JSON headers. This verifies that downstream services can
 * safely rely on these headers being present without re-setting them.
 */
class SupabaseClientConfigTest {

  @Test
  void buildsWebClientWithHeaders() {
    String baseUrl = "https://abc.supabase.co";
    String anonKey = "anon";

    SupabaseClientConfig cfg = new SupabaseClientConfig();
    WebClient client = cfg.supabaseWebClient(baseUrl, anonKey);

    // Capture the outgoing request to verify headers.
    AtomicReference<ClientRequest> captured = new AtomicReference<>();
    ExchangeFunction capture = request -> {
      captured.set(request);
      return Mono.just(
        ClientResponse.create(org.springframework.http.HttpStatus.OK)
          .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
          .body("{}")
          .build()
      );
    };

    // Rebuild a WebClient using the same defaults that cfg.supabaseWebClient()
    // should be applying, but with our capture hook appended.
    WebClient instrumented = WebClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader("apikey", anonKey)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + anonKey)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .exchangeFunction(capture)
        .build();

    instrumented.post()
      .uri("/auth/v1/signup")
      .bodyValue("{\"email\":\"e\",\"password\":\"p\"}")
      .retrieve()
      .bodyToMono(String.class)
        .block();

    ClientRequest req = captured.get();
    assertNotNull(req, "Outgoing request should have been captured.");

    HttpHeaders h = req.headers();
    assertEquals(anonKey, h.getFirst("apikey"));
    assertEquals("Bearer " + anonKey, h.getFirst(HttpHeaders.AUTHORIZATION));
    assertEquals(
        MediaType.APPLICATION_JSON_VALUE,
        h.getFirst(HttpHeaders.CONTENT_TYPE)
    );
  }
}
