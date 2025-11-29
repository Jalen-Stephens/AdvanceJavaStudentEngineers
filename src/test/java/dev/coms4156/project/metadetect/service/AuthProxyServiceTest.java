package dev.coms4156.project.metadetect.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Unit tests for {@link AuthProxyService} using MockWebServer.
 * Goals:
 * - Verify JSON passthrough on 2xx responses with forced JSON content type.
 * - Verify 4xx/5xx errors are raised as ProxyException with status and body.
 * - Confirm the private escape() utility performs minimal JSON string escaping.
 * Test strategy:
 * - Build a WebClient pointed at a local MockWebServer base URL.
 * - Enqueue canned responses, invoke service methods, and assert outcomes.
 * - Keep assertions focused on status, content type, and body fidelity.
 */
class AuthProxyServiceTest {

  private static MockWebServer server;
  private static AuthProxyService service;

  /**
   * Spins up MockWebServer and builds a WebClient with expected default headers.
   * The base URL is normalized to avoid trailing slashes.
   */
  @BeforeAll
  static void setup() throws IOException {
    server = new MockWebServer();
    server.start();
    String base = server.url("/").toString().replaceAll("/+$", "");

    WebClient client = WebClient.builder()
        .baseUrl(base)
        .defaultHeader("apikey", "anon")
        .defaultHeader("Authorization", "Bearer anon")
        .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        .build();

    service = new AuthProxyService(client);
  }

  /** Shuts down the server to free the local port. */
  @AfterAll
  static void tearDown() throws IOException {
    server.shutdown();
  }

  /**
   * login(): returns 200 and JSON body; service forces application/json content type
   * on the ResponseEntity, matching downstream expectations.
   */
  @Test
  void login_success_returnsJsonContentType() {
    server.enqueue(new MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("{\"access_token\":\"tkn\"}"));

    ResponseEntity<String> resp = service.login("a@b.com", "pw");

    assertEquals(HttpStatusCode.valueOf(200), resp.getStatusCode());
    assertEquals(MediaType.APPLICATION_JSON, resp.getHeaders().getContentType());
    assertEquals("{\"access_token\":\"tkn\"}", resp.getBody());
  }

  /**
   * signup(): a 400 from Supabase should surface as ProxyException with both
   * the original status code and the raw JSON body.
   */
  @Test
  void signup_400_throwsProxyExceptionWithStatusAndBody() {
    server.enqueue(new MockResponse()
        .setResponseCode(400)
        .setHeader("Content-Type", "application/json")
        .setBody("{\"msg\":\"bad\"}"));

    AuthProxyService.ProxyException ex =
        assertThrows(AuthProxyService.ProxyException.class,
          () -> service.signup("bad", "pw"));

    assertEquals(400, ex.getStatus().value());
    assertEquals("{\"msg\":\"bad\"}", ex.getBody());
  }

  /**
   * refresh(): a 500 error should also raise ProxyException with the 5xx code.
   */
  @Test
  void refresh_500_throwsProxyException() {
    server.enqueue(new MockResponse()
        .setResponseCode(500)
        .setHeader("Content-Type", "application/json")
        .setBody("{\"error\":\"oops\"}"));

    AuthProxyService.ProxyException ex =
        assertThrows(AuthProxyService.ProxyException.class,
          () -> service.refresh("rfr"));

    assertEquals(500, ex.getStatus().value());
  }

  /**
   * escape(): reflection-based check of the minimal JSON string escape helper.
   * Ensures backslashes and quotes are escaped for safe inline JSON construction.
   */
  @Test
  void escape_minimal_reflection() throws Exception {
    var m = AuthProxyService.class.getDeclaredMethod("escape", String.class);
    m.setAccessible(true);

    String out = (String) m.invoke(null, "a\\\"b");

    // Expected: backslash -> \\\\ and quote -> \\"
    assertEquals("a\\\\\\\"b", out);
  }
}
