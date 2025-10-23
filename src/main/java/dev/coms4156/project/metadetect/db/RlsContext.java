package dev.coms4156.project.metadetect.db;

import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages row-level security (RLS) context for Postgres by setting the
 * `request.jwt.claims` GUC (Grand Unified Configuration) during a transaction.
 * The RLS policies read this per-session variable using:
 *   current_setting('request.jwt.claims', true)::jsonb
 * This makes DB enforcement aware of the "effective user" for the request
 * without pushing JWT parsing into repository code.
 * Scope:
 * - Thin wrapper around JdbcTemplate to ensure the correct GUC is present.
 * - Used by service-layer methods that must execute queries as a specific user.
 */
@Component
public class RlsContext {

  private final JdbcTemplate jdbc;

  /**
   * Constructs the context binding Postgres GUC configuration to this component.
   *
   * @param jdbc JDBC template used to issue `set_config` inside the transaction
   */
  public RlsContext(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Executes work with Postgres GUC `request.jwt.claims` set to identify the
   * caller (or "tenant") for this transaction.
   * The value will be cleared afterward, even if the work throws.
   *
   * @param userId the user identifier to inject into `request.jwt.claims`
   * @param work   lambda representing the DB work to run under this identity
   * @param <T>    return type of the supplied work
   * @return the value returned by the supplied work
   */
  @Transactional
  public <T> T asUser(UUID userId, Supplier<T> work) {
    // Minimal claims payload; include "role" when policies expect it.
    String claimsJson = """
      {"sub":"%s","role":"authenticated"}
        """.formatted(userId);

    // set_config(name, value, is_local) ensures the change is scoped
    // to this transaction rather than the whole session.
    jdbc.queryForObject(
        "select set_config(?, ?, true)",
        String.class,
        "request.jwt.claims",
        claimsJson
    );

    try {
      return work.get();
    } finally {
      // Clear the claim to avoid leaking context into subsequent statements.
      try {
        jdbc.queryForObject(
            "select set_config(?, ?, true)",
            String.class,
            "request.jwt.claims",
            ""
        );
      } catch (DataAccessException ignored) {
        // If the transaction is already aborted, this cleanup may fail;
        // safe to ignore because the GUC scope will be released anyway.
      }
    }
  }

  /**
   * Void variant for callers that do not need a return value.
   *
   * @param userId the identity to set for the duration of `work`
   * @param work   operation to execute
   */
  @Transactional
  public void asUser(UUID userId, Runnable work) {
    asUser(userId, () -> {
      work.run();
      return null;
    });
  }
}
