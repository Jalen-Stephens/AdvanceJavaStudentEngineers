package dev.coms4156.project.metadetect.service;

// Import Metadata Extractor classes
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.Directory;
import com.drew.metadata.Tag;
import com.drew.metadata.MetadataException;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.coms4156.project.metadetect.c2pa.C2paToolInvoker;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

// Import Java utilities

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Coordinates metadata, forensic, and model steps.
 */
@Service
public class AnalyzeService {
  // TODO: inject repositories + compute engines when added

    private final C2paToolInvoker c2paToolInvoker;

    public AnalyzeService(C2paToolInvoker c2paToolInvoker) {
        this.c2paToolInvoker = c2paToolInvoker;
    }

    public String submitAnalysis(MultipartFile file) {
      // TODO: validate/normalize; persist; enqueue; return id
      return "stub-id";
    }


    /**
     * Extracts the C2PA manifest from an image InputStream.
     *
     * @param in InputStream of the user-submitted image.
     * @return JSON string of the C2PA manifest.
     * @throws IOException if the tool fails or the input is invalid.
     */
    //public String fetchC2pa(InputStream in) throws IOException {
    public String fetchC2pa(File file) throws IOException { //Temp to try just using a file
      // Convert InputStream to a temporary file
      //File tempFile = File.createTempFile("uploaded-", ".png");
      //tempFile.deleteOnExit();

      // Write InputStream to the temporary file
      //try (var outputStream = java.nio.file.Files.newOutputStream(tempFile.toPath())) {
      //    in.transferTo(outputStream);
      //}

      // Invoke the C2PA tool and return the JSON result
      return c2paToolInvoker.extractManifest(file);
  }


  //Fetch confidence score
}
