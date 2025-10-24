package dev.coms4156.project.metadetect.service;

import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exposes identity helpers based on the authenticated Supabase JWT.
 * Responsibilities:
 * - Provide the current user's UUID (JWT "sub") as a typed value.
 * - Surface a best-effort email claim for display/auditing.
 * - Return the raw bearer token when downstream services need it
 *   (e.g., to call Supabase Storage with user policies).
 * This service centralizes JWT extraction so controllers/services do not
 * re-implement SecurityContext handling or claim parsing.
 */
@Service
public class UserService {

  /**
   * Returns the caller's Supabase user id (JWT "sub") as a UUID.
   * Throws 401 if unauthenticated or the subject is malformed.
   *
   * @return the authenticated user's UUID
   * @throws ResponseStatusException UNAUTHORIZED if missing/invalid
   */
  public UUID getCurrentUserIdOrThrow() {
    Jwt jwt = getJwtOrThrow();
    try {
      return UUID.fromString(jwt.getSubject());
    } catch (IllegalArgumentException ex) {
      // Subject is present but not a valid UUID string.
      throw new ResponseStatusException(
        HttpStatus.UNAUTHORIZED, "Invalid subject in token"
      );
    }
  }

  /**
   * Returns the user's email claim if present.
   * Empty when unauthenticated or the claim is not set.
   *
   * @return Optional email from JWT claim "email"
   */
  public Optional<String> getCurrentUserEmail() {
    return getJwt().map(jwt -> jwt.getClaimAsString("email"));
  }

  /**
   * Returns the raw bearer token for the current user.
   * Useful for user-scoped calls to Supabase services.
   *
   * @return opaque JWT string for Authorization header
   * @throws ResponseStatusException UNAUTHORIZED if missing
   */
  public String getCurrentBearerOrThrow() {
    return getJwt()
      .map(Jwt::getTokenValue)
      .orElseThrow(() ->
        new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Authentication required"
        )
      );
  }

  /**
   * Retrieves the current JWT or throws 401 if not authenticated.
   */
  private Jwt getJwtOrThrow() {
    return getJwt().orElseThrow(() ->
      new ResponseStatusException(
        HttpStatus.UNAUTHORIZED, "Authentication required"
      )
    );
  }

  /**
   * Pulls the JWT from Spring Security's context, if present.
   * Returns empty when unauthenticated or when the principal is
   * not a Jwt (e.g., anonymous or different auth mechanism).
   */
  private Optional<Jwt> getJwt() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) {
      return Optional.empty();
    }
    Object principal = auth.getPrincipal();
    if (principal instanceof Jwt) {
      return Optional.of((Jwt) principal);
    }
    return Optional.empty();
  }
}
