package dev.coms4156.project.metadetect.controller;

import dev.coms4156.project.metadetect.dto.Dtos;
import dev.coms4156.project.metadetect.service.AnalyzeService;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.io.InputStream;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Endpoints for analysis, metadata, confidence, compare.
 */
@RestController
@RequestMapping("/api")
public class AnalyzeController {

  private final AnalyzeService analyzeService;

  public AnalyzeController(AnalyzeService analyzeService) {
    this.analyzeService = analyzeService;
  }
  /**
   * Handles submission of a new image for analysis.
   * This endpoint accepts a multipart/form-data request containing an image file
   * and optional analysis options. It validates and queues the image for
   * processing, returning an {@link Dtos.AnalyzeResponse} with the job ID and
   * current status.
   *
   * @param image   the uploaded image file to be analyzed (required)
   * @param options optional parameters controlling which analysis modules to run
   * @return a {@link ResponseEntity} containing the analysis job ID, confidence
   *         placeholder, and initial status
   */

  @PostMapping(value = "/analyze", consumes = {"multipart/form-data"})
  public ResponseEntity<Dtos.AnalyzeResponse> analyze(
      @RequestPart("image") MultipartFile image,
      @RequestPart(value = "options", required = false) Dtos.AnalyzeOptions options) {
    // TODO: validate mime/size; persist; enqueue/compute; return id + status
    Dtos.AnalyzeResponse stub =
        new Dtos.AnalyzeResponse("stub-id", 0.42, "PENDING", Instant.now(), null);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(stub);
  }

  @PostMapping(value = "/extract", consumes = "multipart/form-data")
  public ResponseEntity<String> extract(@RequestParam("file") MultipartFile file) {
      try {
          // Extract the C2PA manifest
          //InputStream in = file.getInputStream();
          //String manifestJson = analyzeService.fetchC2pa(in);
  
          String manifestJson = analyzeService.fetchC2pa(new java.io.File("tempfile")); //Temp to try just using a file

          // Return the JSON response
          return ResponseEntity.ok(manifestJson);
      } catch (IOException e) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to extract C2PA manifest", e);
      }
  }



  @GetMapping("/metadata/{id}")
  public ResponseEntity<Dtos.MetadataResponse> metadata(@PathVariable String id) {
    // TODO: fetch parsed EXIF/metadata from DB
    return ResponseEntity.ok(new Dtos.MetadataResponse(id, Map.of()));
  }

  @GetMapping("/confidence/{id}")
  public ResponseEntity<Dtos.ConfidenceResponse> confidence(@PathVariable String id) {
    // TODO: fetch score + status from DB
    return ResponseEntity.ok(new Dtos.ConfidenceResponse(id, 0.42, "PENDING"));
  }

  @PostMapping(value = "/compare", consumes = {"application/json", "multipart/form-data"})
  public ResponseEntity<Dtos.CompareResponse> compare(
      @RequestBody(required = false) Dtos.CompareRequest byIds,
      @RequestPart(value = "imageA", required = false) MultipartFile imageA,
      @RequestPart(value = "imageB", required = false) MultipartFile imageB) {
    // TODO: support id mode and file mode; compute similarity
    return ResponseEntity.ok(new Dtos.CompareResponse("a", "b", 0.13));
  }
}
