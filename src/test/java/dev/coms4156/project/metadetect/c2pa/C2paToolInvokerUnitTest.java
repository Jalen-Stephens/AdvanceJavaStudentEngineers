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
 * Unit tests for {@link C2paToolInvoker}.
 * The tests that depend on the native C2PA CLI are guarded with `assumeTrue`,
 * so they are silently skipped when running in CI or in environments where the
 * binary is not available. This avoids false negatives without disabling them.
 */
class C2paToolInvokerUnitTest {

  private static final String C2PA_TOOL_PATH = "./tools/c2patool/c2patool";
  private static final String TEST_RESOURCES_DIR = "src/test/resources/mock-images/";

  private C2paToolInvoker c2paToolInvoker;

  @BeforeEach
  void setUp() {
    c2paToolInvoker = new C2paToolInvoker(C2PA_TOOL_PATH);
  }

  /**
   * Happy-path test: requires a working tool and a real sample image.
   */
  @Test
  void testExtractManifestSuccess() throws IOException {
    assumeTrue(Files.exists(Path.of(C2PA_TOOL_PATH)),
        "Skipping: c2patool not installed");

    File file = new File(TEST_RESOURCES_DIR + "Spaghetti.png");
    assumeTrue(file.exists(),
        "Missing test image fixture: " + file.getPath());

    String manifest = c2paToolInvoker.extractManifest(file);

    assertNotNull(manifest, "Manifest should not be null");
    assertFalse(manifest.isEmpty(), "Manifest should not be empty");
  }

  /**
   * Running the tool on a non-existent file should throw IOException.
   */
  @Test
  void testExtractManifestFileNotFound() {
    assumeTrue(Files.exists(Path.of(C2PA_TOOL_PATH)),
        "Skipping: c2patool not installed");

    File nonExistentFile = new File("non_existent_image.jpg");

    IOException ex = assertThrows(IOException.class,
        () -> c2paToolInvoker.extractManifest(nonExistentFile));

    assertNotNull(ex.getMessage());
    assertTrue(!ex.getMessage().isBlank(),
        "IOException should carry a diagnostic message");
  }

  /**
   * Passing a real but invalid file should surface a failure from the C2PA tool.
   */
  @Test
  void testExtractManifestInvalidFile() throws IOException {
    assumeTrue(Files.exists(Path.of(C2PA_TOOL_PATH)),
        "Skipping: c2patool not installed");

    File tempInvalidFile = createTempInvalidFile();

    IOException ex = assertThrows(IOException.class,
        () -> c2paToolInvoker.extractManifest(tempInvalidFile));

    assertNotNull(ex.getMessage());
    assertTrue(
        ex.getMessage().contains("C2PA tool failed")
        || !ex.getMessage().isBlank(),
        "C2PA failure should propagate usable diagnostic text"
    );

    tempInvalidFile.delete();
  }

  /**
   * If the binary path is bogus, we expect an immediate IOException
   * rather than a "tool returned error" manifest.
   */
  @Test
  void testExtractManifestToolNotFound() {
    Path bogusTool = Path.of("./tools/c2patool/definitely-not-installed");
    assumeTrue(!Files.exists(bogusTool),
        "Skipping: bogus tool path unexpectedly exists");

    C2paToolInvoker invoker = new C2paToolInvoker(bogusTool.toString());

    File sample = new File(TEST_RESOURCES_DIR + "Spaghetti.png");
    assumeTrue(sample.exists(),
        "Missing test image fixture: " + sample.getPath());

    IOException ex = assertThrows(IOException.class,
        () -> invoker.extractManifest(sample));

    assertNotNull(ex.getMessage());
    assertTrue(!ex.getMessage().isBlank(),
        "IOException should carry a diagnostic message");
  }

  // ---- helpers ----

  /**
   * Creates a temporary invalid "image" for negative-path testing.
   */
  private static File createTempInvalidFile() throws IOException {
    File f = File.createTempFile("invalid-", ".img");
    try (FileWriter fw = new FileWriter(f)) {
      fw.write("this is not a valid image file content");
    }
    return f;
  }
}
