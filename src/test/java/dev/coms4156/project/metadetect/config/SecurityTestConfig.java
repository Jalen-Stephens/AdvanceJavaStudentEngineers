package dev.coms4156.project.metadetect.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security overrides used only in test slices.
 * This configuration:
 *  - Permits unauthenticated calls to /auth/signup, /auth/login, /auth/refresh
 *    so controller tests can run without real Supabase auth
 *  - Leaves /auth/me (and all other endpoints) protected
 *  - Disables CSRF protection for these auth endpoints to avoid false positives
 * This allows realistic test behavior without bypassing authentication globally.
 */
@TestConfiguration
public class SecurityTestConfig {

  @Bean
  SecurityFilterChain testSecurity(HttpSecurity http) throws Exception {
    http
      // Permit auth flows during tests without CSRF interference
      .csrf(csrf -> csrf.ignoringRequestMatchers("/auth/**"))
      .authorizeHttpRequests(auth -> auth
        .requestMatchers("/auth/signup",
          "/auth/login",
          "/auth/refresh")
        .permitAll()
        .anyRequest().authenticated()
      )
        .httpBasic(Customizer.withDefaults());

    return http.build();
  }
}
