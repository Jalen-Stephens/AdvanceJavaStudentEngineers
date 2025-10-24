package dev.coms4156.project.metadetect.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.coms4156.project.metadetect.service.UserService;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit tests for {@link UserService} that verify identity and email extraction
 * from a Spring Security {@link Jwt}-backed authentication (Supabase flow).
 * Focus areas:
 * - Parsing UUID from the JWT "sub" claim
 * - Handling missing/invalid authentication
 * - Reading optional "email" claim
 * Strategy:
 * - Build synthetic JWTs
 * - Place them into the SecurityContext
 * - Call service methods and assert outcomes
 */
class UserServiceTest {

  /** Instance under test. Stateless; safe to share per-method in JUnit 5. */
  private final UserService service = new UserService();

  /** Ensure no test leaks security state to the next one. */
  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  /**
   * When a valid JWT is present and the "sub" is a UUID string, the service
   * should return that UUID.
   */
  @Test
  void getCurrentUserIdOrThrow_returnsUuid_whenAuthenticated() {
    UUID userId = UUID.randomUUID();
    setJwtInContext(jwtWithSubAndEmail(userId.toString(), "user@example.com"));

    UUID result = service.getCurrentUserIdOrThrow();
    assertEquals(userId, result);
  }

  /**
   * When no authentication exists in the security context, the service
   * should throw 401 Unauthorized.
   */
  @Test
  void getCurrentUserIdOrThrow_throwsUnauthorized_whenNoAuth() {
    SecurityContextHolder.clearContext();

    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, service::getCurrentUserIdOrThrow);
    assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
  }

  /**
   * When "sub" exists but is not a valid UUID, the service treats it as
   * unauthenticated and throws 401 Unauthorized.
   */
  @Test
  void getCurrentUserIdOrThrow_throwsUnauthorized_whenInvalidSub() {
    setJwtInContext(jwtWithSubAndEmail("not-a-uuid", "user@example.com"));

    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, service::getCurrentUserIdOrThrow);
    assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
  }

  /**
   * If the "email" claim is present, {@code getCurrentUserEmail()} should
   * return a non-empty Optional.
   */
  @Test
  void getCurrentUserEmail_returnsEmail_whenClaimPresent() {
    UUID userId = UUID.randomUUID();
    setJwtInContext(jwtWithSubAndEmail(userId.toString(), "user@example.com"));

    Optional<String> email = service.getCurrentUserEmail();
    assertTrue(email.isPresent());
    assertEquals("user@example.com", email.get());
  }

  /**
   * With no authentication, the email should be empty rather than throwing.
   */
  @Test
  void getCurrentUserEmail_empty_whenNoAuth() {
    SecurityContextHolder.clearContext();

    Optional<String> email = service.getCurrentUserEmail();
    assertTrue(email.isEmpty());
  }

  /**
   * With a valid JWT but no "email" claim, the email should be empty.
   */
  @Test
  void getCurrentUserEmail_empty_whenClaimMissing() {
    UUID userId = UUID.randomUUID();

    Map<String, Object> headers = Map.of("alg", "RS256");
    Map<String, Object> claims = new HashMap<>();
    claims.put("sub", userId.toString());
    // no "email" claim on purpose

    Jwt jwt = new Jwt(
        "dummy-token",
        Instant.now(),
        Instant.now().plusSeconds(3600),
        headers,
        claims
    );
    setJwtInContext(jwt);

    Optional<String> email = service.getCurrentUserEmail();
    assertTrue(email.isEmpty());
  }

  // --------------------------------------------------------------------------
  // helpers
  // --------------------------------------------------------------------------

  /**
   * Build a synthetic JWT with the given subject and email claims.
   */
  private static Jwt jwtWithSubAndEmail(String sub, String email) {
    Map<String, Object> headers = Map.of("alg", "RS256");
    Map<String, Object> claims = new HashMap<>();
    claims.put("sub", sub);
    claims.put("email", email);

    return new Jwt(
      "dummy-token",
      Instant.now(),
      Instant.now().plusSeconds(3600),
      headers,
      claims
    );
  }

  /**
   * Put a {@link JwtAuthenticationToken} into the SecurityContext so the
   * service can read it as if it were a real request.
   */
  private static void setJwtInContext(Jwt jwt) {
    JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
    SecurityContextHolder.getContext().setAuthentication(auth);
  }
}
