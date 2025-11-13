package dev.coms4156.project.metadetect.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
/**
 * Basic health/version for Iteration 1.
 */
@Tag(name = "Health", description = "Service health checks")
@RestController
public class HealthController {
  
  private final JdbcTemplate jdbc;

  public HealthController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }


  /**
   * Simple liveness endpoint for health checks.
   *
   * @return static JSON confirming the service is reachable
   */
  @Operation(
    summary = "Database liveness check",
    description = "Executes `SELECT 1` against the primary database and "
        + "returns `UP` if successful, `DOWN` otherwise."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200",
          description = "Database reachable; body is `UP` or `DOWN`")
  })
  @GetMapping("/db/health")
  public String dbHealth() {
    Integer one = jdbc.queryForObject("select 1", Integer.class);
    return one != null && one == 1 ? "UP" : "DOWN";

  }

  /**
   * Simple version endpoint.
   *
   * @return static JSON with service name and version
   */
  @Operation(
    summary = "Service version",
    description = "Returns a static JSON payload with the MetaDetect service name and version."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200",
          description = "Version information returned successfully")
  })
  @GetMapping("/db/version")
  public ResponseEntity<Map<String, String>> version() {
    return ResponseEntity.ok(Map.of("service", "metadetect-service", "version", "0.1.0"));
  }
}
