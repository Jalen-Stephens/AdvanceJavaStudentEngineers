package dev.coms4156.project.metadetect.service;

import static dev.coms4156.project.metadetect.model.AnalysisReport.ReportStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.coms4156.project.metadetect.c2pa.C2paToolInvoker;
import dev.coms4156.project.metadetect.dto.Dtos;
import dev.coms4156.project.metadetect.model.AnalysisReport;
import dev.coms4156.project.metadetect.model.Image;
import dev.coms4156.project.metadetect.repository.AnalysisReportRepository;
import dev.coms4156.project.metadetect.service.errors.MissingStoragePathException;
import dev.coms4156.project.metadetect.service.errors.NotFoundException;
import dev.coms4156.project.metadetect.service.SupabaseStorageService;
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
 * Orchestrates the analysis pipeline.
 * - Ownership checks via ImageService
 * - Persist lifecycle: PENDING -> COMPLETED/FAILED
 * - Download from Supabase Storage and invoke C2PA
 * - Returns analysisId for polling
 */
@Service
public class AnalyzeService {

  private final C2paToolInvoker c2paToolInvoker;
  private final ImageService imageService;
  private final AnalysisReportRepository analysisRepo;
  private final SupabaseStorageService storage;
  private final UserService userService;
  private final Clock clock;

  private final ObjectMapper objectMapper = new ObjectMapper();


  /**
   * Constructs the analysis orchestration service. This service coordinates the
   * full analysis pipeline by validating user ownership of the image, persisting
   * analysis lifecycle state (PENDING → COMPLETED/FAILED), retrieving the asset
   * from Supabase Storage, and invoking the C2PA extraction tool.
   *
   * @param c2paToolInvoker    component responsible for invoking the C2PA CLI tool
   *                           to extract embedded provenance/manifest data
   * @param imageService       service used to resolve the target image and enforce
   *                           ownership/RLS checks prior to analysis
   * @param analysisRepo       JPA repository for persisting and updating analysis
   *                           records during their lifecycle
   * @param storage            Supabase-backed storage service used to generate signed
   *                           download URLs and retrieve the image asset for analysis
   * @param userService        provider of the current authenticated user context,
   *                           including user identity and bearer token (for signed download)
   * @param clock              time source used for deterministic timestamps in the
   *                           persisted analysis records (simplifies testing)
   */
  public AnalyzeService(C2paToolInvoker c2paToolInvoker,
                        ImageService imageService,
                        AnalysisReportRepository analysisRepo,
                        SupabaseStorageService storage,
                        UserService userService,
                        Clock clock) {
    this.c2paToolInvoker = c2paToolInvoker;
    this.imageService = imageService;
    this.analysisRepo = analysisRepo;
    this.storage = storage;
    this.userService = userService;
    this.clock = clock;
  }


  /**
   * Starts an analysis for the given image.
   * Creates a PENDING row, then downloads + runs C2PA, and stores the result.
   *
   * @return AnalyzeStartResponse containing the analysisId.
   */
  @Transactional
  public Dtos.AnalyzeStartResponse submitAnalysis(UUID imageId) {
    final UUID currentUser = userService.getCurrentUserIdOrThrow();
    // 1) Ownership gate (RLS-friendly through ImageService)
    Image img = imageService.getById(currentUser, imageId);
    // 2) Require a non-null storage path
    String storagePath = img.getStoragePath();
    if (!StringUtils.hasText(storagePath)) {
      throw new MissingStoragePathException("Image has no storage_path; cannot analyze.");
    }

    // 3) Create PENDING row
    AnalysisReport pending = new AnalysisReport(imageId);
    pending.setStatus(ReportStatus.PENDING);
    pending.setCreatedAt(now());
    pending = analysisRepo.save(pending);
    final UUID analysisId = pending.getId();

    // 4) Ensure row visible before heavy work
    analysisRepo.flush();

    // 5) Run extraction inline (can be moved to async later)
    runExtractionAndFinalize(analysisId, storagePath);

    // 6) Return analysisId for polling
    return new Dtos.AnalyzeStartResponse(analysisId.toString());
  }

