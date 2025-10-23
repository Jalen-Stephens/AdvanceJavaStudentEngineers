package dev.coms4156.project.metadetect.c2pa;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;


/** Invokes the C2PA command-line tool to extract manifests from images.*/
public class C2paToolInvoker {
    
  private final String c2paToolPath;

  public C2paToolInvoker(String c2paToolPath) {
    this.c2paToolPath = c2paToolPath;
  }
   
  /**
   *  Executes the C2PA tool to extract the manifest from the given image file.
  *
  * @param imageFile The image file to analyze.
  * @return JSON string of the C2PA manifest.
  * @throws IOException if the tool fails or the file is invalid.
  *
  */
  public String extractManifest(File imageFile) throws IOException {
    // Create a temporary file for the output

    

    // Command to invoke the C2PA tool
    ProcessBuilder processBuilder = new ProcessBuilder(
        c2paToolPath,
        imageFile.getAbsolutePath(),
        "-d"
    );

    // Start the process
    Process process = processBuilder.start();
    String output;
    String returnString;
    try {
      // Wait for the process to complete
      int exitCode = process.waitFor();
      if (exitCode != 0) {
        throw new IOException("C2PA tool failed with exit code " + exitCode);
      }
      
      // Read the output file and return its contents as a string
      try (InputStream is = process.getInputStream();
           Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name())) {
        output = scanner.useDelimiter("\\A").next();
        
        returnString = output;

      } catch (Exception e) {
        throw new IOException("Failed to read C2PA tool output", e);
      }

    } catch (InterruptedException e) {
      throw new IOException("C2PA tool execution was interrupted", e);
    }
    
    try (FileWriter file = new FileWriter("output.json")) {
      file.write(output);
      file.flush();
      
      return returnString;

    } catch (IOException e) {
      throw new IOException("Failed to write output to file", e);
    }

  }
}
