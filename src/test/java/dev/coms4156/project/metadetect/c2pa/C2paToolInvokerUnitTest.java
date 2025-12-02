package dev.coms4156.project.metadetect.c2pa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.coms4156.project.metadetect.c2pa.C2paToolInvoker.C2paMetadata;
import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit-level coverage for {@link C2paToolInvoker} without calling the real CLI.
 * Exercises JSON parsing branches, AI keyword detection, and CLI failure paths.
 */
class C2paToolInvokerUnitTest {

  /** Invoke the private parseMetadataFromJson helper for deterministic coverage. */
  private static C2paMetadata parse(String json) throws Exception {
    Method m = C2paToolInvoker.class
        .getDeclaredMethod("parseMetadataFromJson", String.class);
    m.setAccessible(true);
    return (C2paMetadata) m.invoke(new C2paToolInvoker("noop"), json);
  }

  @Test
  void parseMetadata_detectsActiveManifest_andAiGenerator() throws Exception {
    String json = """
        {
          "manifests": {
            "m1": {
              "claim": {
                "claim_generator_info": { "name": "MidJourney AI Studio" }
              }
            },
            "m2": { "claim": { "claim_generator": "CameraCo" } }
          },
          "active_manifest": "m1"
        }
        """;

    C2paMetadata meta = parse(json);

    assertEquals(1, meta.getc2paHasManifest());
    assertEquals(2, meta.getc2paManifestCount());
    assertThat(meta.getc2paClaimGenerator()).containsIgnoringCase("midjourney");
    assertEquals(1, meta.getc2paClaimGeneratorIsAi());
    assertEquals(0, meta.getc2paErrorFlag());
  }

  @Test
  void parseMetadata_noManifest_setsFlagsToZero() throws Exception {
    String json = "{\"manifests\":{}}";

    C2paMetadata meta = parse(json);

    assertEquals(0, meta.getc2paHasManifest());
    assertEquals(0, meta.getc2paManifestCount());
    assertEquals(0, meta.getc2paClaimGeneratorIsAi());
    assertEquals(0, meta.getc2paErrorFlag());
  }

  @Test
  void parseMetadata_invalidJson_setsErrorFlag() throws Exception {
    C2paMetadata meta = parse("{not-json}");
    assertEquals(1, meta.getc2paErrorFlag());
    assertThat(meta.getc2paErrorMessage()).contains("Failed to parse");
  }

  @Test
  void parseMetadata_missingManifests_setsHasManifestZero() throws Exception {
    String json = "{\"some\":\"field\"}";
    C2paMetadata meta = parse(json);
    assertEquals(0, meta.getc2paHasManifest());
    assertEquals(0, meta.getc2paManifestCount());
    assertEquals(0, meta.getc2paErrorFlag());
  }

  @Test
  void parseMetadata_manifestsNotObject_setsHasManifestZero() throws Exception {
    String json = "{\"manifests\":[]}";
    C2paMetadata meta = parse(json);
    assertEquals(0, meta.getc2paHasManifest());
    assertEquals(0, meta.getc2paManifestCount());
  }

  @Test
  void parseMetadata_activeManifestMissing_fallsBackToFirst() throws Exception {
    String json = """
        {
          "manifests": {
            "m1": { "claim": { "claim_generator": "CameraCo" } },
            "m2": { "claim": { "claim_generator": "firefly v1" } }
          },
          "active_manifest": "not-here"
        }
        """;

    C2paMetadata meta = parse(json);
    assertEquals(1, meta.getc2paHasManifest());
    assertEquals(2, meta.getc2paManifestCount());
    // fallback picks first entry (CameraCo), not AI
    assertThat(meta.getc2paClaimGenerator()).contains("CameraCo");
    assertEquals(0, meta.getc2paClaimGeneratorIsAi());
  }

  @Test
  void parseMetadata_legacyClaimGenerator_setsNonAi() throws Exception {
    String json = """
        {
          "manifests": {
            "only": {
              "claim": {
                "claim_generator": "Legacy Camera"
              }
            }
          },
          "active_manifest": "only"
        }
        """;

    C2paMetadata meta = parse(json);
    assertEquals(1, meta.getc2paHasManifest());
    assertEquals("Legacy Camera", meta.getc2paClaimGenerator());
    assertEquals(0, meta.getc2paClaimGeneratorIsAi());
  }

  @Test
  void parseMetadata_nullRoot_returnsError() throws Exception {
    C2paMetadata meta = parse("null");
    assertEquals(0, meta.getc2paHasManifest());
    assertEquals(0, meta.getc2paManifestCount());
    assertEquals(0, meta.getc2paClaimGeneratorIsAi());
  }

  @Test
  void parseMetadata_activeManifestIdPresent_butMissingInManifests_handlesNull() throws Exception {
    String json = """
        {
          "manifests": {
            "m1": { "claim": { "claim_generator_info": { "name": "Adobe" } } }
          },
          "active_manifest": "missing-id"
        }
        """;

    C2paMetadata meta = parse(json);
    // falls back to first manifest and extracts generator
    assertEquals("Adobe", meta.getc2paClaimGenerator());
  }

