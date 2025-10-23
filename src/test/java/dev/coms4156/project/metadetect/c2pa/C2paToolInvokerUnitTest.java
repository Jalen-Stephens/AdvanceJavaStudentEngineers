package dev.coms4156.project.metadetect.c2pa;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for C2paToolInvoker. Ensures correct invocation of the C2PA command-line tool.
 * Tests that need the real tool are guarded with assumes to avoid CI flakiness.
 */
class C2paToolInvokerUnitTest {

  private static final String C2PA_TOOL_PATH = "./tools/c2patool/c2patool";
  private static final String TEST_RESOURCES_DIR = "src/test/resources/mock-images/";
  private C2paToolInvoker c2paToolInvoker;

  @BeforeEach
  void setUp() {
    c2paToolInvoker = new C2paToolInvoker(C2PA_TOOL_PATH);
  }

  @Test
  void testExtractManifestSuccess() throws IOException {
    // Requires the real tool and a valid sample image
    assumeTrue(Files.exists(Path.of(C2PA_TOOL_PATH)), "Skipping: c2patool not installed");
    File file = new File(TEST_RESOURCES_DIR + "Spaghetti.png");
    assumeTrue(file.exists(), "Missing test image fixture: " + file.getPath());

    String manifest = c2paToolInvoker.extractManifest(file);

    assertNotNull(manifest, "Manifest should not be null");
    assertFalse(manifest.isEmpty(), "Manifest should not be empty");
  }

  @Test
  void testExtractManifestFileNotFound() {
    // Requires the real tool to execute; otherwise behavior differs
    assumeTrue(Files.exists(Path.of(C2PA_TOOL_PATH)), "Skipping: c2patool not installed");

    File nonExistentFile = new File("non_existent_image.jpg");

    IOException ex = assertThrows(IOException.class,
        () -> c2paToolInvoker.extractManifest(nonExistentFile));

    // Message/content varies by platform; we at least expect an error message
    assertNotNull(ex.getMessage());
    assertTrue(!ex.getMessage().isBlank(), "IOException should carry a diagnostic message");
  }

  @Test
  void testExtractManifestInvalidFile() throws IOException {
    // Requires the real tool to run and fail on invalid input
    assumeTrue(Files.exists(Path.of(C2PA_TOOL_PATH)), "Skipping: c2patool not installed");

    File tempInvalidFile = createTempInvalidFile();

    IOException ex = assertThrows(IOException.class,
        () -> c2paToolInvoker.extractManifest(tempInvalidFile));

    assertNotNull(ex.getMessage());
    assertTrue(ex.getMessage().contains("C2PA tool failed") || !ex.getMessage().isBlank());

    // Clean up
    tempInvalidFile.delete();
  }

  @Test
  void testExtractManifestToolNotFound() throws Exception {
    // Point to a path that should NOT exist. If it does, skip to avoid false negatives.
    Path bogusTool = Path.of("./tools/c2patool/definitely-not-installed");
    assumeTrue(!Files.exists(bogusTool), "Skipping: bogus tool path unexpectedly exists");

    C2paToolInvoker invoker = new C2paToolInvoker(bogusTool.toString());
    File sample = new File(TEST_RESOURCES_DIR + "Spaghetti.png");
    assumeTrue(sample.exists(), "Missing test image fixture: " + sample.getPath());

    // Behavior we require: trying to run a missing binary throws IOException
    IOException ex = assertThrows(IOException.class, () -> invoker.extractManifest(sample));

    // Don’t assert on platform-specific messages. Just ensure there’s *some* message.
    assertNotNull(ex.getMessage());
    assertTrue(!ex.getMessage().isBlank(), "IOException should carry a diagnostic message");
  }

  // --------- helpers ---------

  private static File createTempInvalidFile() throws IOException {
    File f = File.createTempFile("invalid-", ".img");
    try (FileWriter fw = new FileWriter(f)) {
      fw.write("this is not a valid image file content");
    }
    return f;
  }
}
