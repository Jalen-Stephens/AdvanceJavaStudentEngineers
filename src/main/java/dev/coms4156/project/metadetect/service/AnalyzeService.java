package dev.coms4156.project.metadetect.service;

// Import Metadata Extractor classes
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.Directory;
import com.drew.metadata.Tag;
import com.drew.metadata.MetadataException;

import com.fasterxml.jackson.databind.ObjectMapper;


import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

// Import Java utilities

import java.io.File;
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


    public String submitAnalysis(MultipartFile file) {
      // TODO: validate/normalize; persist; enqueue; return id
      return "stub-id";
    }


    /**
     * Reads EXIF and other metadata from an image InputStream,
     * converts it into a structured JSON string.
     */
    
    // TODO: methods to fetch metadata, confidence, compare, etc.
    public String fetchMetadata(InputStream in) throws Exception {
      Metadata metadata = ImageMetadataReader.readMetadata(in);

      // Build JSON-ready map structure
      Map<String, Object> root = new LinkedHashMap<>();
      List<Map<String, Object>> directoriesJson = new ArrayList<>();

      for (Directory dir : metadata.getDirectories()) {
          Map<String, Object> dirJson = new LinkedHashMap<>();
          dirJson.put("name", dir.getName());
          dirJson.put("type", dir.getClass().getName());

          List<Map<String, Object>> tagsJson = new ArrayList<>();
          for (Tag tag : dir.getTags()) {
              Map<String, Object> tagJson = new LinkedHashMap<>();
              tagJson.put("tagType", tag.getTagType());
              tagJson.put("tagName", tag.getTagName());
              tagJson.put("description", tag.getDescription());

              Object raw = dir.getObject(tag.getTagType());
              tagJson.put("rawValue", raw != null ? raw.toString() : tag.getDescription());

              tagsJson.add(tagJson);
          }

          dirJson.put("tags", tagsJson);
          //TODO: .getErrors() method in Directory class
          directoriesJson.add(dirJson);
      }

      root.put("directories", directoriesJson);

      // Convert the map to JSON string using Jackson
      ObjectMapper mapper = new ObjectMapper();
      return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }

    
    //Fetch metadata
    



  //Fetch confidence score
}
