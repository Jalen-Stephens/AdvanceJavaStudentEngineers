package dev.coms4156.project.metadetect.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.coms4156.project.metadetect.c2pa.C2paToolInvoker;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Integration-style test that runs the real `c2patool` when present.
 * This validates that our wrapper (C2paToolInvoker) can successfully
 * extract the manifest using a real external binary. If the tool is
 * missing (typical in CI), the test is skipped rather than failing.
 * This test intentionally avoids mocking to confirm real CLI behavior.
 */
class AnalyzeServiceC2paIntegrationTest {

  /** Filesystem location where the test runner expects the c2patool binary. */
  private static final String TOOL_PATH = "./tools/c2patool/c2patool";

  /**
   * Runs the manifest extraction end-to-end if the tool is installed. This
   * verifies correct invocation, wiring, and non-empty output. If the tool
   * is not installed, the test is skipped to preserve CI stability.
   */
  @Test
  void extractManifest_withRealTool_whenPresent() throws Exception {
    // Skip if binary is absent. Using assumeTrue avoids test failure.
    assumeTrue(
        Files.exists(Path.of(TOOL_PATH)),
        "Skipping: c2patool not installed"
    );

    C2paToolInvoker invoker = new C2paToolInvoker(TOOL_PATH);

    // Sample image used to exercise real command flow
    File file = new File("src/test/resources/mock-images/Spaghetti.png");

    // If non-null, extraction succeeded
    String manifestJson = invoker.extractManifest(file);
    assertNotNull(manifestJson, "C2PA manifest should not be null");
  }
}
