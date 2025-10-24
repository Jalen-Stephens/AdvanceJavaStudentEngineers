package dev.coms4156.project.metadetect.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Central DTO (Data Transfer Object) definitions for MetaDetect.
 * These records define the request/response shapes used by controllers and services.
 * Keeping them in a single file is acceptable for project size at Iteration 1 and
 * keeps API contracts easy to audit and evolve.
 */
public final class Dtos {
  private Dtos() {
    // Prevent instantiation of static container class.
  }

  /* -------------------------------------------------------------------------- */
  /* ANALYSIS ENDPOINT DTOs                                                     */
  /* -------------------------------------------------------------------------- */

  /**
   * Optional flags allowing callers to enable/disable specific analysis modules.
   * Future iterations may promote this to a top-level POST payload.
   */
  public record AnalyzeOptions(
      Boolean runMetadata,
      Boolean runPrnu,
      Boolean runGan,
      Boolean runCompression
  ) { }

  /**
   * Returned immediately after an analysis is submitted for an existing image.
   * Matches POST /api/analyze/{imageId} (202 Accepted).
   */
  public record AnalyzeStartResponse(String analysisId) { }

  /**
   * Pollable snapshot of an analysis job status.
   * Matches GET /api/analyze/{analysisId}.
   */
  public record AnalysisStatusResponse(
      String analysisId,
      String imageId,
      String status,          // PENDING | COMPLETED | FAILED
      Instant createdAt,
      Instant completedAt,    // nullable until terminal state
      String errorMessage     // present when status == FAILED
  ) { }

  /**
   * Manifest response for a completed analysis.
   * Raw JSON is returned as a String to avoid lossy re-parsing.
   */
  public record AnalysisManifestResponse(
      String analysisId,
      String manifestJson
  ) { }

  /**
   * Confidence view for an analysis. Currently a stub, but future-compatible with
   * ML scoring. Can be returned by the same route as status or a dedicated route.
   */
  public record AnalyzeConfidenceResponse(
      String analysisId,
      String status,
      Double score              // nullable until we implement a real scorer
  ) { }

  /**
   * Stubbed comparison DTO for GET /api/analyze/compare.
   * More complete ML-based comparison can reuse this shape.
   */
  public record AnalyzeCompareResponse(
      String status,
      Double similarity,        // nullable in the initial stub
      String note
  ) { }

  /**
   * Legacy/general response used by early prototypes.
   * Retained temporarily for compatibility with older callers.
   */
  public record AnalyzeResponse(
      String id,
      Double confidence,
      String status,
      Instant createdAt,
      Map<String, Object> details
  ) { }

  /**
   * Metadata extracted from an image (e.g., EXIF).
   */
  public record MetadataResponse(
      String id,
      Map<String, Object> exifData
  ) { }

  /**
   * Legacy/general confidence DTO from early versions of the pipeline.
   * Kept until all routes migrate to AnalyzeConfidenceResponse.
   */
  public record ConfidenceResponse(
      String id,
      Double confidence,
      String status
  ) { }

  /**
   * Request body for comparing two images by ID.
   */
  public record CompareRequest(String imageIdA, String imageIdB) { }

  /**
   * Legacy/general compare response.
   * Maintained for backward compatibility only.
   */
  public record CompareResponse(
      String imageIdA,
      String imageIdB,
      Double similarity
  ) { }

  /* -------------------------------------------------------------------------- */
  /* IMAGE MANAGEMENT DTOs                                                      */
  /* -------------------------------------------------------------------------- */

  /**
   * Public view of an image. Returned by list/get endpoints.
   */
  public record ImageDto(
      String id,
      String filename,
      String userId,
      OffsetDateTime uploadedAt,
      List<String> labels,
      String note
  ) { }

  /**
   * Request for updating mutable metadata fields on an image.
   */
  public record UpdateImageRequest(
      String note,
      List<String> labels
  ) { }

  /* -------------------------------------------------------------------------- */
  /* AUTHENTICATION DTOs                                                        */
  /* -------------------------------------------------------------------------- */

  /**
   * Request body for registering a new user.
   */
  public record RegisterRequest(String email, String password) { }

  /**
   * Request body for logging in.
   */
  public record LoginRequest(String email, String password) { }

  /**
   * Response returned after a successful login or registration.
   */
  public record AuthResponse(String userId, String token) { }

  /**
   * Request body for obtaining a fresh access token.
   */
  public record RefreshRequest(String refreshToken) { }
}
