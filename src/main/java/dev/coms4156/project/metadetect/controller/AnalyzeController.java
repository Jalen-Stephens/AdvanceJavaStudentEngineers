package dev.coms4156.project.metadetect.controller;

import dev.coms4156.project.metadetect.dto.Dtos;
import dev.coms4156.project.metadetect.service.AnalyzeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@Tag(name = "Analysis", description = "Run AI-authenticity analysis for images")
@SecurityRequirement(name = "bearerAuth")
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
  @Operation(
      summary = "Start analysis for an image",
      description = "Creates a new analysis job for an image previously uploaded and owned by "
          + "the current user. Returns an `analysisId` that can be polled for status."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "202",
          description = "Analysis accepted; body contains `{ analysisId }`"),
      @ApiResponse(responseCode = "403",
          description = "User does not own the image"),
      @ApiResponse(responseCode = "404",
          description = "Image not found")
  })
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
  @Operation(
    summary = "Get analysis status and score",
    description = "Fetches the status and (stubbed) confidence score for a previously "
        + "started analysis job."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200",
          description = "Status returned successfully"),
      @ApiResponse(responseCode = "404",
          description = "Analysis not found")
  })
  @GetMapping("/{analysisId}")
  public ResponseEntity<Dtos.AnalyzeConfidenceResponse> getStatus(@PathVariable UUID analysisId) {
    Dtos.AnalyzeConfidenceResponse resp = analyzeService.getConfidence(analysisId);
    return ResponseEntity.ok(resp);
  }

  /**
   * Returns the stored C2PA manifest JSON for a completed analysis.
   */
  @Operation(
    summary = "Get C2PA manifest for an analysis",
    description = "Retrieves metadata / manifest JSON (e.g., C2PA) stored for a completed "
        + "analysis job."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200",
          description = "Manifest JSON returned successfully"),
      @ApiResponse(responseCode = "404",
          description = "Analysis or manifest not found")
  })
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
  @Operation(
    summary = "Compare two images",
    description = "Compares two images (by ID) owned by the current user. "
        + "The current implementation is stubbed for Iteration 1."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200",
          description = "Comparison result returned"),
      @ApiResponse(responseCode = "403",
          description = "User does not own one or both images"),
      @ApiResponse(responseCode = "404",
          description = "One or both images not found")
  })
  @GetMapping("/compare")
  public ResponseEntity<Dtos.AnalyzeCompareResponse> compare(
      @RequestParam("left") UUID leftImageId,
      @RequestParam("right") UUID rightImageId) {

    Dtos.AnalyzeCompareResponse resp = analyzeService.compare(leftImageId, rightImageId);
    return ResponseEntity.ok(resp);
  }
}
