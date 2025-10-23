package dev.coms4156.project.metadetect.controller;

import dev.coms4156.project.metadetect.dto.Dtos;
import dev.coms4156.project.metadetect.service.AnalyzeService;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
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


  //  /**
  //   * Extracts the C2PA manifest from an uploaded image file.
  //   * This endpoint accepts a multipart/form-data request containing an image file.
  //   * It processes the file using the C2PA tool to extract the manifest metadata.
  //   * The extracted manifest is returned as a JSON string.
  //   *
  //   * @param file the uploaded image file (required).
  //   * @return a {@link ResponseEntity} containing the extracted C2PA manifest as a JSON string.
  //   * @throws ResponseStatusException
  //   * if the extraction fails due to an I/O error or invalid input.
  //   */
  //  @PostMapping(value = "/extract", consumes = "multipart/form-data")
  //  public ResponseEntity<String> extract(@RequestParam("file") MultipartFile file) {
  //    try {
  //      // Extract the C2PA manifest
  //      //InputStream in = file.getInputStream();
  //      //String manifestJson = analyzeService.fetchC2pa(in);
  //
  //      // String manifestJson = analyzeService.fetchC2pa(new java.io.File("tempfile"));
  //
  //      // Return the JSON response
  //      return ResponseEntity.ok(manifestJson);
  //    } catch (IOException e) {
  //      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
  //                                        "Failed to extract C2PA manifest", e);
  //    }
  //  }

  /**
   * Retrieves metadata for a specific image analysis job. (TODO: IMPLEMENT/DELETE)
   * This endpoint fetches parsed EXIF and other metadata associated with the given job ID.
   * The metadata is returned as a {@link Dtos.MetadataResponse} object.
   *
   * @param id the unique identifier of the image analysis job (required).
   * @return a {@link ResponseEntity} containing the metadata associated with the job ID.
   */
  @GetMapping("/metadata/{id}")
  public ResponseEntity<Dtos.MetadataResponse> metadata(@PathVariable String id) {
    // TODO: fetch parsed EXIF/metadata from DB
    return ResponseEntity.ok(new Dtos.MetadataResponse(id, Map.of()));
  }

  /**
   * Retrieves the confidence score and status for a specific image analysis job.
   * This endpoint fetches the confidence score and current status associated with the given job ID.
   * The confidence score represents the likelihood of the analysis being correct, and the status
   * indicates the current state of the analysis (e.g., PENDING, COMPLETED).
   *
   * @param id the unique identifier of the image analysis job (required).
   * @return a {@link ResponseEntity} containing the confidence score and status for the job ID.
   */
  @GetMapping("/confidence/{id}")
  public ResponseEntity<Dtos.ConfidenceResponse> confidence(@PathVariable String id) {
    // TODO: fetch score + status from DB
    return ResponseEntity.ok(new Dtos.ConfidenceResponse(id, 0.42, "PENDING"));
  }

  /**
   * Compares two images or analysis results to compute their similarity.
   * This endpoint accepts either image files or analysis job IDs to compare the similarity between
   * two images. It supports both file-based comparison and ID-based comparison modes. 
   * The similarity score is returned as a percentage value.
   *
   * @param byIds an optional {@link Dtos.CompareRequest} containing job IDs for comparison.
   * @param imageA an optional {@link MultipartFile} representing the first image file to compare.
   * @param imageB an optional {@link MultipartFile} representing the second image file to compare.
   * @return a {@link ResponseEntity} containing the similarity score and details of the comparison.
   */
  @PostMapping(value = "/compare", consumes = {"application/json",
      "multipart/form-data"})
  public ResponseEntity<Dtos.CompareResponse> compare(
      @RequestBody(required = false) Dtos.CompareRequest byIds,
      @RequestPart(value = "imageA", required = false) MultipartFile imageA,
      @RequestPart(value = "imageB", required = false) MultipartFile imageB) {
    // TODO: support id mode and file mode; compute similarity
    return ResponseEntity.ok(new Dtos.CompareResponse("a", "b", 0.13));
  }
}
