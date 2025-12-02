package dev.coms4156.project.metadetect.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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

    var analyzeStart = new Dtos.AnalyzeStartResponse("analysis-123");
    assertThat(analyzeStart.analysisId()).isEqualTo("analysis-123");

    var analyzeStatus = new Dtos.AnalysisStatusResponse(
        "analysis-123",
        "image-1",
        "PENDING",
        Instant.parse("2025-01-01T00:00:00Z"),
        null,
        null
    );
    assertThat(analyzeStatus.status()).isEqualTo("PENDING");
    assertThat(analyzeStatus.completedAt()).isNull();

    var compare = new Dtos.AnalyzeCompareResponse("DONE", null, "note");
    assertThat(compare.status()).isEqualTo("DONE");
    assertThat(compare.similarity()).isNull();
    assertThat(compare.note()).isEqualTo("note");

    var analyzeResponse = new Dtos.AnalyzeResponse(
        "analysis-123",
        0.5,
        "DONE",
        Instant.parse("2025-01-01T00:00:00Z"),
        Map.of("k", "v")
    );
    assertThat(analyzeResponse.details()).containsEntry("k", "v");

    var meta = new Dtos.MetadataResponse("img-1", Map.of("camera", "abc"));
    assertThat(meta.exifData()).containsEntry("camera", "abc");

    var imageDto = new Dtos.ImageDto(
        "img-1",
        "file.jpg",
        "user-1",
        Instant.parse("2025-01-01T00:00:00Z").atOffset(java.time.ZoneOffset.UTC),
        List.of("l1", "l2"),
        "note"
    );
    assertThat(imageDto.labels()).containsExactly("l1", "l2");
    assertThat(imageDto.note()).isEqualTo("note");

    var updateImage = new Dtos.UpdateImageRequest("note2", List.of("x"));
    assertThat(updateImage.labels()).containsExactly("x");

    var log = new Dtos.LoginRequest("e@x.com", "pw");
    assertThat(log.email()).isEqualTo("e@x.com");
    assertThat(log.password()).isEqualTo("pw");

    var ref = new Dtos.RefreshRequest("rfr");
    assertThat(ref.refreshToken()).isEqualTo("rfr");

    var auth = new Dtos.AuthResponse("u1", "token");
    assertThat(auth.userId()).isEqualTo("u1");
    assertThat(auth.token()).isEqualTo("token");

    var compareLegacy = new Dtos.CompareResponse("a", "b", 0.7);
    assertThat(compareLegacy.imageIdA()).isEqualTo("a");
    assertThat(compareLegacy.similarity()).isEqualTo(0.7);

    var options = new Dtos.AnalyzeOptions(true, false, null, true);
    assertThat(options.runMetadata()).isTrue();
    assertThat(options.runGan()).isNull();
  }
}
