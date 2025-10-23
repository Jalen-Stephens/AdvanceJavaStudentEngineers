package dev.coms4156.project.metadetect.db;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Holds tenant or user-scoped context used for enforcing row-level security.
 * This value is propagated into repository calls to ensure isolation.
 */
@Component
public class RlsContext {

  private final JdbcTemplate jdbc;

  public RlsContext(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Runs work with Postgres GUC "request.jwt.claims" set for this transaction.
   * Policies can read it via: current_setting('request.jwt.claims', true)::jsonb
   */
  @Transactional
  public <T> T asUser(UUID userId, Supplier<T> work) {
    // Minimal claims; include role if your RLS expects it
    String claimsJson = """
      {"sub":"%s","role":"authenticated"}
        """.formatted(userId);

    // set_config(name, value, is_local) → text (returns the set value)
    jdbc.queryForObject(
        "select set_config(?, ?, true)",
        String.class,
        "request.jwt.claims",
        claimsJson);

    try {
      return work.get();
    } finally {
      try {
        jdbc.queryForObject(
            "select set_config(?, ?, true)",
            String.class,
            "request.jwt.claims",
            "");
      } catch (DataAccessException ignored) {
        // tx may be aborted; safe to ignore
      }
    }
  }

  /** Void variant. */
  @Transactional
  public void asUser(UUID userId, Runnable work) {
    asUser(userId, () -> {
      work.run();
      return null;
    });
  }
}
