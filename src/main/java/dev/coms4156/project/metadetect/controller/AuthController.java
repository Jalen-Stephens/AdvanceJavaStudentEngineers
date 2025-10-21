package dev.coms4156.project.metadetect.controller;

import dev.coms4156.project.metadetect.dto.Dtos;
import dev.coms4156.project.metadetect.service.AuthProxyService;
import dev.coms4156.project.metadetect.service.UserService;
import java.util.Map;
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

  @PostMapping("/refresh")
  public ResponseEntity<String> refresh(@RequestBody Dtos.RefreshRequest req) {
    return authProxy.refresh(req.refreshToken());
  }

  // --- Identity endpoint (validated by our resource server) ---

  /**
   * Returns the identity of the currently authenticated user
   * as resolved by our resource server (Supabase JWT).
   *
   * @return a JSON object containing {id, email}
   */
  @GetMapping("/me")
  public ResponseEntity<Map<String, Object>> me() {

    var id = userService.getCurrentUserIdOrThrow();
    var email = userService.getCurrentUserEmail().orElse(null);
    return ResponseEntity.ok(Map.of("id", id.toString(), "email", email));
  }

  // --- Error mapping for proxy failures ---

  @ExceptionHandler(AuthProxyService.ProxyException.class)
  public ResponseEntity<String> handleProxyError(AuthProxyService.ProxyException ex) {
    return ResponseEntity.status(ex.getStatus()).body(ex.getBody());
  }
}
