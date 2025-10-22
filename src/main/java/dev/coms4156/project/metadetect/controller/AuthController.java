package dev.coms4156.project.metadetect.controller;

import dev.coms4156.project.metadetect.dto.Dtos;
import dev.coms4156.project.metadetect.service.AuthProxyService;
import dev.coms4156.project.metadetect.service.UserService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AuthController proxies signup/login/refresh to Supabase and exposes /auth/me.
 * Registration/login are not implemented locally.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

  private final UserService userService;
  private final AuthProxyService authProxy;

  public AuthController(UserService userService, AuthProxyService authProxy) {
    this.userService = userService;
    this.authProxy = authProxy;
  }

  // --- Proxy endpoints (raw Supabase JSON passthrough) ---

  @PostMapping("/signup")
  public ResponseEntity<String> signup(@RequestBody Dtos.RegisterRequest req) {
    return authProxy.signup(req.email(), req.password());
  }

  @PostMapping("/login")
  public ResponseEntity<String> login(@RequestBody Dtos.LoginRequest req) {
    return authProxy.login(req.email(), req.password());
  }

  /**
   * Exchanges a Supabase refresh token for a new access token.
   * This endpoint simply proxies to Supabase Auth's
   * {@code /auth/v1/token?grant_type=refresh_token}. If the request body is
   * missing or does not include a {@code refreshToken} field, a
   * {@code 400 Bad Request} is returned with a JSON error message instead of
   * forwarding the call.
   *
   * @param req wrapper containing the {@code refreshToken} required to obtain a new access token
   * @return 200 with Supabase's raw JSON on success, or
   *         400 {@code {"error":"missing refreshToken"}} if the field is absent
   */
  @PostMapping(value = "/refresh", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> refresh(@RequestBody Dtos.RefreshRequest req) {
    if (req == null || req.refreshToken() == null) {         // adds a branch
      return ResponseEntity.badRequest()
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"error\":\"missing refreshToken\"}");
    }
    return authProxy.refresh(req.refreshToken());
  }


  // --- Identity endpoint (validated by our resource server) ---

  /**
   * Returns the identity of the currently authenticated user as resolved
   * by our resource server (Supabase JWT).
   * The response always includes a user {@code id}. If an email address
   * is available, it is also included. Some JWT variants (or service accounts)
   * may not contain an email claim, in which case {@code email} is omitted.
   *
   * @return a JSON object containing at least {@code { "id": "<uuid>" }},
   *         and optionally {@code "email"} when present
   */

  @GetMapping("/me")
  public ResponseEntity<Map<String, Object>> me() {
    var id = userService.getCurrentUserIdOrThrow();
    var email = userService.getCurrentUserEmail().orElse(null);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", id.toString());
    // explicit branch JaCoCo can measure
    if (email != null) {
      payload.put("email", email);
    }

    return ResponseEntity.ok(payload);
  }

  /**
   * Handles errors bubbled up from the Supabase proxy layer,
   * preserving the original HTTP status and raw JSON body.
   *
   * @param ex the proxy exception containing status and body
   * @return ResponseEntity with Supabase's status and JSON body
   */
  @ExceptionHandler(AuthProxyService.ProxyException.class)
  public ResponseEntity<String> handleProxyError(AuthProxyService.ProxyException ex) {
    return ResponseEntity.status(ex.getStatus())
      .contentType(MediaType.APPLICATION_JSON)
      .body(ex.getBody());
  }
}
