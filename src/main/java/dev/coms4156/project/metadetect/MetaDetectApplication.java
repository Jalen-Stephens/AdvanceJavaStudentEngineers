package dev.coms4156.project.metadetect;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Entry point for the MetaDetect AI Image Detection Service.
 * Bootstraps the Spring Boot application and exposes shared infrastructure
 * beans (such as {@link Clock}) used across the service for deterministic and
 * testable timestamps.
 */
@SpringBootApplication
public class MetaDetectApplication {

  public static void main(String[] args) {
    SpringApplication.run(MetaDetectApplication.class, args);
  }

  /**
   * System UTC clock used for consistent timestamp generation across
   * services (injection-friendly for tests).
   */
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
