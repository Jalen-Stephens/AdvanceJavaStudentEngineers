package dev.coms4156.project.metadetect.service;

import static dev.coms4156.project.metadetect.model.AnalysisReport.ReportStatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.coms4156.project.metadetect.c2pa.C2paToolInvoker;
import dev.coms4156.project.metadetect.dto.Dtos;
import dev.coms4156.project.metadetect.model.AnalysisReport;
import dev.coms4156.project.metadetect.model.AnalysisReport.ReportStatus;
import dev.coms4156.project.metadetect.model.Image;
import dev.coms4156.project.metadetect.repository.AnalysisReportRepository;
import dev.coms4156.project.metadetect.service.LogisticRegressionService.InferenceResult;
import dev.coms4156.project.metadetect.service.errors.MissingStoragePathException;
import dev.coms4156.project.metadetect.service.errors.NotFoundException;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Orchestrates the analysis pipeline for an uploaded image.
 * Responsibilities:
 * - Enforce ownership via ImageService and current user context.
 * - Persist lifecycle: PENDING → COMPLETED/FAILED in AnalysisReport.
 * - Download object from Supabase Storage via a signed URL.
 * - Invoke C2PA tool to extract manifest JSON.
 * - Return analysisId to support client polling.
 * Notes:
 * - Extraction is synchronous for Iteration 1. Can be moved to async later.
 * - Error details are stored as JSON in `details` to aid troubleshooting.
 */
@Service
public class AnalyzeService {

  private final C2paToolInvoker c2paToolInvoker;
  private final ImageService imageService;
  private final AnalysisReportRepository analysisRepo;
  private final SupabaseStorageService storage;
  private final UserService userService;
  private final LogisticRegressionService logisticRegressionService;
  private final Clock clock;

  // Lightweight mapper for error JSON assembly.
  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Constructs the orchestration service.
   *
   * @param c2paToolInvoker invokes the C2PA CLI to read embedded manifests
   * @param imageService resolves image ownership and metadata
   * @param analysisRepo repository for AnalysisReport rows
   * @param storage signed URL creation and object retrieval
   * @param userService current user identity + bearer token provider
   * @param clock deterministic time source (eases testing)
   */
  public AnalyzeService(C2paToolInvoker c2paToolInvoker,
                        ImageService imageService,
                        AnalysisReportRepository analysisRepo,
                        SupabaseStorageService storage,
                        UserService userService,
                        LogisticRegressionService logisticRegressionService,
                        Clock clock) {
    this.c2paToolInvoker = c2paToolInvoker;
    this.imageService = imageService;
    this.analysisRepo = analysisRepo;
    this.storage = storage;
    this.userService = userService;
    this.logisticRegressionService = logisticRegressionService;
    this.clock = clock;
  }

  /**
   * Starts analysis for the given image.
   * Flow:
   * 1) Validate ownership and storage path.
   * 2) Insert PENDING row and flush.
   * 3) Download object and run C2PA.
   * 4) Mark COMPLETED or FAILED with details.
   *
   * @param imageId image to analyze
   * @return AnalyzeStartResponse containing the analysisId
   * @throws MissingStoragePathException when storage_path is empty
   * @implNote Synchronous in Iteration 1; consider job queue later.
   */
  public Dtos.AnalyzeStartResponse submitAnalysis(UUID imageId) {
    final UUID currentUser = userService.getCurrentUserIdOrThrow();

    // 1) Ownership gate (RLS-friendly through ImageService)
    Image img = imageService.getById(currentUser, imageId);

    // 2) Require a non-null storage path
    String storagePath = img.getStoragePath();
    if (!StringUtils.hasText(storagePath)) {
      throw new MissingStoragePathException(
        "Image has no storage_path; cannot analyze."
      );
    }

    // 3) Create PENDING row with deterministic timestamp
    AnalysisReport pending = new AnalysisReport(imageId);
    pending.setStatus(ReportStatus.PENDING);
    pending.setCreatedAt(now());
    pending = analysisRepo.save(pending);
    final UUID analysisId = pending.getId();

    // 4) Run extraction inline (can be moved to async later)
    runExtractionAndFinalize(analysisId, storagePath);

    // 5) Return analysisId for polling
    return new Dtos.AnalyzeStartResponse(analysisId.toString());
  }

