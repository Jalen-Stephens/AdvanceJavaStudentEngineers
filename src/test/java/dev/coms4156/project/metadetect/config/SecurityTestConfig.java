package dev.coms4156.project.metadetect.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Test security configuration that relaxes authentication requirements
 * for proxy endpoints under /auth/* during controller-slice tests,
 * while retaining authentication for /auth/me.
 */
@TestConfiguration
public class SecurityTestConfig {

  @Bean
  SecurityFilterChain testSecurity(HttpSecurity http) throws Exception {
    http
      .csrf(csrf -> csrf.ignoringRequestMatchers("/auth/**"))
      .authorizeHttpRequests(auth -> auth
        .requestMatchers("/auth/signup", "/auth/login", "/auth/refresh").permitAll()
        .anyRequest().authenticated()
      )
        .httpBasic(Customizer.withDefaults());

    return http.build();
  }
}
