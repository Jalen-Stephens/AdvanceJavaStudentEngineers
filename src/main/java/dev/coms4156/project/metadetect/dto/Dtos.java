package dev.coms4156.project.metadetect.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Central DTO (Data Transfer Object) definitions for MetaDetect.
 * These records define the request and response shapes used by the controllers.
 * Keeping them in a single file is fine for a small project, and makes it easier
 * to review and modify the API contracts.
 */
public final class Dtos {
  private Dtos() {
    // Prevent instantiation
  }

  /* -------------------------------------------------------------------------- */
  /* ANALYSIS ENDPOINT DTOs                                                     */
  /* -------------------------------------------------------------------------- */

  /**
   * Optional flags for controlling analysis modules.
   */
  public record AnalyzeOptions(
      Boolean runMetadata,
      Boolean runPrnu,
      Boolean runGan,
      Boolean runCompression) {
  }

  /**
   * Response returned immediately after submitting an analysis for an existing image.
   * Matches POST /api/analyze/{imageId} -> 202 Accepted.
   */
  public record AnalyzeStartResponse(String analysisId) { }

  /**
   * Pollable view of an analysis job.
   * Matches GET /api/analyze/{analysisId}.
   */
  public record AnalysisStatusResponse(
      String analysisId,
      String imageId,
      String status,            // PENDING | COMPLETED | FAILED
      Instant createdAt,
      Instant completedAt,      // may be null until terminal state
      String errorMessage       // present when status == FAILED
  ) { }

  /**
   * Manifest response for an analysis.
   * Matches GET /api/analyze/{analysisId}/manifest.
   * The service stores raw JSON; we surface it as a String to avoid lossy parsing.
   */
  public record AnalysisManifestResponse(
      String analysisId,
      String manifestJson
  ) { }

  /**
   * Confidence/status view (stubbed score for now, but shaped for future ML).
   * Can back GET /api/analyze/{analysisId} or a dedicated .../confidence route.
   */
  public record AnalyzeConfidenceResponse(
      String analysisId,
      String status,
      Double score              // nullable until we implement a real scorer
  ) { }

  /**
   * Stub compare response for GET /api/analyze/compare?left=&right=.
   * Adds status/note fields while keeping your older CompareResponse available.
   */
  public record AnalyzeCompareResponse(
      String status,
      Double similarity,        // nullable in the initial stub
      String note
  ) { }

  /**
   * (Legacy/general) Response returned after submitting an analysis job.
   * Retained for backward compatibility with any callers using this shape.
   */
  public record AnalyzeResponse(
      String id,
      Double confidence,
      String status,
      Instant createdAt,
      Map<String, Object> details) {
  }

  /**
   * Metadata (EXIF or embedded data) extracted from an image.
   */
  public record MetadataResponse(String id, Map<String, Object> exifData) {
  }

  /**
   * (Legacy/general) Confidence score and processing status for an analysis job.
   * Kept for callers already using this shape; new endpoints can prefer AnalyzeConfidenceResponse.
   */
  public record ConfidenceResponse(String id, Double confidence, String status) {
  }

  /**
   * Request body for comparing two images (by ID).
   */
  public record CompareRequest(String imageIdA, String imageIdB) {
  }

  /**
   * (Legacy/general) Response containing similarity score between two images.
   * New analysis endpoints can prefer AnalyzeCompareResponse.
   */
  public record CompareResponse(String imageIdA, String imageIdB, Double similarity) {
  }

  /* -------------------------------------------------------------------------- */
  /* IMAGE MANAGEMENT DTOs                                                      */
  /* -------------------------------------------------------------------------- */

  /**
   * Simplified view of an image record.
   */
  public record ImageDto(
      String id,
      String filename,
      String userId,
      OffsetDateTime uploadedAt,
      List<String> labels,
      String note
  ) {}

  /**
   * Request body for updating image metadata (labels, note).
   */
  public record UpdateImageRequest(String note, List<String> labels) {
  }

  /* -------------------------------------------------------------------------- */
  /* AUTHENTICATION DTOs                                                        */
  /* -------------------------------------------------------------------------- */

  /**
   * Request body for registering a new user.
   */
  public record RegisterRequest(String email, String password) {
  }

  /**
   * Request body for logging in.
   */
  public record LoginRequest(String email, String password) {
  }

  /**
   * Response returned after successful login or registration.
   */
  public record AuthResponse(String userId, String token) {
  }

  /**
   * Request body for refreshing token.
   */
  public record RefreshRequest(String refreshToken) {}
}
