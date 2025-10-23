package dev.coms4156.project.metadetect.c2pa;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for C2paToolInvoker. Ensures correct invocation of the C2PA command-line tool.
 */
class C2paToolInvokerUnitTest {

  private static final String C2PA_TOOL_PATH = "./tools/c2patool/c2patool";
  private C2paToolInvoker c2paToolInvoker;
  private static final String TEST_RESOURCES_DIR = "src/test/resources/mock-images/";

  @BeforeEach
  void setUp() {
    c2paToolInvoker = new C2paToolInvoker(C2PA_TOOL_PATH);
  }

  @Test
  void testExtractManifestSuccess() throws IOException {
    // Arrange: Create a temporary image file
    File file = new File(TEST_RESOURCES_DIR + "Spaghetti.PNG");

    // Act: Invoke the C2PA tool
    String manifest = c2paToolInvoker.extractManifest(file);

    // Assert: Verify the manifest is not null or empty
    assertNotNull(manifest);
    assertFalse(manifest.isEmpty());
  }

  @Test
  void testExtractManifestFileNotFound() {
    // Arrange: Use a non-existent file
    File nonExistentFile = new File("non_existent_image.jpg");

    // Act & Assert: Expect an IOException
    IOException exception = assertThrows(IOException.class, () -> 
                        c2paToolInvoker.extractManifest(nonExistentFile));
    System.out.println("Exception message: " + exception.getMessage());
    assertTrue(exception.getMessage().contains("C2PA tool failed with exit code 1"));
  }

  @Test
  void testExtractManifestInvalidFile() throws IOException {
    // Arrange: Create a temporary invalid file
    File tempInvalidFile = createTempInvalidFile();

    // Act & Assert: Expect an IOException due to invalid file
    IOException exception = assertThrows(IOException.class, 
                        () -> c2paToolInvoker.extractManifest(tempInvalidFile));
    assertTrue(exception.getMessage().contains("C2PA tool failed"));

    // Clean up
    tempInvalidFile.delete();
  }

  @Test
  void testExtractManifestToolNotFound() {
    // Arrange: Use an invalid tool path
    C2paToolInvoker invalidInvoker = new C2paToolInvoker("./invalid/path/to/c2patool");

    // Act & Assert: Expect an IOException
    IOException exception = assertThrows(IOException.class, () -> 
                                invalidInvoker.extractManifest(new File("some_image.jpg")));
    System.out.println("Exception message: " + exception.getMessage());
    assertTrue(exception.getMessage().contains(
        "Cannot run program \"./invalid/path/to/c2patool\": Exec failed, error: 2 "
        + "(No such file or directory)"));
  }

  // Helper method to create a temporary invalid file
  private File createTempInvalidFile() throws IOException {
    File tempFile = Files.createTempFile("invalid_file", ".txt").toFile();
    try (FileWriter writer = new FileWriter(tempFile)) {
      writer.write("This is not a valid image file.");
    }
    return tempFile;
  }
}