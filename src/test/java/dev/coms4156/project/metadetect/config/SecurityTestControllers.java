package dev.coms4156.project.metadetect.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal controllers used by {@link SecurityConfigMvcTest} to verify security rules.
 */
@RestController
class SecurityTestControllers {

  @GetMapping("/health")
  String health() {
    return "ok";
  }

  @GetMapping("/actuator/info")
  String info() {
    return "info";
  }

  @GetMapping("/auth/login")
  String login() {
    return "login";
  }

  @GetMapping("/auth/signup")
  String signup() {
    return "signup";
  }

  @GetMapping("/secured")
  String secured() {
    return "secure";
  }
}
