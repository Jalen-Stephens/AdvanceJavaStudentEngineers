package dev.coms4156.project.metadetect.c2pa;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.File;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;


/** Invokes the C2PA command-line tool to extract manifests from images.*/
public class C2paToolInvoker {
    private C2paToolInvoker () {}

    public static String extractManifest(File imageFile) throws IOException, InterruptedIOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("tools/c2patool/c2patool", "-json", imageFile.getAbsolutePath());
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        byte[] outputBytes;

        try (InputStream is = process.getInputStream()) {
            outputBytes = is.readAllBytes();
            String output = new String(outputBytes, StandardCharsets.UTF_8);
            System.out.println("C2PA Tool Output:\n" + output);

        }  
    
        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new InterruptedIOException("C2PA tool timed out");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new IOException("C2PA tool failed with exit code " + exitCode);
        }

        return new String(outputBytes, StandardCharsets.UTF_8);
    }

}
