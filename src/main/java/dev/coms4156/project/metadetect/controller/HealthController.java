package dev.coms4156.project.metadetect.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Basic health/version for Iteration 1.
 */
@RestController
public class HealthController {

  @GetMapping("/health")
  public ResponseEntity<Map<String, String>> health() {
    return ResponseEntity.ok(Map.of("status", "ok", "service", "metadetect-service"));
  }

  @GetMapping("/version")
  public ResponseEntity<Map<String, String>> version() {
    return ResponseEntity.ok(Map.of("service", "metadetect-service", "version", "0.1.0"));
  }
}
