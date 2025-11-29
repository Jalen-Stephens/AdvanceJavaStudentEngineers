package dev.coms4156.project.metadetect.config;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Direct unit coverage for the issuer ternary in {@link SecurityConfig#jwtDecoder}.
 * Executes both the trailing-slash and non-trailing-slash branches to satisfy JaCoCo.
 */
class SecurityConfigJwtDecoderBranchTest {

  private static final String SECRET_256 =
      "0123456789abcdef0123456789abcdef"; // 32B HS256 key

  @Test
  @DisplayName("jwtDecoder builds issuer without trailing slash")
  void jwtDecoder_branch_noTrailingSlash() throws Exception {
    SecurityConfig cfg = new SecurityConfig();
    String base = "https://unit.supabase.co";
    JwtDecoder decoder = cfg.jwtDecoder(SECRET_256, base);
    String iss = base + "/auth/v1";
    Jwt jwt = decoder.decode(hs256(iss, SECRET_256));
    assertThat(jwt.getIssuer().toString()).isEqualTo(iss);
  }

  @Test
  @DisplayName("jwtDecoder builds issuer with trailing slash")
  void jwtDecoder_branch_trailingSlash() throws Exception {
    SecurityConfig cfg = new SecurityConfig();
    String base = "https://unit.supabase.co/";
    JwtDecoder decoder = cfg.jwtDecoder(SECRET_256, base);
    String iss = base + "auth/v1";
    Jwt jwt = decoder.decode(hs256(iss, SECRET_256));
    assertThat(jwt.getIssuer().toString()).isEqualTo(iss);
  }

  // Helper to mint a tiny HS256 JWT for the given issuer.
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
}