  /**
   * Returns the stored manifest JSON for an analysis.
   * Re-validates ownership via the linked image.
   *
   * @param analysisId target analysis id
   * @return manifest response containing raw JSON string
   * @throws NotFoundException when analysis or manifest is missing
   */
  @Transactional(readOnly = true)
  public Dtos.AnalysisManifestResponse getMetadata(UUID analysisId) {
    var report = analysisRepo.findById(analysisId)
        .orElseThrow(() ->
        new NotFoundException("Analysis not found: " + analysisId));

    // Re-assert ownership via the linked image
    var currentUser = userService.getCurrentUserIdOrThrow();
    imageService.getById(currentUser, report.getImageId());

    String detailsJson = report.getDetails();
    if (!StringUtils.hasText(detailsJson)) {
      throw new NotFoundException(
        "Manifest not available for analysis: " + analysisId
      );
    }
    return new Dtos.AnalysisManifestResponse(
      analysisId.toString(),
      detailsJson
    );
  }

  /**
   * Returns a status/score snapshot compatible with AnalyzeConfidenceResponse.
   *
   * @param analysisId target analysis id
   * @return confidence DTO; score may be null until ML scoring exists
   * @throws NotFoundException when analysis is missing
   */
  @Transactional(readOnly = true)
  public Dtos.AnalyzeConfidenceResponse getConfidence(UUID analysisId) {
    var report = analysisRepo.findById(analysisId)
        .orElseThrow(() ->
        new NotFoundException("Analysis not found: " + analysisId));

    var currentUser = userService.getCurrentUserIdOrThrow();
    imageService.getById(currentUser, report.getImageId());

    return new Dtos.AnalyzeConfidenceResponse(
      report.getId().toString(),
      report.getStatus().name(),
      report.getConfidence(),
      deriveC2paUsed(report.getDetails()),
      logisticRegressionService.getModelVersion()
    );
  }

  /**
   * Validates ownership of both images and returns a placeholder comparison.
   *
   * @param leftImageId left image id
   * @param rightImageId right image id
   * @return stubbed compare response for Iteration 1
   */
  @Transactional(readOnly = true)
  public Dtos.AnalyzeCompareResponse compare(UUID leftImageId, UUID rightImageId) {
    var currentUser = userService.getCurrentUserIdOrThrow();
    imageService.getById(currentUser, leftImageId);
    imageService.getById(currentUser, rightImageId);
    return new Dtos.AnalyzeCompareResponse(
      "DONE",
      null,
      "compare() is a stub in Iteration 1"
    );
  }

  // ---------------------------------------------------------------------------
  // Internal orchestration
  // ---------------------------------------------------------------------------

  /**
   * Downloads the asset, runs C2PA, and finalizes the report.
   * Converts any thrown errors into a FAILED report with error JSON.
   * For C2PA specifically, hard failures are converted to a C2paMetadata
   * object with c2pa_errorFlag = 1, so the analysis flow is not broken.
   */
  private void runExtractionAndFinalize(UUID analysisId, String storagePath) {
    File tempFile = null;
    try {
      // 1) Create a signed URL and download to a temp file
      String bearer = userService.getCurrentBearerOrThrow();
      String signed = storage.createSignedUrl(storagePath, bearer);
      tempFile = downloadToTemp(signed, storagePath);

      // 2) Run C2PA extraction into ML-ready metadata
      C2paToolInvoker.C2paMetadata meta = c2paToolInvoker.extractMetadata(tempFile);

      // 3) Compute logistic-regression score using OpenCV + C2PA features
      InferenceResult inference = logisticRegressionService.predict(
          tempFile.getAbsolutePath(),
          meta
      );

      // 4) Serialize metadata and mark COMPLETED with a confidence score
      String json = objectMapper.writeValueAsString(meta);

      // The details field now stores the C2PA metadata schema, not raw manifest JSON.
      markCompleted(analysisId, json, inference.confidenceScore());

    } catch (IOException ioe) {
      // IO-level failures (download, JSON serialization) are genuine failures.
      handleGenericFailure(analysisId, storagePath, ioe);

    } catch (Exception e) {
      // Non-IO exceptions: treat as failure as before
      handleGenericFailure(analysisId, storagePath, e);

    } finally {
      // Best-effort cleanup of the temp file
      if (tempFile != null && tempFile.exists()) {
        //noinspection ResultOfMethodCallIgnored
        tempFile.delete();
      }
    }
  }

