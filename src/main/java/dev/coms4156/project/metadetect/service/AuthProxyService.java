package dev.coms4156.project.metadetect.service;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Service responsible for proxying authentication requests directly to
 * Supabase Auth. All bodies are forwarded as raw JSON, and the response
 * is returned untouched so clients receive the exact semantics of the
 * upstream provider.
 * Scope / Intent:
 * - This service does not authenticate locally.
 * - It shields callers from WebClient wiring, error handling, and JSON
 *   assembly.
 * - It ensures downstream controllers do not assume any particular auth
 *   storage or implementation.
 */
@Service
public class AuthProxyService {

  private final WebClient supabase;

  /**
   * Constructs a proxy service using a preconfigured WebClient that already
   * targets the Supabase base URL and required headers (apikey, anon key,
   * service key, etc.).
   */
  public AuthProxyService(WebClient supabaseWebClient) {
    this.supabase = supabaseWebClient;
  }

  /**
   * Proxies a signup request to Supabase Auth using the password grant.
   *
   * @param email user email
   * @param password user password
   * @return raw Supabase JSON response wrapped in 200 OK, or
   *         ProxyException if Supabase returns >= 400
   */
  public ResponseEntity<String> signup(String email, String password) {
    String path = "/auth/v1/signup";
    return forwardJson(
      path,
      "{\"email\":\"" + escape(email)
        + "\",\"password\":\"" + escape(password) + "\"}"
    );
  }

  /**
   * Forwards a password-based login request to Supabase Auth.
   *
   * @param email user email
   * @param password user password
   * @return raw Supabase JSON response
   */
  public ResponseEntity<String> login(String email, String password) {
    String path = "/auth/v1/token?grant_type=password";
    return forwardJson(
      path,
      "{\"email\":\"" + escape(email)
        + "\",\"password\":\"" + escape(password) + "\"}"
    );
  }

  /**
   * Exchanges a refresh token for a new access token (Supabase refresh grant).
   *
   * @param refreshToken refresh token previously issued by Supabase
   * @return raw Supabase JSON response
   */
  public ResponseEntity<String> refresh(String refreshToken) {
    String path = "/auth/v1/token?grant_type=refresh_token";
    return forwardJson(
      path,
      "{\"refresh_token\":\"" + escape(refreshToken) + "\"}"
    );
  }

  /**
   * Issues the POST request to the upstream Supabase Auth endpoint and
   * propagates 4xx/5xx as a ProxyException for controller advice to map.
   */
  private ResponseEntity<String> forwardJson(String path, String jsonBody) {
    String body = supabase.post()
        .uri(path)
        .bodyValue(jsonBody)
        .retrieve()
        .onStatus(HttpStatusCode::is4xxClientError, resp ->
        resp.bodyToMono(String.class)
          .flatMap(b ->
            Mono.error(new ProxyException(resp.statusCode(), b))))
        .onStatus(HttpStatusCode::is5xxServerError, resp ->
        resp.bodyToMono(String.class)
          .flatMap(b ->
            Mono.error(new ProxyException(resp.statusCode(), b))))
        .bodyToMono(String.class)
        .block();

    // Force application/json so client code does not need to guess.
    return ResponseEntity.ok()
      .contentType(MediaType.APPLICATION_JSON)
      .body(body);
  }

  /**
   * Lightweight runtime exception that captures both HTTP status and
   * raw response body from Supabase for upstream propagation.
   */
  public static class ProxyException extends RuntimeException {
    private final HttpStatusCode status;
    private final String body;

    /**
     * Constructs a proxy exception that preserves Supabase's original
     * HTTP status code and raw response body.
     *
     * @param status the status returned by Supabase
     * @param body   the raw JSON body returned by Supabase
     */
    public ProxyException(HttpStatusCode status, String body) {
      super("Supabase responded " + status.value());
      this.status = status;
      this.body = body;
    }

    public HttpStatusCode getStatus() {
      return status;
    }

    public String getBody() {
      return body;
    }
  }

  /**
   * Escapes minimal characters for embedding into JSON string literals.
   * We intentionally do not perform full JSON encoding here as Supabase
   * performs final validation.
   */
  private static String escape(String s) {
    return s
      .replace("\\", "\\\\")
      .replace("\"", "\\\"");
  }
}
