package dev.coms4156.project.metadetect.dto;

import java.time.Instant;
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
   * Response returned after submitting an analysis job.
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
   * Confidence score and processing status for an analysis job.
   */
  public record ConfidenceResponse(String id, Double confidence, String status) {
  }

  /**
   * Request body for comparing two images (by ID).
   */
  public record CompareRequest(String imageIdA, String imageIdB) {
  }

  /**
   * Response containing similarity score between two images.
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
      String ownerUserId,
      Instant uploadedAt,
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