  @Test
  void parseMetadata_firstManifestUsedWhenActiveManifestNodeMissing() throws Exception {
    String json = """
        {
          "manifests": {
            "a": { "claim": { "claim_generator": "a-cam" } },
            "b": { "claim": { "claim_generator": "b-cam" } }
          }
        }
        """;

    C2paMetadata meta = parse(json);
    assertEquals("a-cam", meta.getc2paClaimGenerator());
  }

  @Test
  void parseMetadata_claimNodeMissing_usesActiveManifestNode() throws Exception {
    String json = """
        {
          "manifests": {
            "a": { "claim_generator": "direct-legacy" }
          },
          "active_manifest": "a"
        }
        """;

    C2paMetadata meta = parse(json);
    assertEquals("direct-legacy", meta.getc2paClaimGenerator());
  }

  @Test
  void parseMetadata_activeManifestIdResolves_claimGeneratorInfo_ai() throws Exception {
    String json = """
        {
          "manifests": {
            "m1": {
              "claim": {
                "claim_generator_info": { "name": "MidJourney v5" }
              }
            }
          },
          "active_manifest": "m1"
        }
        """;

    C2paMetadata meta = parse(json);
    assertEquals(1, meta.getc2paHasManifest());
    assertEquals(1, meta.getc2paManifestCount());
    assertEquals("MidJourney v5", meta.getc2paClaimGenerator());
    assertEquals(1, meta.getc2paClaimGeneratorIsAi());
  }

  @Test
  void parseMetadata_manifestWithoutClaim_usesManifestFields() throws Exception {
    String json = """
        {
          "manifests": {
            "solo": {
              "claim_generator": "NonAI Tool"
            }
          },
          "active_manifest": "solo"
        }
        """;

    C2paMetadata meta = parse(json);
    assertEquals("NonAI Tool", meta.getc2paClaimGenerator());
    assertEquals(0, meta.getc2paClaimGeneratorIsAi());
  }

  @Test
  void parseMetadata_activeManifestExplicitNull_fallsBackToFirst() throws Exception {
    String json = """
        {
          "manifests": {
            "a": { "claim": { "claim_generator": "CameraCo" } }
          },
          "active_manifest": null
        }
        """;

    C2paMetadata meta = parse(json);
    assertEquals(1, meta.getc2paHasManifest());
    assertEquals(1, meta.getc2paManifestCount());
    assertEquals("CameraCo", meta.getc2paClaimGenerator());
    assertEquals(0, meta.getc2paClaimGeneratorIsAi());
  }

  @Test
  void parseMetadata_manifestClaimWithoutGenerator_leavesGeneratorNull() throws Exception {
    String json = """
        {
          "manifests": {
            "m1": { "claim": { "foo": "bar" } }
          },
          "active_manifest": "m1"
        }
        """;

    C2paMetadata meta = parse(json);
    assertEquals(1, meta.getc2paHasManifest());
    assertEquals(1, meta.getc2paManifestCount());
    assertEquals(0, meta.getc2paClaimGeneratorIsAi());
    assertThat(meta.getc2paClaimGenerator()).isNull();
  }

  @Test
  void parseMetadata_manifestWithoutClaimOrGenerator_keepsAiFlagZero() throws Exception {
    String json = """
        {
          "manifests": {
            "m1": { "other": 123 }
          }
        }
        """;

    C2paMetadata meta = parse(json);
    assertEquals(1, meta.getc2paHasManifest());
    assertEquals(1, meta.getc2paManifestCount());
    assertThat(meta.getc2paClaimGenerator()).isNull();
    assertEquals(0, meta.getc2paClaimGeneratorIsAi());
  }

  @Test
  void extractMetadata_nullFile_returnsError() {
    C2paToolInvoker invoker = new C2paToolInvoker("no-cli");

    C2paMetadata meta = invoker.extractMetadata(null);

    assertEquals(1, meta.getc2paErrorFlag());
    assertThat(meta.getc2paErrorMessage()).contains("null");
  }

  @Test
  void extractMetadata_cliFailure_setsErrorFlag() throws Exception {
    File tmp = File.createTempFile("c2pa-", ".bin");
    // Use /bin/echo to return non-JSON stdout, ensuring parse failure on success exit
    C2paToolInvoker invoker = new C2paToolInvoker("/bin/echo");

    C2paMetadata meta = invoker.extractMetadata(tmp);

    // parseMetadataFromJson will fail, resulting in error flag
    assertEquals(1, meta.getc2paErrorFlag());
    assertThat(meta.getc2paErrorMessage()).contains("Failed to parse");
    tmp.delete();
  }

