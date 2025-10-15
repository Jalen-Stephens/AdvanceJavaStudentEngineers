package dev.coms4156.project.metadetect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the MetaDetect AI Image Detection Service.
 * This class starts the Spring Boot application.
 */
@SpringBootApplication
public class MetaDetectApplication {

  public static void main(String[] args) {
    SpringApplication.run(MetaDetectApplication.class, args);
  }
}
