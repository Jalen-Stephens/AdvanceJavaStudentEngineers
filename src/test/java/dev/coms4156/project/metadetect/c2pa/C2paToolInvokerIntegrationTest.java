package dev.coms4156.project.metadetect.c2pa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.coms4156.project.metadetect.c2pa.C2paToolInvoker.C2paMetadata;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration-style tests for C2paToolInvoker using the repository-local
 * c2patool binary stored at ./tools/c2patool/c2patool.
 * IMPORTANT:
 * Place these files in src/test/resources/mock-images/:
 *   - ai_dawg_valid.png
 *   - ai_dawg_invalid.png (a tampered copy of the valid one)
 *   - real_no_manifest.HEIC
 */
public class C2paToolInvokerIntegrationTest {

  /** True when host OS can execute the bundled macOS c2patool (local dev). */
  private boolean c2paToolSupported() {
    String os = System.getProperty("os.name", "").toLowerCase();
    boolean isMac = os.contains("mac");
    File tool = new File("tools/c2patool/c2patool");
    return isMac && tool.exists() && tool.canExecute();
  }

  /** Resolve c2patool binary from repository (no system install needed). */
  private File resolveLocalTool() {
    File tool = new File("tools/c2patool/c2patool");
    assertTrue(tool.exists(), "c2patool binary missing at tools/c2patool/c2patool");
    assertTrue(tool.canExecute(), "c2patool binary is not executable; run chmod +x");
    return tool;
  }

  /** Load a test resource file under src/test/resources/c2pa. */
  private File resolveResource(String name) {
    var url = getClass().getClassLoader().getResource("mock-images/" + name);
    assertNotNull(url, "Test resource not found: mock-images/" + name);
    return new File(url.getFile());
  }

  /** Load JSON fixture for non-native environments. */
  private String loadFixtureJson(String name) {
    try (var in = getClass().getClassLoader().getResourceAsStream("c2pa-fixtures/" + name)) {
      Objects.requireNonNull(in, "Fixture not found: c2pa-fixtures/" + name);
      return new String(in.readAllBytes());
    } catch (IOException e) {
      throw new RuntimeException("Unable to load fixture " + name, e);
    }
  }

  @Test
  void validAiImage_hasManifest_andAiClaimGenerator() {
    if (!c2paToolSupported()) {
      // Fallback: validate JSON parsing on non-macOS runners
      C2paToolInvoker invoker = new C2paToolInvoker("unused");
      C2paMetadata meta = invoker.parseMetadataFromJsonForTests(
          loadFixtureJson("valid_ai_output.json"));

      assertNotNull(meta);
      assertEquals(1, meta.getc2paHasManifest());
      assertTrue(meta.getc2paManifestCount() >= 1);
      assertEquals(0, meta.getc2paErrorFlag());
      assertNull(meta.getc2paErrorMessage());
      assertNotNull(meta.getc2paClaimGenerator());
      assertEquals(1, meta.getc2paClaimGeneratorIsAi());
      return;
    }

    File tool = resolveLocalTool();
    C2paToolInvoker invoker = new C2paToolInvoker(tool.getAbsolutePath());

    File validImage = resolveResource("ai_dawg_valid.png");

    C2paMetadata meta = invoker.extractMetadata(validImage);

    assertNotNull(meta);

    // Expected for valid AI-generated image
    assertEquals(1, meta.getc2paHasManifest(), "Manifest should be present");
    assertTrue(meta.getc2paManifestCount() >= 1, "Should detect >= 1 manifest");
    assertEquals(0, meta.getc2paErrorFlag(), "Should not be marked as error");
    assertNull(meta.getc2paErrorMessage(), "No error expected");

    // AI detection (once claimGenerator_info fix is applied)
    assertNotNull(meta.getc2paClaimGenerator(), "claimGenerator should be present");
    assertEquals(1, meta.getc2paClaimGeneratorIsAi(),
        "AI claim generator should be detected");
  }

  @Test
  void tamperedAiImage_stillHasManifest_butSameSchema(@TempDir Path tmp) throws IOException {
    if (!c2paToolSupported()) {
      C2paToolInvoker invoker = new C2paToolInvoker("unused");
      C2paMetadata meta = invoker.parseMetadataFromJsonForTests(
          loadFixtureJson("tampered_ai_output.json"));

      assertNotNull(meta);
      assertEquals(1, meta.getc2paHasManifest());
      assertTrue(meta.getc2paManifestCount() >= 1);
      assertEquals(0, meta.getc2paErrorFlag());
      assertNotNull(meta.getc2paClaimGenerator());
      assertEquals(1, meta.getc2paClaimGeneratorIsAi());
      return;
    }

    File validImage = resolveResource("ai_dawg_valid.png");

    // Copy to temp file
    Path tamperedPath = tmp.resolve("ai_dawg_invalid.png");
    Files.copy(validImage.toPath(), tamperedPath, StandardCopyOption.REPLACE_EXISTING);

    // Flip a byte in the pixel data region
    byte[] data = Files.readAllBytes(tamperedPath);
    int idx = data.length / 2;
    data[idx] ^= 0x01;
    Files.write(tamperedPath, data);

    File tool = resolveLocalTool();
    C2paToolInvoker invoker = new C2paToolInvoker(tool.getAbsolutePath());
    C2paMetadata meta = invoker.extractMetadata(tamperedPath.toFile());

    assertNotNull(meta);

    // Still should detect that a manifest exists
    assertEquals(1, meta.getc2paHasManifest(),
        "Tampered version should still appear to have a manifest");

    assertTrue(meta.getc2paManifestCount() >= 1,
        "Manifest count should be unchanged");

    // Soft-success case: no CLI error, so errorFlag should still be 0
    assertEquals(0, meta.getc2paErrorFlag(),
        "Logical invalidity is not yet surfaced as an ML 'error'");

    // AI-origin should still be detected
    assertNotNull(meta.getc2paClaimGenerator());
    assertEquals(1, meta.getc2paClaimGeneratorIsAi(),
        "Tampered copy should still show AI claim generator");
  }

  @Test
  void noManifestImage_returnsSoftSuccess_noError() {
    if (!c2paToolSupported()) {
      C2paToolInvoker invoker = new C2paToolInvoker("unused");
      C2paMetadata meta = invoker.parseMetadataFromJsonForTests(
          loadFixtureJson("no_manifest_output.json"));

      assertNotNull(meta);
      assertEquals(0, meta.getc2paHasManifest());
      assertEquals(0, meta.getc2paManifestCount());
      assertEquals(0, meta.getc2paErrorFlag());
      assertNull(meta.getc2paErrorMessage());
      return;
    }

    File tool = resolveLocalTool();
    C2paToolInvoker invoker = new C2paToolInvoker(tool.getAbsolutePath());

    File noManifest = resolveResource("real_no_manifest.HEIC");

    C2paMetadata meta = invoker.extractMetadata(noManifest);

    assertNotNull(meta);

    // No C2PA:
    assertEquals(0, meta.getc2paHasManifest());
    assertEquals(0, meta.getc2paManifestCount());

    // No errors (soft success)
    assertEquals(0, meta.getc2paErrorFlag());
    assertNull(meta.getc2paErrorMessage());
  }
}
