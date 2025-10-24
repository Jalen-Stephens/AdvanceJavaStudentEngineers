package dev.coms4156.project.metadetect.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.coms4156.project.metadetect.c2pa.C2paToolInvoker;
import dev.coms4156.project.metadetect.dto.Dtos;
import dev.coms4156.project.metadetect.model.AnalysisReport;
import dev.coms4156.project.metadetect.model.Image;
import dev.coms4156.project.metadetect.repository.AnalysisReportRepository;
import dev.coms4156.project.metadetect.service.SupabaseStorageService;
import dev.coms4156.project.metadetect.service.errors.ForbiddenException;
import dev.coms4156.project.metadetect.service.errors.MissingStoragePathException;
import dev.coms4156.project.metadetect.service.errors.NotFoundException;
import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link AnalyzeService}.
 * Strategy:
 * - Mock external collaborators (C2PA tool, storage, repo, user, image svc).
 * - Exercise both happy-path and error paths for analysis lifecycle.
 * - Use fixed Clock for deterministic timestamps.
 * - Verify ownership checks, error propagation, persistence state, and DTO shape.
 */
class AnalyzeServiceTest {

  private C2paToolInvoker c2pa;
  private ImageService imageService;
  private AnalysisReportRepository repo;
  private SupabaseStorageService storage;
  private UserService userService;
  private Clock clock;

  private AnalyzeService service;

  private final UUID userId = UUID.randomUUID();
  private final UUID imageId = UUID.randomUUID();
  private final Instant fixedNow = Instant.parse("2025-01-01T00:00:00Z");

  @BeforeEach
  void setUp() {
    c2pa = mock(C2paToolInvoker.class);
    imageService = mock(ImageService.class);
    repo = mock(AnalysisReportRepository.class);
    storage = mock(SupabaseStorageService.class);
    userService = mock(UserService.class);
    clock = Clock.fixed(fixedNow, ZoneOffset.UTC);

    service = new AnalyzeService(c2pa, imageService, repo, storage, userService, clock);

    when(userService.getCurrentUserIdOrThrow()).thenReturn(userId);
    // Bearer required by storage for signed URL generation.
    when(userService.getCurrentBearerOrThrow()).thenReturn("bearer-token");
  }

  /** Creates an owned Image with the provided storage path. */
  private Image ownedImage(String storagePath) {
    Image img = new Image();
    img.setId(imageId);
    img.setUserId(userId);
    img.setStoragePath(storagePath);
    return img;
  }

  /**
   * submitAnalysis happy-path:
   * - Creates PENDING report.
   * - Downloads via signed URL.
   * - Extracts manifest.
   * - Marks DONE with manifest details.
   */
  @Test
  void submitAnalysis_happyPath_marksCompleted_andReturnsId() throws Exception {
    when(imageService.getById(userId, imageId)).thenReturn(ownedImage("u/i/file.png"));

    File downloadable = File.createTempFile("source-", ".bin");
    Files.writeString(downloadable.toPath(), "bytes", StandardCharsets.UTF_8);

    when(storage.createSignedUrl(eq("u/i/file.png"), anyString()))
        .thenReturn(downloadable.toURI().toURL().toString());

    String manifest = "{\"c2pa\":\"ok\"}";
    when(c2pa.extractManifest(any(File.class))).thenReturn(manifest);

    UUID analysisId = UUID.randomUUID();
    AnalysisReport pending = new AnalysisReport(imageId);
    pending.setCreatedAt(fixedNow);

    // Save returns entity with generated id; later findById reads same row.
    when(repo.save(any(AnalysisReport.class))).thenAnswer(inv -> {
      AnalysisReport ar = inv.getArgument(0);
      ar.setId(analysisId);
      return ar;
    });
    when(repo.findById(analysisId)).thenReturn(Optional.of(pending));

    Dtos.AnalyzeStartResponse resp = service.submitAnalysis(imageId);
    assertThat(resp.analysisId()).isEqualTo(analysisId.toString());

    // Capture final save and assert DONE + manifest persisted.
    ArgumentCaptor<AnalysisReport> saved = ArgumentCaptor.forClass(AnalysisReport.class);
    verify(repo, atLeast(1)).save(saved.capture());
    AnalysisReport last = saved.getAllValues().get(saved.getAllValues().size() - 1);
    assertThat(last.getStatus().name()).isEqualTo("DONE");
    assertThat(last.getDetails()).isEqualTo(manifest);

    downloadable.delete();
  }

  /** If image has no storage path, service should fail fast with 400-like error. */
  @Test
  void submitAnalysis_missingStoragePath_throws400() {
    when(imageService.getById(userId, imageId)).thenReturn(ownedImage(null));

    assertThrows(MissingStoragePathException.class, () -> service.submitAnalysis(imageId));
    verify(repo, never()).save(any());
  }