  /**
   * Returns the stored manifest JSON for an analysis, enforcing ownership.
   */
  @Transactional(readOnly = true)
  public Dtos.AnalysisManifestResponse getMetadata(UUID analysisId) {
    var report = analysisRepo.findById(analysisId)
        .orElseThrow(() -> new NotFoundException("Analysis not found: " + analysisId));

    // Re-assert ownership via the linked image
    var currentUser = userService.getCurrentUserIdOrThrow();
    imageService.getById(currentUser, report.getImageId());

    String detailsJson = report.getDetails();
    if (!StringUtils.hasText(detailsJson)) {
      throw new NotFoundException("Manifest not available for analysis: " + analysisId);
    }
    return new Dtos.AnalysisManifestResponse(analysisId.toString(), detailsJson);
  }

  /**
   * Returns a status/score stub consistent with Dtos.AnalyzeConfidenceResponse.
   */
  @Transactional(readOnly = true)
  public Dtos.AnalyzeConfidenceResponse getConfidence(UUID analysisId) {
    var report = analysisRepo.findById(analysisId)
        .orElseThrow(() -> new NotFoundException("Analysis not found: " + analysisId));

    var currentUser = userService.getCurrentUserIdOrThrow();
    imageService.getById(currentUser, report.getImageId());

    return new Dtos.AnalyzeConfidenceResponse(
      report.getId().toString(),
      report.getStatus().name(),
      report.getConfidence()   // null until a real scorer exists
    );
  }

  /**
   * Stubbed compare – enforces ownership of both images and returns a placeholder response.
   */
  @Transactional(readOnly = true)
  public Dtos.AnalyzeCompareResponse compare(UUID leftImageId, UUID rightImageId) {
    var currentUser = userService.getCurrentUserIdOrThrow();
    imageService.getById(currentUser, leftImageId);
    imageService.getById(currentUser, rightImageId);
    return new Dtos.AnalyzeCompareResponse("DONE", null, "compare() is a stub in Iteration 1");
  }

  // ======== Internal orchestration ========

  private void runExtractionAndFinalize(UUID analysisId, String storagePath) {
    File tempFile = null;
    try {
      // 1) Create a signed URL + download to a temp file
      String bearer = userService.getCurrentBearerOrThrow();
      String signed = storage.createSignedUrl(storagePath, bearer);
      tempFile = downloadToTemp(signed, storagePath);

      // 2) Run C2PA
      String manifestJson = c2paToolInvoker.extractManifest(tempFile);

      // 3) Mark COMPLETED
      markCompleted(analysisId, manifestJson, /*confidence*/ null);

    } catch (Exception e) {
      String errMsg = truncate(e.toString(), 2000);

      try {
        var errorObj = new java.util.LinkedHashMap<String, Object>();
        errorObj.put("error", errMsg);
        // optionally include more context:
        // errorObj.put("storagePath", storagePath);
        String errorJson = objectMapper.writeValueAsString(errorObj);
        markFailed(analysisId, errorJson);
      } catch (Exception jsonEx) {
        // absolute fallback (escape dangerous chars)
        markFailed(analysisId, "{\"error\":\"" + escapeForJson(errMsg) + "\"}");
      }
    }
  }

  @Transactional
  protected void markCompleted(UUID analysisId, String manifestJson, @Nullable Double confidence) {
    var report = analysisRepo.findById(analysisId)
        .orElseThrow(() -> new NotFoundException("Analysis not found: " + analysisId));
    report.setStatus(ReportStatus.DONE);
    report.setDetails(manifestJson);
    report.setConfidence(confidence);
    analysisRepo.save(report);
  }

  @Transactional
  protected void markFailed(UUID analysisId, String detailsJson) {
    var report = analysisRepo.findById(analysisId)
        .orElseThrow(() -> new NotFoundException("Analysis not found: " + analysisId));
    report.setStatus(ReportStatus.FAILED);
    report.setDetails(detailsJson);   // store {"error":"..."} to meet acceptance signal
    analysisRepo.save(report);
  }

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
    // quick sanity
    if (Files.size(tmp.toPath()) <= 0) {
      throw new IOException("Downloaded empty file from signed URL");
    }
    return tmp;
  }

  private Instant now() {
    return Instant.now(clock);
  }

  private static String truncate(String s, int max) {
    if (s == null) {
      return null;
    }
    return s.length() <= max ? s : s.substring(0, Math.max(0, max));
  }

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


  /** Minimal builder to create a PENDING report if you want to centralize creation logic later. */
  @SuppressWarnings("unused")
  private static final class AnalysisReportBuilder {
    static AnalysisReport pending(UUID imageId, Instant createdAt) {
      var ar = new AnalysisReport(imageId);
      ar.setStatus(ReportStatus.PENDING);
      ar.setCreatedAt(createdAt);
      return ar;
    }
  }
}
