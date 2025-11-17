package dev.coms4156.project.metadetect.config;

import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
   *
   * @param http HttpSecury builder
   * @return configured SecurityFilterChain
   */
  @Bean
  public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
    http
        // Only apply this chain to /api/** endpoints
        .securityMatcher("/api/**")
        .csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .authorizeHttpRequests(auth -> auth
            // Public API endpoints (if you have any under /api)
            .requestMatchers("/api/health", "/api/public/**").permitAll()
            // Everything else under /api/** requires auth
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));

    return http.build();
  }

  /**
   * JWT decoder configured to validate Supabase-issued access tokens.
   * Uses the project's JWT secret for HS256 signature validation
   * and enforces the correct issuer URL.
   *
   * @param jwtSecret Supabase project's JWT secret
   * @param projectBaseUrl Supabase project base URL
   * @return configured JwtDecoder
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
   *
   * @return configured CorsConfigurationSource
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
