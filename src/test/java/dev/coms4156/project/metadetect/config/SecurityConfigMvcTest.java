package dev.coms4156.project.metadetect.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tests security rules, CORS, and JWT decoder.
 */
@SpringBootTest(
    properties = {
      // 32 bytes for HS256
      "metadetect.supabase.jwtSecret=0123456789abcdef0123456789abcdef",
      "metadetect.supabase.url=https://unit.supabase.co"
    }
)
@AutoConfigureMockMvc
@Import({SecurityConfigMvcTest.TestControllers.class, SecurityConfig.class})
@EnableAutoConfiguration
class SecurityConfigMvcTest {

  private static final String SECRET_256 =
      "0123456789abcdef0123456789abcdef"; // 32B

  @Autowired MockMvc mvc;
  @Autowired SecurityConfig config;

  @Test
  @DisplayName("permitAll endpoints are reachable without token")
  void publicEndpoints_noAuthRequired() throws Exception {
    mvc.perform(get("/health")).andExpect(status().isOk());
    mvc.perform(get("/actuator/info")).andExpect(status().isOk());
    mvc.perform(get("/auth/login")).andExpect(status().isOk());
    mvc.perform(get("/auth/signup")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("secured endpoints require auth")
  void securedEndpoints_requireAuth() throws Exception {
    mvc.perform(get("/secured")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("OPTIONS preflight is permitted and sets CORS headers")
  void optionsPreflight_corsHeaders() throws Exception {
    mvc.perform(
        options("/secured")
          .header("Origin", "http://localhost:3000")
          .header("Access-Control-Request-Method", "GET"))
      .andExpect(status().isOk())
      .andExpect(header().string("Access-Control-Allow-Origin", "*"))
        .andExpect(header().string("Vary", "Origin"));
  }

  @Test
  @DisplayName("corsConfigurationSource returns expected values")
  void corsBean_values() {
    var source = config.corsConfigurationSource();
    // supply a request to avoid NPE
    var req = new MockHttpServletRequest("GET", "/any");
    var cfg = source.getCorsConfiguration(req);
    assertThat(cfg.getAllowedOrigins()).contains("*");
    assertThat(cfg.getAllowedMethods()).contains("GET", "POST", "PUT", "DELETE", "OPTIONS");
    assertThat(cfg.getAllowedHeaders()).contains("Authorization", "Content-Type");
    assertThat(cfg.getAllowCredentials()).isFalse();
  }

  @Test
  @DisplayName("jwtDecoder validates HS256 and issuer (no slash)")
  void jwtDecoder_validIssuer_noSlash() throws Exception {
    String base = "https://unit.supabase.co";
    JwtDecoder dec = config.jwtDecoder(SECRET_256, base);
    String iss = base + "/auth/v1";
    String token = hs256(iss, SECRET_256);
    Jwt jwt = dec.decode(token);
    assertThat(jwt.getIssuer().toString()).isEqualTo(iss);
  }

  @Test
  @DisplayName("jwtDecoder validates HS256 and issuer (with slash)")
  void jwtDecoder_validIssuer_withSlash() throws Exception {
    String base = "https://unit.supabase.co/";
    JwtDecoder dec = config.jwtDecoder(SECRET_256, base);
    String iss = base + "auth/v1";
    String token = hs256(iss, SECRET_256);
    Jwt jwt = dec.decode(token);
    assertThat(jwt.getIssuer().toString()).isEqualTo(iss);
  }

  @Test
  @DisplayName("jwtDecoder rejects wrong issuer")
  void jwtDecoder_wrongIssuer_rejected() throws Exception {
    String base = "https://unit.supabase.co";
    JwtDecoder dec = config.jwtDecoder(SECRET_256, base);
    String badIss = "https://other.supabase.co/auth/v1";
    String token = hs256(badIss, SECRET_256);
    assertThatThrownBy(() -> dec.decode(token)).isInstanceOf(JwtException.class);
  }

  // build a tiny HS256 JWT
  private static String hs256(String issuer, String secret) throws Exception {
    var header = new JWSHeader.Builder(JWSAlgorithm.HS256).build();
    var now = new Date();
    var exp = Date.from(Instant.now().plusSeconds(300));
    var claims = new JWTClaimsSet.Builder()
        .issuer(issuer).subject("sub-123")
        .issueTime(now).expirationTime(exp).build();
    var jwt = new SignedJWT(header, claims);
    var signer = new MACSigner(secret.getBytes(StandardCharsets.UTF_8));
    jwt.sign(signer);
    return jwt.serialize();
  }

  // tiny controllers for predictable responses
  @RestController
  static class TestControllers {

    @GetMapping("/health")
    public String health() {
      return "ok";
    }

    @GetMapping("/actuator/info")
    public String info() {
      return "info";
    }

    @GetMapping("/auth/login")
    public String login() {
      return "login";
    }

    @GetMapping("/auth/signup")
    public String signup() {
      return "signup";
    }

    @GetMapping("/secured")
    public String secured() {
      return "secure";
    }
  }
}
