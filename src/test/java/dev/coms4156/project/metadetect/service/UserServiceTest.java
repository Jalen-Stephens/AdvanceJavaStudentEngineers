package dev.coms4156.project.metadetect;

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
 * Unit tests for UserService identity extraction from Supabase JWT.
 */
class UserServiceTest {

  private final UserService service = new UserService();

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void getCurrentUserIdOrThrow_returnsUuid_whenAuthenticated() {
    UUID userId = UUID.randomUUID();
    setJwtInContext(jwtWithSubAndEmail(userId.toString(), "user@example.com"));

    UUID result = service.getCurrentUserIdOrThrow();
    assertEquals(userId, result);
  }

  @Test
  void getCurrentUserIdOrThrow_throwsUnauthorized_whenNoAuth() {
    SecurityContextHolder.clearContext();

    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, service::getCurrentUserIdOrThrow);
    assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
  }

  @Test
  void getCurrentUserIdOrThrow_throwsUnauthorized_whenInvalidSub() {
    setJwtInContext(jwtWithSubAndEmail("not-a-uuid", "user@example.com"));

    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, service::getCurrentUserIdOrThrow);
    assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
  }

  @Test
  void getCurrentUserEmail_returnsEmail_whenClaimPresent() {
    UUID userId = UUID.randomUUID();
    setJwtInContext(jwtWithSubAndEmail(userId.toString(), "user@example.com"));

    Optional<String> email = service.getCurrentUserEmail();
    assertTrue(email.isPresent());
    assertEquals("user@example.com", email.get());
  }

  @Test
  void getCurrentUserEmail_empty_whenNoAuth() {
    SecurityContextHolder.clearContext();

    Optional<String> email = service.getCurrentUserEmail();
    assertTrue(email.isEmpty());
  }

  @Test
  void getCurrentUserEmail_empty_whenClaimMissing() {
    UUID userId = UUID.randomUUID();

    Map<String, Object> headers = Map.of("alg", "RS256");
    Map<String, Object> claims = new HashMap<>();
    claims.put("sub", userId.toString());
    // intentionally no "email" claim

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

  // --- helpers ---

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

  private static void setJwtInContext(Jwt jwt) {
    JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
    SecurityContextHolder.getContext().setAuthentication(auth);
  }
}