  /**
   * Marks the report as DONE and stores manifest + optional confidence.
   */
  @Transactional
  protected void markCompleted(UUID analysisId,
                               String manifestJson,
                               @Nullable Double confidence) {
    var report = analysisRepo.findById(analysisId)
        .orElseThrow(() ->
        new NotFoundException("Analysis not found: " + analysisId));
    report.setStatus(ReportStatus.DONE);
    report.setDetails(manifestJson);
    report.setConfidence(confidence);
    analysisRepo.save(report);
  }

  /**
   * Marks the report as FAILED and persists error details as JSON.
   */
  @Transactional
  protected void markFailed(UUID analysisId, String detailsJson) {
    var report = analysisRepo.findById(analysisId)
        .orElseThrow(() ->
        new NotFoundException("Analysis not found: " + analysisId));
    report.setStatus(ReportStatus.FAILED);
    // Store {"error":"..."} to meet acceptance signal and aid debugging.
    report.setDetails(detailsJson);
    analysisRepo.save(report);
  }

  /**
   * Downloads a signed URL to a temp file with a reasonable extension.
   * The extension is derived from storagePath to help downstream tools
   * that branch on file type (e.g., .jpg vs .png).
   *
   * @param signedUrl pre-signed download URL
   * @param storagePath object path used to infer extension
   * @return on-disk temp file containing the asset
   * @throws IOException if download fails or produces an empty file
   */
  private File downloadToTemp(String signedUrl, String storagePath) throws IOException {
    String ext = ".bin";
    int slash = storagePath.lastIndexOf('/');
    int dot = storagePath.lastIndexOf('.');
    if (dot > slash && dot >= 0 && dot < storagePath.length() - 1) {
      String candidate = storagePath.substring(dot);
      if (candidate.length() <= 10 && candidate.matches("\\.[A-Za-z0-9]+")) {
        ext = candidate;
      }
    }

    File tmp = File.createTempFile("analysis-", ext);
    try (var in = new URL(signedUrl).openStream()) {
      Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    // Sanity check: ensure we did not fetch an empty object
    if (Files.size(tmp.toPath()) <= 0) {
      throw new IOException("Downloaded empty file from signed URL");
    }
    return tmp;
  }

  /** Returns a clock-based Instant for deterministic tests. */
  private Instant now() {
    return Instant.now(clock);
  }

  private boolean deriveC2paUsed(String detailsJson) {
    if (!StringUtils.hasText(detailsJson)) {
      return false;
    }
    try {
      JsonNode node = objectMapper.readTree(detailsJson);
      int hasManifest = node.path("c2paHasManifest").asInt(0);
      int errorFlag = node.path("c2paErrorFlag").asInt(0);
      return hasManifest == 1 && errorFlag == 0;
    } catch (Exception e) {
      // If parsing fails, default to false so the field is conservative.
      return false;
    }
  }

  /** Truncates a string to a maximum length, null-safe. */
  private static String truncate(String s, int max) {
    if (s == null) {
      return null;
    }
    return s.length() <= max ? s : s.substring(0, Math.max(0, max));
  }

  /** Escapes minimal JSON control characters for a string value. */
  private static String escapeForJson(String s) {
    if (s == null) {
      return null;
    }
    return s
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\r", "\\r")
      .replace("\n", "\\n")
      .replace("\t", "\\t");
  }

  /**
   * Minimal builder for future centralization of AnalysisReport creation.
   * Currently unused; kept to show intended construction pattern.
   */
  @SuppressWarnings("unused")
  private static final class AnalysisReportBuilder {
    static AnalysisReport pending(UUID imageId, Instant createdAt) {
      var ar = new AnalysisReport(imageId);
      ar.setStatus(ReportStatus.PENDING);
      ar.setCreatedAt(createdAt);
      return ar;
    }
  }

  /**
   * Handles a generic failure during analysis by marking the analysis as FAILED
   * and storing the error message in the details field.
   *
   * @param analysisId id of the analysis to mark
   * @param storagePath storage path of the image being analyzed
   * @param e the exception that caused the failure
   */

  private void handleGenericFailure(UUID analysisId, String storagePath, Exception e) {
    String errMsg = truncate(e.toString(), 2000);
  
    try {
      var errorObj = new java.util.LinkedHashMap<String, Object>();
      errorObj.put("error", errMsg);
      // optionally include more context:
      errorObj.put("storagePath", storagePath);
      String errorJson = objectMapper.writeValueAsString(errorObj);
      markFailed(analysisId, errorJson);
    } catch (Exception jsonEx) {
      // absolute fallback (escape dangerous chars)
      markFailed(analysisId, "{\"error\":\"" + escapeForJson(errMsg) + "\"}");
    }
  }
  


}
