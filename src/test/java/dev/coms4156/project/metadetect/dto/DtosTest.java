package dev.coms4156.project.metadetect.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Smoke test to register coverage for DTO record constructors and accessors.
 */
class DtosTest {
  @Test
  void recordsRoundTrip() {
    var reg = new Dtos.RegisterRequest("e@x.com", "pw");
    assertThat(reg.email()).isEqualTo("e@x.com");
    assertThat(reg.password()).isEqualTo("pw");

    var log = new Dtos.LoginRequest("e@x.com", "pw");
    assertThat(log.email()).isEqualTo("e@x.com");
    assertThat(log.password()).isEqualTo("pw");

    var ref = new Dtos.RefreshRequest("rfr");
    assertThat(ref.refreshToken()).isEqualTo("rfr");
  }
}
