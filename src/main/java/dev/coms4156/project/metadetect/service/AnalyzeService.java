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
   
    public String fetchC2pa(InputStream in) throws Exception {
      
      return "c2pa-metadata-placeholder";
    }


  //Fetch confidence score
}
