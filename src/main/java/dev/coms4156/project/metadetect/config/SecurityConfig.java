package dev.coms4156.project.metadetect.config;

import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security configuration for the MetaDetect service.
 * Defines authentication, authorization, and HTTP security policies.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  /**
   * Security filter chain for API endpoints under /api/**.
   * Enforces JWT authentication via OAuth2 Resource Server.
   * Public endpoints (e.g. /api/health) are permitted without auth.
   */
  @Bean
  @Order(1)
  public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
    http
        // Only apply this chain to /api/** endpoints
        .securityMatcher("/api/**")
        .csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .authorizeHttpRequests(auth -> auth
            // Allow CORS preflight (OPTIONS) through without auth
            .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()

            // Public API endpoints
            .requestMatchers("/api/health", "/api/public/**").permitAll()

            // Everything else under /api/** requires auth
            .anyRequest().authenticated()
        )
        .exceptionHandling(e -> e.authenticationEntryPoint(
            new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));

    return http.build();
  }

  /**
   * Security filter chain for non-API endpoints:
   * Serves static assets, HTML pages, and allows public access to the Pulse client.
   */
  @Bean
  @Order(2)
  public SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
    http
          .csrf(csrf -> csrf.disable())
          .cors(Customizer.withDefaults())
          .authorizeHttpRequests(auth -> auth
              // Public pages (served from src/main/resources/static)
              .requestMatchers(
                  "/",
                  "/index.html",
                  "/login.html",
                  "/signup.html",
                  "/compose.html"
              ).permitAll()

              // Static assets – list concrete files and folders
              .requestMatchers(
                  "/styles.css",
                  "/compose.css",
                  "/config.js",
                  "/app.js",
                  "/compose.js",
                  "/css/**",
                  "/js/**",
                  "/images/**",
                  "/fonts/**",
                  "/webjars/**",

                  // Swagger / OpenAPI docs
                  "/swagger-ui.html",
                  "/swagger-ui/**",
                  "/v3/api-docs/**",
                  "/api-docs/**"
              ).permitAll()

              // Public non-API endpoints (health/auth pages used by tests + clients)
              .requestMatchers(
                  "/health",
                  "/actuator/**",
                  "/auth/**"
              ).permitAll()

              // Everything else (non-API) requires authentication
              .anyRequest().authenticated()
        );
    http.exceptionHandling(e -> e.authenticationEntryPoint(
        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));

    return http.build();
  }


  /**
   * JWT decoder configured to validate Supabase-issued access tokens.
   * Uses the project's JWT secret for HS256 signature validation
   * and enforces the correct issuer URL.
   */
  @Bean
  JwtDecoder jwtDecoder(
      @Value("${metadetect.supabase.jwtSecret}") String jwtSecret,
      @Value("${metadetect.supabase.url}") String projectBaseUrl
  ) {
    // Supabase access tokens are HS256-signed with the project's JWT secret
    var key = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    var decoder = NimbusJwtDecoder.withSecretKey(key)
        .macAlgorithm(MacAlgorithm.HS256)
        .build();

    // Enforce issuer = https://<project>.supabase.co/auth/v1
    var issuer = projectBaseUrl.endsWith("/")
        ? projectBaseUrl + "auth/v1"
        : projectBaseUrl + "/auth/v1";

    OAuth2TokenValidator<Jwt> validator =
        new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer));
    decoder.setJwtValidator(validator);

    return decoder;
  }

  /**
   * CORS configuration allowing requests from any origin.
   * Allows common HTTP methods and headers.
   */
  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    var cfg = new CorsConfiguration();
    cfg.setAllowedOrigins(List.of("*"));
    cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    cfg.setAllowCredentials(false);

    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cfg);
    return source;
  }
}
