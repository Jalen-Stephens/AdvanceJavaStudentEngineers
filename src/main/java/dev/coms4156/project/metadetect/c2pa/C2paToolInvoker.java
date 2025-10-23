package dev.coms4156.project.metadetect.c2pa;

import java.io.File;
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
    ProcessBuilder pb = new ProcessBuilder(
        c2paToolPath,
        imageFile.getAbsolutePath(),
        "-d" // your tool version expects this
    );
    pb.redirectErrorStream(false);

    Process proc = pb.start();
    try (InputStream out = proc.getInputStream();
         InputStream err = proc.getErrorStream();
         Scanner so = new Scanner(out, StandardCharsets.UTF_8);
         Scanner se =
           new Scanner(err, StandardCharsets.UTF_8)) {

      int exit = proc.waitFor();
      String stdout = so.useDelimiter("\\A").hasNext() ? so.next() : "";
      String stderr = se.useDelimiter("\\A").hasNext() ? se.next() : "";

      if (exit != 0) {
        String msg = "C2PA tool failed with exit code " + exit
            + (stderr.isBlank() ? "" : " | stderr: " + stderr);
        throw new IOException(msg);
      }
      return stdout; // should already be JSON from -d
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw new IOException("C2PA tool execution was interrupted", ie);
    }
  }
}
