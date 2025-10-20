package dev.coms4156.project.metadetect.controller;

import dev.coms4156.project.metadetect.dto.Dtos;
import dev.coms4156.project.metadetect.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;



/**
 * Minimal register/login stubs for Iteration 1.
 */
@RestController
@RequestMapping("/api/users")
public class AuthController {

  private final UserService userService;

  public AuthController(UserService userService) {
    this.userService = userService;
  }

  @PostMapping("/register")
  public ResponseEntity<Dtos.AuthResponse> register(@RequestBody Dtos.RegisterRequest req) {
    // TODO: create user and return token (stub ok)
    return ResponseEntity.ok(new Dtos.AuthResponse("stub-user", "stub-token"));
  }

  @PostMapping("/login")
  public ResponseEntity<Dtos.AuthResponse> login(@RequestBody Dtos.LoginRequest req) {
    // TODO: verify credentials; return token (stub ok)
    return ResponseEntity.ok(new Dtos.AuthResponse("stub-user", "stub-token"));
  }
}
