package dev.coms4156.project.metadetect.controller;

import dev.coms4156.project.metadetect.dto.Dtos;
import dev.coms4156.project.metadetect.service.AnalyzeService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


/**
 * REST controller for image analysis operations.
 * Endpoints:
 *  - POST   /api/analyze/{imageId}               -> start an analysis (202 Accepted)
 *  - GET    /api/analyze/{analysisId}            -> status/score (polling)
 *  - GET    /api/analyze/{analysisId}/manifest   -> manifest JSON
 *  - GET    /api/analyze/compare                 -> stubbed compare (left & right image IDs)
 * Notes:
 *  - Ownership and RLS checks are enforced in AnalyzeService/ImageService.
 *  - Exceptions (Forbidden/NotFound/etc.)
 *    are expected to be mapped by global @RestControllerAdvice.
 */
@RestController
@RequestMapping("/api/analyze")
public class AnalyzeController {

  private final AnalyzeService analyzeService;

  public AnalyzeController(AnalyzeService analyzeService) {
    this.analyzeService = analyzeService;
  }

  /**
   * Starts analysis for an existing image that is already uploaded to Supabase Storage.
   * Returns 202 with a body containing the new analysisId.
   */
  @PostMapping("/{imageId}")
  public ResponseEntity<Dtos.AnalyzeStartResponse> submit(@PathVariable UUID imageId) {
    Dtos.AnalyzeStartResponse resp = analyzeService.submitAnalysis(imageId);
    // As per ticket: 202 Accepted with { analysisId }
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(resp);
  }

  /**
   * Returns current status (PENDING/COMPLETED/FAILED) and an optional score (stubbed).
   * Suitable for client-side polling.
   */
  @GetMapping("/{analysisId}")
  public ResponseEntity<Dtos.AnalyzeConfidenceResponse> getStatus(@PathVariable UUID analysisId) {
    Dtos.AnalyzeConfidenceResponse resp = analyzeService.getConfidence(analysisId);
    return ResponseEntity.ok(resp);
  }

  /**
   * Returns the stored C2PA manifest JSON for a completed analysis.
   */
  @GetMapping("/{analysisId}/manifest")
  public ResponseEntity<Dtos.AnalysisManifestResponse> getManifest(@PathVariable UUID analysisId) {
    Dtos.AnalysisManifestResponse resp = analyzeService.getMetadata(analysisId);
    return ResponseEntity.ok(resp);
  }

  /**
   * Stubbed comparison endpoint (Iteration 1).
   * Ownership of both images is validated by the service layer.
   * Example: /api/analyze/compare?left={imageId}&right={imageId}
   */
  @GetMapping("/compare")
  public ResponseEntity<Dtos.AnalyzeCompareResponse> compare(
      @RequestParam("left") UUID leftImageId,
      @RequestParam("right") UUID rightImageId) {

    Dtos.AnalyzeCompareResponse resp = analyzeService.compare(leftImageId, rightImageId);
    return ResponseEntity.ok(resp);
  }
}
