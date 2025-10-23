package dev.coms4156.project.metadetect.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.coms4156.project.metadetect.c2pa.C2paToolInvoker;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Lightweight integration test that executes the real c2patool if available.
 * Skips when the tool binary is not present on the developer/CI machine.
 */
class AnalyzeServiceC2paIntegrationTest {

  private static final String TOOL_PATH = "./tools/c2patool/c2patool";

  @Test
  void extractManifest_withRealTool_whenPresent() throws Exception {
    // Skip if tool is not present
    assumeTrue(Files.exists(Path.of(TOOL_PATH)), "Skipping: c2patool not installed");

    C2paToolInvoker invoker = new C2paToolInvoker(TOOL_PATH);
    File file = new File("src/test/resources/mock-images/Spaghetti.png");

    String manifestJson = invoker.extractManifest(file);
    assertNotNull(manifestJson, "C2PA manifest should not be null");
    // System.out.println("C2PA manifest: " + manifestJson);
  }
}