  /**
   * If the signed URL download fails, the report should be marked FAILED and
   * error JSON should be stored in details.
   */
  @Test
  void submitAnalysis_downloadFailure_marksFailed() throws Exception {
    when(imageService.getById(userId, imageId)).thenReturn(ownedImage("x/y/z.png"));
    when(storage.createSignedUrl(eq("x/y/z.png"), anyString()))
        .thenReturn("file:/does/not/exist");

    UUID analysisId = UUID.randomUUID();
    AnalysisReport pending = new AnalysisReport(imageId);
    pending.setCreatedAt(fixedNow);

    when(repo.save(any(AnalysisReport.class))).thenAnswer(inv -> {
      AnalysisReport ar = inv.getArgument(0);
      ar.setId(analysisId);
      return ar;
    });
    when(repo.findById(analysisId)).thenReturn(Optional.of(pending));

    Dtos.AnalyzeStartResponse resp = service.submitAnalysis(imageId);
    assertThat(resp.analysisId()).isEqualTo(analysisId.toString());

    ArgumentCaptor<AnalysisReport> saved = ArgumentCaptor.forClass(AnalysisReport.class);
    verify(repo, atLeast(1)).save(saved.capture());
    AnalysisReport last = saved.getAllValues().get(saved.getAllValues().size() - 1);
    assertThat(last.getStatus().name()).isEqualTo("FAILED");
    assertThat(last.getDetails()).contains("\"error\":");
  }

  /**
   * If the C2PA tool throws, the report should be marked FAILED and the error
   * message should be captured into details JSON.
   */
  @Test
  void submitAnalysis_c2paFailure_marksFailed() throws Exception {
    when(imageService.getById(userId, imageId)).thenReturn(ownedImage("a/b/c.png"));

    File downloadable = File.createTempFile("dl-", ".img");
    try (FileWriter fw = new FileWriter(downloadable)) {
      fw.write("imgdata");
    }
    when(storage.createSignedUrl(eq("a/b/c.png"), anyString()))
        .thenReturn(downloadable.toURI().toURL().toString());

    when(c2pa.extractManifest(any(File.class))).thenThrow(new RuntimeException("boom"));

    UUID analysisId = UUID.randomUUID();
    AnalysisReport pending = new AnalysisReport(imageId);
    pending.setCreatedAt(fixedNow);

    when(repo.save(any(AnalysisReport.class))).thenAnswer(inv -> {
      AnalysisReport ar = inv.getArgument(0);
      ar.setId(analysisId);
      return ar;
    });
    when(repo.findById(analysisId)).thenReturn(Optional.of(pending));

    Dtos.AnalyzeStartResponse resp = service.submitAnalysis(imageId);
    assertThat(resp.analysisId()).isEqualTo(analysisId.toString());

    ArgumentCaptor<AnalysisReport> saved = ArgumentCaptor.forClass(AnalysisReport.class);
    verify(repo, atLeast(1)).save(saved.capture());
    AnalysisReport last = saved.getAllValues().get(saved.getAllValues().size() - 1);
    assertThat(last.getStatus().name()).isEqualTo("FAILED");
    assertThat(last.getDetails()).contains("\"error\":\"");

    downloadable.delete();
  }

  /**
   * getMetadata returns stored manifest JSON and re-validates ownership by
   * resolving the associated image.
   */
  @Test
  void getMetadata_success_returnsManifest_andChecksOwnership() {
    UUID analysisId = UUID.randomUUID();
    AnalysisReport report = new AnalysisReport(imageId);
    report.setId(analysisId);
    report.setDetails("{\"m\":\"v\"}");
    report.setStatus(AnalysisReport.ReportStatus.DONE);

    when(repo.findById(analysisId)).thenReturn(Optional.of(report));
    when(imageService.getById(userId, imageId)).thenReturn(ownedImage("s/p.png"));

    Dtos.AnalysisManifestResponse out = service.getMetadata(analysisId);
    assertThat(out.analysisId()).isEqualTo(analysisId.toString());
    assertThat(out.manifestJson()).isEqualTo("{\"m\":\"v\"}");
  }

  /** getMetadata should 404 when the analysis row has no manifest details. */
  @Test
  void getMetadata_missing_throws404() {
    UUID analysisId = UUID.randomUUID();
    AnalysisReport report = new AnalysisReport(imageId);
    report.setId(analysisId);
    report.setDetails(null);
    report.setStatus(AnalysisReport.ReportStatus.DONE);

    when(repo.findById(analysisId)).thenReturn(Optional.of(report));
    when(imageService.getById(userId, imageId)).thenReturn(ownedImage("x"));

    assertThrows(NotFoundException.class, () -> service.getMetadata(analysisId));
  }

