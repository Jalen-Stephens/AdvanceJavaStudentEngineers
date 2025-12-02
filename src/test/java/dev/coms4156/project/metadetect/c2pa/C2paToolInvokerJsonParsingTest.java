package dev.coms4156.project.metadetect.c2pa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.coms4156.project.metadetect.c2pa.C2paToolInvoker.C2paMetadata;
import org.junit.jupiter.api.Test;

/**
 * JSON-focused unit tests for {@link C2paToolInvoker} that avoid invoking the
 * platform-specific c2patool binary. These run on any OS to keep coverage high
 * even when the Linux binary cannot execute locally.
 */
class C2paToolInvokerJsonParsingTest {

  private static final String VALID_AI_JSON = """
      {
        "manifests": {
          "m1": {
            "claim": {
              "claim_generator_info": { "name": "Adobe Firefly v1" }
            }
          }
        },
        "active_manifest": "m1"
      }
      """;

  private static final String VALID_NON_AI_JSON = """
      {
        "manifests": {
          "m1": {
            "claim": {
              "claim_generator": "Adobe Photoshop 25.1"
            }
          }
        },
        "active_manifest": "m1"
      }
      """;

  private static final String SCREENSHOT_JSON = """
      {
        "manifests": {
          "m1": {
            "claim": {
              "claim_generator_info": { "name": "Snipping Tool 3.0" }
            }
          }
        },
        "active_manifest": "m1"
      }
      """;

  private static final String CAPTURE_TYPE_SCREENSHOT_JSON = """
      {
        "manifests": {
          "m1": {
            "claim": {
              "claim_generator": "Browser Capture",
              "capture_type": "screenshot"
            }
          }
        },
        "active_manifest": "m1"
      }
      """;

  private static final String NO_MANIFEST_JSON = """
      {
        "manifests": { }
      }
      """;

  private final C2paToolInvoker invoker = new C2paToolInvoker("tools/c2patool/c2patool");

  @Test
  void parse_validAiImage_setsAiFlagAndManifestFields() {
    C2paMetadata meta = invoker.parseMetadataFromJsonForTests(VALID_AI_JSON);

    assertEquals(1, meta.getc2paHasManifest());
    assertEquals(1, meta.getc2paManifestCount());
    assertEquals("Adobe Firefly v1", meta.getc2paClaimGenerator());
    assertEquals(1, meta.getc2paClaimGeneratorIsAi());
    assertEquals(0, meta.getc2paIsScreenshot());
    assertEquals(0, meta.getc2paErrorFlag());
    assertNull(meta.getc2paErrorMessage());
  }

  @Test
  void parse_validNonAiImage_leavesAiFlagZero() {
    C2paMetadata meta = invoker.parseMetadataFromJsonForTests(VALID_NON_AI_JSON);

    assertEquals(1, meta.getc2paHasManifest());
    assertEquals(1, meta.getc2paManifestCount());
    assertEquals("Adobe Photoshop 25.1", meta.getc2paClaimGenerator());
    assertEquals(0, meta.getc2paClaimGeneratorIsAi());
    assertEquals(0, meta.getc2paIsScreenshot());
    assertEquals(0, meta.getc2paErrorFlag());
    assertNull(meta.getc2paErrorMessage());
  }

  @Test
  void parse_screenshotClaimGenerator_setsScreenshotFlag() {
    C2paMetadata meta = invoker.parseMetadataFromJsonForTests(SCREENSHOT_JSON);

    assertEquals(1, meta.getc2paHasManifest());
    assertEquals(1, meta.getc2paManifestCount());
    assertEquals(1, meta.getc2paIsScreenshot());
    assertEquals(0, meta.getc2paErrorFlag());
    assertNull(meta.getc2paErrorMessage());
  }

  @Test
  void parse_captureTypeScreenshot_setsScreenshotFlag() {
    C2paMetadata meta = invoker.parseMetadataFromJsonForTests(CAPTURE_TYPE_SCREENSHOT_JSON);

    assertEquals(1, meta.getc2paHasManifest());
    assertEquals(1, meta.getc2paManifestCount());
    assertEquals(1, meta.getc2paIsScreenshot());
    assertEquals(0, meta.getc2paErrorFlag());
    assertNull(meta.getc2paErrorMessage());
  }

  @Test
  void parse_noManifest_softSuccessWithoutError() {
    C2paMetadata meta = invoker.parseMetadataFromJsonForTests(NO_MANIFEST_JSON);

    assertEquals(0, meta.getc2paHasManifest());
    assertEquals(0, meta.getc2paManifestCount());
    assertNull(meta.getc2paClaimGenerator());
    assertEquals(0, meta.getc2paClaimGeneratorIsAi());
    assertEquals(0, meta.getc2paIsScreenshot());
    assertEquals(0, meta.getc2paErrorFlag());
    assertNull(meta.getc2paErrorMessage());
  }

  @Test
  void parse_malformedJson_setsErrorFlag() {
    String malformed = "{ \"foo\": \"bar\" ";
    C2paMetadata meta = invoker.parseMetadataFromJsonForTests(malformed);

    assertEquals(0, meta.getc2paHasManifest());
    assertEquals(0, meta.getc2paManifestCount());
    assertEquals(1, meta.getc2paErrorFlag());
    assertTrue(meta.getc2paErrorMessage() != null && !meta.getc2paErrorMessage().isBlank());
  }

  @Test
  void factories_coverNoManifestAndErrorHelpers() {
    C2paMetadata noManifest = C2paMetadata.noManifest();
    assertEquals(0, noManifest.getc2paHasManifest());
    assertEquals(0, noManifest.getc2paManifestCount());
    assertEquals(0, noManifest.getc2paIsScreenshot());
    assertEquals(0, noManifest.getc2paErrorFlag());

    C2paMetadata err = C2paMetadata.error("boom");
    assertEquals(1, err.getc2paErrorFlag());
    assertEquals("boom", err.getc2paErrorMessage());
    assertEquals(0, err.getc2paHasManifest());
    assertEquals(0, err.getc2paIsScreenshot());
  }
}
