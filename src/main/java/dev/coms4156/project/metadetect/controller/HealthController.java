package dev.coms4156.project.metadetect.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
/**
 * Basic health/version for Iteration 1.
 */

@RestController
public class HealthController {

  private final JdbcTemplate jdbc;

  public HealthController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping("/db/health")
  public String dbHealth() {
    Integer one = jdbc.queryForObject("select 1", Integer.class);
    return one != null && one == 1 ? "UP" : "DOWN";

  }

  @GetMapping("/db/version")
  public ResponseEntity<Map<String, String>> version() {
    return ResponseEntity.ok(Map.of("service", "metadetect-service", "version", "0.1.0"));
  }
}
