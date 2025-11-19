package dev.coms4156.project.metadetect.controller;

import dev.coms4156.project.metadetect.dto.Dtos;
import dev.coms4156.project.metadetect.service.AnalyzeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * HTTP API for starting and querying image analyses.
 * Responsibilities
 * - Accepts requests to start analysis on an already-uploaded image.
 * - Exposes polling endpoints to retrieve analysis status, confidence scores, and stored manifests.
 * - Delegates ownership/RLS checks and business logic to {@link AnalyzeService}.
 * Contract
 * - POST /api/analyze/{imageId} returns 202 Accepted with an analysis identifier.
 * - GET  /api/analyze/{analysisId} returns current status and (optionally) a confidence score.
 * - GET  /api/analyze/{analysisId}/manifest returns a captured C2PA manifest (if available).
 * - GET  /api/analyze/compare?left=...&right=...
 *     returns a lightweight comparison (Iteration 1 stub).
 * Error Handling
 * - Authorization, ownership, and not-found conditions are surfaced as exceptions from the service
 *   layer and mapped by a global {@code @RestControllerAdvice}.
 * Thread-safety
 * - Controller is stateless; relies on Spring-managed, thread-safe collaborators.
 */
@Tag(name = "Analysis", description = "Run AI-authenticity analysis for images")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/analyze")
public class AnalyzeController {

  private final AnalyzeService analyzeService;

  /**
   * Constructs the controller with required collaborators.
   *
   * @param analyzeService domain service coordinating analysis lifecycle and access checks
   */
  public AnalyzeController(AnalyzeService analyzeService) {
    this.analyzeService = analyzeService;
  }

  /**
   * Starts analysis for an existing image that is already persisted
   *     and stored (e.g., in Supabase Storage).
   * The service enqueues or triggers downstream processing and
   *     returns an identifier used for polling.
   * Response semantics
   * - Returns HTTP 202 Accepted to indicate asynchronous processing has begun.
   *
   * @param imageId unique identifier of the previously uploaded image
   * @return 202 Accepted with a body containing
   *        {@link Dtos.AnalyzeStartResponse} and a new analysisId
   * @throws org.springframework.web.server.ResponseStatusException if the image does not exist or
   *         the caller is not authorized to analyze it (propagated from the service layer)
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
    // Delegate to service: performs ownership checks, persists analysis row, and schedules work.
    Dtos.AnalyzeStartResponse resp = analyzeService.submitAnalysis(imageId);

    // Per API contract, asynchronous start returns 202 Accepted rather than 200 OK.
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(resp);
  }

  /**
   * Retrieves the current analysis status and (optionally) a confidence score suitable for polling.
   * Typical states include PENDING, COMPLETED, and FAILED.
   *
   * @param analysisId unique identifier returned by {@link #submit(UUID)}
   * @return 200 OK with {@link Dtos.AnalyzeConfidenceResponse}
   * @throws org.springframework.web.server.ResponseStatusException
   *         if the analysis does not exist or the
   *         caller lacks access (propagated from the service layer)
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
    // Service encapsulates lookup and authorization; controller simply returns the DTO.
    Dtos.AnalyzeConfidenceResponse resp = analyzeService.getConfidence(analysisId);
    return ResponseEntity.ok(resp);
  }

  /**
   * Returns the stored C2PA manifest for a completed analysis, when available.
   * Clients should check analysis status before calling this endpoint
   * to avoid unnecessary requests.
   *
   * @param analysisId unique identifier of the analysis
   * @return 200 OK with {@link Dtos.AnalysisManifestResponse};
   *     may be 404/403 via advice if not accessible
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
    // The service is responsible for ensuring the analysis is complete and manifest exists or
    // raising a mapped exception if it does not.
    Dtos.AnalysisManifestResponse resp = analyzeService.getMetadata(analysisId);
    return ResponseEntity.ok(resp);
  }

  /**
   * Lightweight comparison endpoint (Iteration 1 stub) that
   *  compares two images owned by the caller.
   * The service validates ownership of both resources and returns a basic comparison DTO.
   * Example
   *   GET /api/analyze/compare?left={imageId}&right={imageId}
   *
   * @param leftImageId identifier of the left image to compare
   * @param rightImageId identifier of the right image to compare
   * @return 200 OK with {@link Dtos.AnalyzeCompareResponse}
   * @throws org.springframework.web.server.ResponseStatusException
   *     if either image is missing or unauthorized
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

    // Delegate comparison to the domain service; controller remains a thin transport layer.
    Dtos.AnalyzeCompareResponse resp = analyzeService.compare(leftImageId, rightImageId);
    return ResponseEntity.ok(resp);
  }
}
