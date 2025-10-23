package dev.coms4156.project.metadetect.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health and metadata endpoints for Iteration 1 readiness.
 * Responsibilities:
 * - Verifies DB connectivity with a minimal round-trip.
 * - Exposes a static version endpoint for diagnostics.
 * These endpoints are intentionally unauthenticated so platform probes (Kubernetes, Render,
 * Fly.io, etc.) can detect whether the service is healthy at startup and runtime.
 */
@RestController
public class HealthController {

  private final JdbcTemplate jdbc;

  /**
   * Constructs the controller used for basic health checks.
   *
   * @param jdbc JDBC template used to verify DB connectivity.
   */
  public HealthController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Performs a minimal DB liveness check using a simple `select 1`.
   * Returns "UP" when the DB is reachable, otherwise "DOWN".
   *
   * @return a simple "UP" or "DOWN" status string
   */
  @GetMapping("/db/health")
  public String dbHealth() {
    // No table lookup required: trivial round-trip ensures database is responsive.
    Integer one = jdbc.queryForObject("select 1", Integer.class);
    return one != null && one == 1 ? "UP" : "DOWN";
  }

  /**
   * Returns static version/service metadata for smoke tests or rollout tracing.
   *
   * @return JSON map containing service name and version
   */
  @GetMapping("/db/version")
  public ResponseEntity<Map<String, String>> version() {
    return ResponseEntity.ok(
      Map.of("service", "metadetect-service", "version", "0.1.0")
    );
  }
}
