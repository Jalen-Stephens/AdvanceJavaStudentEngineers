package dev.coms4156.project.metadetect.controller;

import dev.coms4156.project.metadetect.dto.Dtos;
import dev.coms4156.project.metadetect.service.AuthProxyService;
import dev.coms4156.project.metadetect.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AuthController proxies signup/login/refresh to Supabase and exposes /auth/me.
 * Registration/login are not implemented locally.
 */
@Tag(name = "Auth", description = "Authentication endpoints (Supabase proxy) and current-user info")
@RestController
@RequestMapping("/auth")
public class AuthController {

  private final UserService userService;
  private final AuthProxyService authProxy;

  /**
   * Constructs the controller with required collaborators.
   *
   * @param userService local user domain service
   * @param authProxy adaptor that calls Supabase Auth endpoints
   */
  public AuthController(UserService userService, AuthProxyService authProxy) {
    this.userService = userService;
    this.authProxy = authProxy;
  }

  // --- Proxy endpoints (raw Supabase JSON passthrough) ---
  @Operation(
      summary = "Sign up a new user (proxied to Supabase)",
      description = "Wraps Supabase Auth sign-up. Accepts an email and password and forwards "
          + "the request to Supabase. Returns Supabase's raw JSON response."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200",
          description = "Signup completed (see Supabase JSON for details)"),
      @ApiResponse(responseCode = "400",
          description = "Validation error (from Supabase)")
  })
  @PostMapping("/signup")
  public ResponseEntity<String> signup(@RequestBody Dtos.RegisterRequest req) {
    // Pass-through: do not log raw passwords; the proxy handles HTTP and error codes.
    return authProxy.signup(req.email(), req.password());
  }

  @Operation(
      summary = "Login (proxied to Supabase)",
      description = "Wraps Supabase Auth login. Accepts an email and password and forwards "
        + "the request to Supabase. Returns Supabase's raw JSON response containing tokens."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200",
          description = "Login succeeded (see Supabase JSON for tokens)"),
      @ApiResponse(responseCode = "400",
          description = "Invalid credentials or validation error (from Supabase)")
  })
  @PostMapping("/login")
  public ResponseEntity<String> login(@RequestBody Dtos.LoginRequest req) {
    // Pass-through: credentials are forwarded to Supabase; no local auth happens here.
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
  @Operation(
      summary = "Refresh access token (proxied to Supabase)",
      description = "Exchanges a Supabase refresh token for a new access token. If the "
        + "`refreshToken` field is missing, returns `400` with a JSON error instead of "
        + "forwarding the call."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200",
          description = "New access token returned (Supabase JSON)"),
      @ApiResponse(responseCode = "400",
          description = "Missing `refreshToken` field")
  })
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
  @Operation(
      summary = "Get current authenticated user",
      description = "Returns the identity of the caller as resolved from the Supabase JWT. "
          + "Always includes a user `id` and includes `email` when present.",
      security = { @SecurityRequirement(name = "bearerAuth") }
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200",
          description = "User identity returned successfully"),
      @ApiResponse(responseCode = "401",
          description = "Missing or invalid bearer token")
  })
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
