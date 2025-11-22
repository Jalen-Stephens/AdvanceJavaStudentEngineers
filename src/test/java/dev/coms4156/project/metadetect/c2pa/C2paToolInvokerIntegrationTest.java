package dev.coms4156.project.metadetect.c2pa;

import static org.junit.jupiter.api.Assertions.*;

import dev.coms4156.project.metadetect.c2pa.C2paToolInvoker.C2paMetadata;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration-style tests for C2paToolInvoker using the repository-local
 * c2patool binary stored at ./tools/c2patool/c2patool.
 *
 * IMPORTANT:
 * Place these files in src/test/resources/mock-images/:
 *   - ai_dawg_valid.png
 *   - ai_dawg_invalid.png (a tampered copy of the valid one)
 *   - real_no_manifest.HEIC
 */
public class C2paToolInvokerIntegrationTest {

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

  @Test
  void validAiImage_hasManifest_andAiClaimGenerator() {
    File tool = resolveLocalTool();
    C2paToolInvoker invoker = new C2paToolInvoker(tool.getAbsolutePath());

    File validImage = resolveResource("ai_dawg_valid.png");

    C2paMetadata meta = invoker.extractMetadata(validImage);

    assertNotNull(meta);

    // Expected for valid AI-generated image
    assertEquals(1, meta.getC2pa_hasManifest(), "Manifest should be present");
    assertTrue(meta.getC2pa_manifestCount() >= 1, "Should detect >= 1 manifest");
    assertEquals(0, meta.getC2pa_errorFlag(), "Should not be marked as error");
    assertNull(meta.getC2pa_errorMessage(), "No error expected");

    // AI detection (once claimGenerator_info fix is applied)
    assertNotNull(meta.getC2pa_claimGenerator(), "claimGenerator should be present");
    assertEquals(1, meta.getC2pa_claimGeneratorIsAI(),
        "AI claim generator should be detected");
  }

  @Test
  void tamperedAiImage_stillHasManifest_butSameSchema(@TempDir Path tmp) throws IOException {

    File tool = resolveLocalTool();
    C2paToolInvoker invoker = new C2paToolInvoker(tool.getAbsolutePath());

    File validImage = resolveResource("ai_dawg_valid.png");

    // Copy to temp file
    Path tamperedPath = tmp.resolve("ai_dawg_invalid.png");
    Files.copy(validImage.toPath(), tamperedPath, StandardCopyOption.REPLACE_EXISTING);

    // Flip a byte in the pixel data region
    byte[] data = Files.readAllBytes(tamperedPath);
    int idx = data.length / 2;
    data[idx] ^= 0x01;
    Files.write(tamperedPath, data);

    C2paMetadata meta = invoker.extractMetadata(tamperedPath.toFile());

    assertNotNull(meta);

    // Still should detect that a manifest exists
    assertEquals(1, meta.getC2pa_hasManifest(),
        "Tampered version should still appear to have a manifest");

    assertTrue(meta.getC2pa_manifestCount() >= 1,
        "Manifest count should be unchanged");

    // Soft-success case: no CLI error, so errorFlag should still be 0
    assertEquals(0, meta.getC2pa_errorFlag(),
        "Logical invalidity is not yet surfaced as an ML 'error'");

    // AI-origin should still be detected
    assertNotNull(meta.getC2pa_claimGenerator());
    assertEquals(1, meta.getC2pa_claimGeneratorIsAI(),
        "Tampered copy should still show AI claim generator");
  }

  @Test
  void noManifestImage_returnsSoftSuccess_noError() {

    File tool = resolveLocalTool();
    C2paToolInvoker invoker = new C2paToolInvoker(tool.getAbsolutePath());

    File noManifest = resolveResource("real_no_manifest.HEIC");

    C2paMetadata meta = invoker.extractMetadata(noManifest);

    assertNotNull(meta);

    // No C2PA:
    assertEquals(0, meta.getC2pa_hasManifest());
    assertEquals(0, meta.getC2pa_manifestCount());

    // No errors (soft success)
    assertEquals(0, meta.getC2pa_errorFlag());
    assertNull(meta.getC2pa_errorMessage());
  }
}
