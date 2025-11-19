package dev.coms4156.project.metadetect.api.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI (Swagger) configuration for the MetaDetect API.
 * Sets up API metadata, security schemes, and grouping for documentation generation.
 * Uses springdoc-openapi for integration with Spring Boot.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "MetaDetect API",
                version = "v1",
                description = "API for image authenticity and metadata analysis."
        ),
        servers = {
          @Server(url = "http://localhost:8080", description = "Local dev")
        },
        security = {
          @SecurityRequirement(name = "bearerAuth")
        }
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER
)


public class OpenApiConfig {

  /**
   * Grouped OpenAPI bean for the MetaDetect API.
   * Scans the controller package to include all API endpoints in the documentation.
   *
   * @return GroupedOpenApi instance for MetaDetect API
  */
  @Bean
  public GroupedOpenApi metadetectApi() {
    return GroupedOpenApi.builder()
        .group("metadetect")
        .packagesToScan("dev.coms4156.project.metadetect.controller")
        .build();
  }
}

