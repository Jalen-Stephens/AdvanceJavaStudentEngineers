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
 * Provides user identity helpers derived from the authenticated Supabase JWT.
 * This service exposes the current user's UUID (`sub`) and optional email
 * claim so that downstream services can enforce ownership and access control.
 */
@Service
public class UserService {

  /**
   * Returns caller's Supabase user id (JWT "sub") as UUID.
   * Throws 401 if unauthenticated or invalid token.
   */
  public UUID getCurrentUserIdOrThrow() {
    Jwt jwt = getJwtOrThrow();
    try {
      return UUID.fromString(jwt.getSubject());
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid subject in token");
    }
  }

  /**
   * Best-effort email from JWT ("email" claim). Empty if missing/unauthenticated.
   */
  public Optional<String> getCurrentUserEmail() {
    return getJwt().map(jwt -> jwt.getClaimAsString("email"));
  }

  /**
   * Returns the raw bearer token for the current user or throws 401.
   * */
  public String getCurrentBearerOrThrow() {
    return getJwt().map(Jwt::getTokenValue)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
        "Authentication required"));
  }

  /** Returns 401 if no authenticated JWT is present. */
  private Jwt getJwtOrThrow() {
    return getJwt().orElseThrow(() ->
      new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
  }

  /** Returns the current JWT if present; empty when unauthenticated. */
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