  /** getMetadata should 404 when the analysis id does not exist. */
  @Test
  void getMetadata_notFound_throws404() {
    UUID analysisId = UUID.randomUUID();
    when(repo.findById(analysisId)).thenReturn(Optional.empty());
    assertThrows(NotFoundException.class, () -> service.getMetadata(analysisId));
  }

  /**
   * getConfidence returns status and score and re-validates ownership via the
   * linked image.
   */
  @Test
  void getConfidence_success_returnsStatusAndScore_andChecksOwnership() {
    UUID analysisId = UUID.randomUUID();
    AnalysisReport report = new AnalysisReport(imageId);
    report.setId(analysisId);
    report.setStatus(AnalysisReport.ReportStatus.PENDING);
    report.setConfidence(null);

    when(repo.findById(analysisId)).thenReturn(Optional.of(report));
    when(imageService.getById(userId, imageId)).thenReturn(ownedImage("x"));

    Dtos.AnalyzeConfidenceResponse out = service.getConfidence(analysisId);
    assertThat(out.analysisId()).isEqualTo(analysisId.toString());
    assertThat(out.status()).isEqualTo("PENDING");
    assertThat(out.score()).isNull();
  }

  /**
   * compare enforces ownership of both images and returns a stub response
   * in Iteration 1.
   */
  @Test
  void compare_enforcesOwnershipOnBothImages() {
    when(imageService.getById(userId, imageId)).thenReturn(ownedImage("x"));
    UUID otherImage = UUID.randomUUID();
    when(imageService.getById(userId, otherImage)).thenReturn(ownedImage("y"));

    Dtos.AnalyzeCompareResponse out = service.compare(imageId, otherImage);
    assertThat(out.status()).isEqualTo("DONE");
    assertThat(out.similarity()).isNull();
    assertThat(out.note()).contains("stub");
  }

  /** compare should propagate ForbiddenException from left image check. */
  @Test
  void compare_forbiddenOnLeft_propagates() {
    UUID otherImage = UUID.randomUUID();
    when(imageService.getById(userId, imageId))
        .thenThrow(new ForbiddenException("nope"));

    assertThrows(ForbiddenException.class, () -> service.compare(imageId, otherImage));
  }

  /**
   * submitAnalysis should propagate ownership errors thrown by ImageService
   * before any repo writes occur.
   */
  @Test
  void submitAnalysis_propagatesOwnershipErrors() {
    doThrow(new ForbiddenException("nope"))
        .when(imageService)
        .getById(eq(userId), eq(imageId));
    assertThrows(ForbiddenException.class, () -> service.submitAnalysis(imageId));

    doThrow(new NotFoundException("missing"))
        .when(imageService)
        .getById(eq(userId), eq(imageId));
    assertThrows(NotFoundException.class, () -> service.submitAnalysis(imageId));
  }

  /** truncate(): returns original when under limit. */
  @Test
  void truncate_shorterThanLimit_returnsOriginal() {
    String s = "short json";
    String out = callPrivate(
        service,
        "truncate",
        new Class<?>[] { String.class, int.class },
        s,
        50
    );
    assertThat(out).isEqualTo(s);
  }

  /** truncate(): caps to limit when over. */
  @Test
  void truncate_longerThanLimit_isCappedToLimit() {
    String s = "x".repeat(200);
    int limit = 50;
    String out = callPrivate(
        service,
        "truncate",
        new Class<?>[] { String.class, int.class },
        s,
        limit
    );
    assertThat(out.length()).isEqualTo(limit);
  }

  /**
   * downloadToTemp(): given a file:// URL, writes to a temp file and returns it.
   * Verifies byte-for-byte integrity.
   */
  @Test
  void downloadToTemp_fileUrl_intoProvidedDir_copiesBytes() throws Exception {
    File src = File.createTempFile("src-", ".bin");
    byte[] payload = "hello-bytes".getBytes(StandardCharsets.UTF_8);
    Files.write(src.toPath(), payload);
    String url = src.toURI().toURL().toString();

    File destDir = Files.createTempDirectory("dl-dir-").toFile();

    File out = callPrivate(
        service,
        "downloadToTemp",
        new Class<?>[] { String.class, String.class },
        url,
        destDir.getAbsolutePath()
    );

    assertTrue(out.exists());
    assertThat(Files.readAllBytes(out.toPath())).isEqualTo(payload);

    // cleanup
    out.delete();
    destDir.delete();
    src.delete();
  }

  // Reflection helper used to reach private test targets.
  // Avoids adding package-private visibility in main code.
  @SuppressWarnings("unchecked")
  private static <T> T callPrivate(Object target,
                                   String name,
                                   Class<?>[] paramTypes,
                                   Object... args) {
    try {
      java.lang.reflect.Method m = target.getClass().getDeclaredMethod(name, paramTypes);
      m.setAccessible(true);
      return (T) m.invoke(target, args);
    } catch (Exception e) {
      throw new RuntimeException("Failed to invoke private method: " + name, e);
    }
  }
}
