package dev.coms4156.project.metadetect.service;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Forwards signup/login/refresh to Supabase Auth and returns raw JSON.
 */
@Service
public class AuthProxyService {

  private final WebClient supabase;

  public AuthProxyService(WebClient supabaseWebClient) {
    this.supabase = supabaseWebClient;
  }

  /**
   * Forwards an email+password signup request to Supabase Auth.
   *
   * @param email user email
   * @param password user password
   * @return raw Supabase JSON response
   */
  public ResponseEntity<String> signup(String email, String password) {

    String path = "/auth/v1/signup";
    return forwardJson(path, "{\"email\":\"" + escape(email)
      + "\",\"password\":\"" + escape(password) + "\"}");
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
    return forwardJson(path, "{\"email\":\"" + escape(email)
      + "\",\"password\":\"" + escape(password) + "\"}");
  }

  /**
   * Invokes Supabase refresh-token grant to exchange a refresh token
   * for a new access token.
   *
   * @param refreshToken the refresh token
   * @return raw Supabase JSON response
   */
  public ResponseEntity<String> refresh(String refreshToken) {

    String path = "/auth/v1/token?grant_type=refresh_token";
    return forwardJson(path, "{\"refresh_token\":\"" + escape(refreshToken) + "\"}");
  }

  private ResponseEntity<String> forwardJson(String path, String jsonBody) {
    String body = supabase.post()
        .uri(path)
        .bodyValue(jsonBody)
        .retrieve()
        .onStatus(HttpStatusCode::is4xxClientError, resp ->
          resp.bodyToMono(String.class)
            .flatMap(b -> Mono.error(new ProxyException(resp.statusCode(), b))))
        .onStatus(HttpStatusCode::is5xxServerError, resp ->
          resp.bodyToMono(String.class)
            .flatMap(b -> Mono.error(new ProxyException(resp.statusCode(), b))))
        .bodyToMono(String.class)
        .block();

    // Force JSON content type so tests (and clients) see application/json
    return ResponseEntity.ok()
      .contentType(MediaType.APPLICATION_JSON)
      .body(body);
  }

  /**
   * Lightweight proxy exception that holds original HTTP status and body.
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

    /**
     * Gets the HTTP status returned by Supabase.
     *
     * @return the HTTP status code
     */
    public HttpStatusCode getStatus() {
      return status;
    }

    /**
     * Gets the raw JSON error body returned by Supabase.
     *
     * @return the original response body string
     */
    public String getBody() {
      return body;
    }
  }

  /**
   * Minimal JSON string escape for quotes and backslashes.
   *
   * @param s input string
   * @return escaped JSON-safe string
   */
  private static String escape(String s) {

    // Minimal JSON string escape for quotes/backslashes.
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
