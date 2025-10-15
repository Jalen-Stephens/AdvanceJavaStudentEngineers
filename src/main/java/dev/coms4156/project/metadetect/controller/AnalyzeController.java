package dev.coms4156.project.metadetect.controller;

import dev.coms4156.project.metadetect.dto.Dtos;
import dev.coms4156.project.metadetect.dto.Dtos.AnalyzeOptions;
import dev.coms4156.project.metadetect.dto.Dtos.AnalyzeResponse;
import dev.coms4156.project.metadetect.dto.Dtos.MetadataResponse;
import dev.coms4156.project.metadetect.dto.Dtos.ConfidenceResponse;
import dev.coms4156.project.metadetect.dto.Dtos.CompareRequest;
import dev.coms4156.project.metadetect.dto.Dtos.CompareResponse;

import java.time.Instant;
import java.util.Map;

import dev.coms4156.project.metadetect.service.AnalyzeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;

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

  @PostMapping(value = "/analyze", consumes = {"multipart/form-data"})
  public ResponseEntity<Dtos.AnalyzeResponse> analyze(
    @RequestPart("image") MultipartFile image,
    @RequestPart(value = "options", required = false) Dtos.AnalyzeOptions options) {
    // TODO: validate mime/size; persist; enqueue/compute; return id + status
    AnalyzeResponse stub = new AnalyzeResponse("stub-id", 0.42, "PENDING", Instant.now(), null);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(stub);
  }

  @GetMapping("/metadata/{id}")
  public ResponseEntity<Dtos.MetadataResponse> metadata(@PathVariable String id) {
    // TODO: fetch parsed EXIF/metadata from DB
    return ResponseEntity.ok(new MetadataResponse(id, Map.of()));
  }

  @GetMapping("/confidence/{id}")
  public ResponseEntity<Dtos.ConfidenceResponse> confidence(@PathVariable String id) {
    // TODO: fetch score + status from DB
    return ResponseEntity.ok(new ConfidenceResponse(id, 0.42, "PENDING"));
  }

  @PostMapping(value = "/compare", consumes = {"application/json", "multipart/form-data"})
  public ResponseEntity<Dtos.CompareResponse> compare(
    @RequestBody(required = false) Dtos.CompareRequest byIds,
    @RequestPart(value = "imageA", required = false) MultipartFile imageA,
    @RequestPart(value = "imageB", required = false) MultipartFile imageB) {
    // TODO: support id mode and file mode; compute similarity
    return ResponseEntity.ok(new CompareResponse("a", "b", 0.13));
  }
}
