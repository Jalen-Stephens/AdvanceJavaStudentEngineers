// src/main/java/dev/coms4156/project/metadetect/config/SecurityConfig.java

package dev.coms4156.project.metadetect.config;

import java.util.Collection;
import java.util.Collections;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for JWT-based authentication using Supabase.
 * This class configures the application as an OAuth2 Resource Server and
 * validates incoming Bearer tokens via Supabase's JWKS endpoint.
 */
@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth
        .requestMatchers("/health", "/actuator/**").permitAll()
        .anyRequest().authenticated()
      )
        .oauth2ResourceServer(oauth2 -> oauth2
        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
        );
    return http.build();
  }

  @Bean
  JwtAuthenticationConverter jwtAuthenticationConverter() {
    // Keep authorities empty for now; we only need claims (sub, email).
    JwtAuthenticationConverter conv = new JwtAuthenticationConverter();
    conv.setJwtGrantedAuthoritiesConverter(SecurityConfig::emptyAuthorities);
    return conv;
  }

  private static Collection<GrantedAuthority> emptyAuthorities(Jwt jwt) {
    return Collections.emptyList();
  }
}