  @Test
  void extractMetadata_emptyStdout_returnsError(@TempDir Path tmp) throws Exception {
    Path script = tmp.resolve("c2pa-empty.sh");
    Files.writeString(script, "#!/bin/sh\nexit 0\n", StandardCharsets.UTF_8);
    script.toFile().setExecutable(true);

    C2paToolInvoker invoker = new C2paToolInvoker(script.toAbsolutePath().toString());
    File img = File.createTempFile("img-", ".bin");

    C2paMetadata meta = invoker.extractMetadata(img);
    assertEquals(1, meta.getc2paErrorFlag());
    assertThat(meta.getc2paErrorMessage()).contains("empty output");
  }

  @Test
  void extractMetadata_toolMissing_returnsError() throws Exception {
    C2paToolInvoker invoker = new C2paToolInvoker("/does/not/exist/c2pa");
    File img = File.createTempFile("img-", ".bin");

    C2paMetadata meta = invoker.extractMetadata(img);

    assertEquals(1, meta.getc2paErrorFlag());
    assertThat(meta.getc2paErrorMessage()).contains("not found");
  }

  @Test
  void extractMetadata_toolNotExecutable_warnsAndReturnsError(@TempDir Path tmp) throws Exception {
    Path tool = tmp.resolve("c2pa-noexec");
    Files.writeString(tool, "#!/bin/sh\nexit 0\n", StandardCharsets.UTF_8);
    // intentionally leave non-executable to hit the branch
    File img = File.createTempFile("img-", ".bin");

    C2paToolInvoker invoker = new C2paToolInvoker(tool.toAbsolutePath().toString());
    C2paMetadata meta = invoker.extractMetadata(img);

    assertEquals(1, meta.getc2paErrorFlag());
    assertThat(meta.getc2paErrorMessage()).contains(tool.toString());
  }

  @Test
  void extractMetadata_noClaimFound_returnsSoftNoManifest(@TempDir Path tmp) throws Exception {
    Path script = tmp.resolve("c2pa-noclaim.sh");
    Files.writeString(script, """
        #!/bin/sh
        echo "no claim found" 1>&2
        exit 1
        """, StandardCharsets.UTF_8);
    script.toFile().setExecutable(true);

    C2paToolInvoker invoker = new C2paToolInvoker(script.toAbsolutePath().toString());
    File img = File.createTempFile("img-", ".bin");

    C2paMetadata meta = invoker.extractMetadata(img);
    assertEquals(0, meta.getc2paErrorFlag());
    assertEquals(0, meta.getc2paHasManifest());
    assertEquals(0, meta.getc2paManifestCount());
  }

  @Test
  void extractMetadata_exitNonZero_withDifferentStderr_isError(@TempDir Path tmp) throws Exception {
    Path script = tmp.resolve("c2pa-fail.sh");
    Files.writeString(script, """
        #!/bin/sh
        echo "bad" 1>&2
        exit 2
        """, StandardCharsets.UTF_8);
    script.toFile().setExecutable(true);

    C2paToolInvoker invoker = new C2paToolInvoker(script.toAbsolutePath().toString());
    File img = File.createTempFile("img-", ".bin");

    C2paMetadata meta = invoker.extractMetadata(img);
    assertEquals(1, meta.getc2paErrorFlag());
    assertThat(meta.getc2paErrorMessage()).contains("exit code 2");
  }

  @Test
  void extractMetadata_stdoutEmpty_returnsError(@TempDir Path tmp) throws Exception {
    Path script = tmp.resolve("c2pa-empty-stdout.sh");
    Files.writeString(script, """
        #!/bin/sh
        echo "" > /dev/null
        exit 0
        """, StandardCharsets.UTF_8);
    script.toFile().setExecutable(true);

    C2paToolInvoker invoker = new C2paToolInvoker(script.toAbsolutePath().toString());
    File img = File.createTempFile("img-", ".bin");

    C2paMetadata meta = invoker.extractMetadata(img);
    assertEquals(1, meta.getc2paErrorFlag());
    assertThat(meta.getc2paErrorMessage()).contains("empty output");
  }

  @Test
  void extractMetadata_nonZero_noStderr_usesErrorWithoutPipe(@TempDir Path tmp) throws Exception {
    Path script = tmp.resolve("c2pa-no-stderr.sh");
    Files.writeString(script, "#!/bin/sh\nexit 3\n", StandardCharsets.UTF_8);
    script.toFile().setExecutable(true);

    C2paToolInvoker invoker = new C2paToolInvoker(script.toAbsolutePath().toString());
    File img = File.createTempFile("img-", ".bin");

    C2paMetadata meta = invoker.extractMetadata(img);
    assertEquals(1, meta.getc2paErrorFlag());
    assertThat(meta.getc2paErrorMessage()).contains("exit code 3");
    assertThat(meta.getc2paErrorMessage()).doesNotContain("stderr:");
  }

  @Test
  void isAiClaimGenerator_returnsFalseForNonAi() throws Exception {
    Method m = C2paToolInvoker.class
        .getDeclaredMethod("isAiClaimGenerator", String.class);
    m.setAccessible(true);
    boolean result = (boolean) m.invoke(null, "Canon Camera");
    assertFalse(result);
  }
}
