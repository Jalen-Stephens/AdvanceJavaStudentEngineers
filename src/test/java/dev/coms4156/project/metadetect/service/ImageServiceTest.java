package dev.coms4156.project.metadetect.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.coms4156.project.metadetect.db.RlsContext;
import dev.coms4156.project.metadetect.model.Image;
import dev.coms4156.project.metadetect.repository.ImageRepository;
import dev.coms4156.project.metadetect.service.SupabaseStorageService;
import dev.coms4156.project.metadetect.service.errors.ForbiddenException;
import dev.coms4156.project.metadetect.service.errors.NotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

/**
 * Unit tests for {@link ImageService}.
 * Scope:
 * - Validates CRUD behavior under RLS impersonation (via {@link RlsContext}).
 * - Ensures ownership checks, error propagation, and storage orchestration.
 * - Covers paging, update semantics, and signed URL generation.
 * Test style:
 * - Use lenient stubs for RLS to avoid noise in tests that don't cross it.
 * - Mock repository and storage; assert side effects and saved entities.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

  @Mock private ImageRepository repo;
  @Mock private RlsContext rls;
  @Mock private SupabaseStorageService storage;

  @InjectMocks private ImageService service;

  private UUID ownerId;
  private UUID otherUserId;
  private UUID imageId;

  @BeforeEach
  void setUp() {
    ownerId = UUID.randomUUID();
    otherUserId = UUID.randomUUID();
    imageId = UUID.randomUUID();

    // Make RLS helpers simply run provided work in tests.
    when(rls.asUser(any(UUID.class), any(Supplier.class)))
        .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
    doAnswer(inv -> {
      ((Runnable) inv.getArgument(1)).run();
      return null;
    })
      .when(rls).asUser(any(UUID.class), any(Runnable.class));
  }

  // ---- helpers --------------------------------------------------------------

  /** Creates a new Image with default fields for the provided owner. */
  private static Image newImage(UUID userId) {
    Image img = new Image();
    img.setId(UUID.randomUUID());
    img.setUserId(userId);
    img.setFilename("orig.jpg");
    img.setStoragePath("images/orig.jpg");
    img.setLabels(new String[] { "a", "b" });
    img.setNote("note");
    return img;
  }

  /** Mimics DB "echo" behavior where id is generated on first save. */
  private static Image dbEchoSave(Image input) {
    Image out = new Image();
    out.setId(input.getId() != null ? input.getId() : UUID.randomUUID());
    out.setUserId(input.getUserId());
    out.setFilename(input.getFilename());
    out.setStoragePath(input.getStoragePath());
    out.setLabels(input.getLabels());
    out.setNote(input.getNote());
    return out;
  }

  // ---- CREATE ---------------------------------------------------------------

  /**
   * create(): persists with owner/filename and returns the DB-populated entity.
   * Asserts that id is assigned by the DB.
   */
  @Test
  void create_persistsWithOwnerAndFilename_andReturnsSaved() {
    when(repo.save(any(Image.class))).thenAnswer(inv -> dbEchoSave(inv.getArgument(0)));

    Image saved =
        service.create(ownerId, "file.jpg", "images/file.jpg", new String[] { "x" }, "hello");

    ArgumentCaptor<Image> captor = ArgumentCaptor.forClass(Image.class);
    verify(repo).save(captor.capture());

    Image toSave = captor.getValue();
    assertThat(toSave.getId()).isNull();
    assertThat(toSave.getUserId()).isEqualTo(ownerId);
    assertThat(toSave.getFilename()).isEqualTo("file.jpg");
    assertThat(toSave.getStoragePath()).isEqualTo("images/file.jpg");
    assertThat(toSave.getLabels()).containsExactly("x");
    assertThat(toSave.getNote()).isEqualTo("hello");

    assertThat(saved.getId()).isNotNull();
  }

  // ---- READ (single) --------------------------------------------------------

  /** getById(): owner OK -> return entity. */
  @Test
  void getById_ownerOk_returnsImage() {
    Image img = newImage(ownerId);
    when(repo.findById(imageId)).thenReturn(Optional.of(img));

    Image out = service.getById(ownerId, imageId);

    assertThat(out).isSameAs(img);
    verify(repo).findById(imageId);
  }

  /** getById(): missing -> NotFoundException. */
  @Test
  void getById_notFound_throwsNotFound() {
    when(repo.findById(imageId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getById(ownerId, imageId))
      .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("Image not found");

    verify(repo).findById(imageId);
  }

  /** getById(): owner mismatch -> ForbiddenException. */
  @Test
  void getById_notOwner_throwsForbidden() {
    Image img = newImage(otherUserId);
    when(repo.findById(imageId)).thenReturn(Optional.of(img));

    assertThatThrownBy(() -> service.getById(ownerId, imageId))
        .isInstanceOf(ForbiddenException.class);

    verify(repo).findById(imageId);
  }

  // ---- LIST ----------------------------------------------------------------

  /** listByOwner(): uses repository sorted query and returns all within page. */
  @Test
  void listByOwner_usesSortedQuery() {
    Image a = newImage(ownerId);
    Image b = newImage(ownerId);
    when(repo.findAllByUserIdOrderByUploadedAtDesc(ownerId)).thenReturn(List.of(a, b));

    List<Image> out = service.listByOwner(ownerId, 0, 5);

    assertThat(out).containsExactly(a, b);
    verify(repo).findAllByUserIdOrderByUploadedAtDesc(ownerId);
  }

  /** listByOwner(): invalid args -> IllegalArgumentException. */
  @Test
  void listByOwner_invalidArgs_throwIllegalArgument() {
    assertThatThrownBy(() -> service.listByOwner(ownerId, -1, 5))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.listByOwner(ownerId, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ---- UPDATE --------------------------------------------------------------

  /** update(): nulls mean "no change" to that field. */
  @Test
  void update_noChanges_keepsOriginalValues() {
    Image img = newImage(ownerId);
    when(repo.findById(imageId)).thenReturn(Optional.of(img));
    when(repo.save(any(Image.class))).thenAnswer(inv -> inv.getArgument(0));

    Image out = service.update(ownerId, imageId, null, null, null, null);

    assertThat(out.getFilename()).isEqualTo("orig.jpg");
    assertThat(out.getStoragePath()).isEqualTo("images/orig.jpg");
    assertThat(out.getLabels()).containsExactly("a", "b");
    assertThat(out.getNote()).isEqualTo("note");

    verify(repo).findById(imageId);
    verify(repo).save(any(Image.class));
  }

  /** update(): blank filename ignored; other fields updated. */
  @Test
  void update_blankFilename_isIgnored_butOtherFieldsApply() {
    Image img = newImage(ownerId);
    when(repo.findById(imageId)).thenReturn(Optional.of(img));
    when(repo.save(any(Image.class))).thenAnswer(inv -> inv.getArgument(0));

    Image out =
        service.update(ownerId, imageId, "   ", "images/new.jpg", new String[] { "k" }, null);

    assertThat(out.getFilename()).isEqualTo("orig.jpg");
    assertThat(out.getStoragePath()).isEqualTo("images/new.jpg");
    assertThat(out.getLabels()).containsExactly("k");
    assertThat(out.getNote()).isEqualTo("note");
  }

  /** update(): owner mismatch -> ForbiddenException. */
  @Test
  void update_ownerMismatch_throwsForbidden() {
    Image img = newImage(otherUserId);
    when(repo.findById(imageId)).thenReturn(Optional.of(img));

    assertThatThrownBy(() -> service.update(ownerId, imageId, "x.jpg", null, null, null))
        .isInstanceOf(ForbiddenException.class);
  }

  /** update(): target not found -> NotFoundException. */
  @Test
  void update_notFound_throwsNotFound() {
    when(repo.findById(imageId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.update(ownerId, imageId, "x.jpg", null, null, null))
        .isInstanceOf(NotFoundException.class);
  }

  // ---- DELETE --------------------------------------------------------------

  /** delete(): owner OK -> row removed. */
  @Test
  void delete_ownerOk_deletes() {
    Image img = newImage(ownerId);
    when(repo.findById(imageId)).thenReturn(Optional.of(img));
    doNothing().when(repo).deleteById(imageId);

    service.delete(ownerId, imageId);

    verify(repo).findById(imageId);
    verify(repo).deleteById(eq(imageId));
  }

  /** delete(): owner mismatch -> ForbiddenException and no delete. */
  @Test
  void delete_notOwner_throwsForbidden_andDoesNotDelete() {
    Image img = newImage(otherUserId);
    when(repo.findById(imageId)).thenReturn(Optional.of(img));

    assertThatThrownBy(() -> service.delete(ownerId, imageId))
        .isInstanceOf(ForbiddenException.class);

    verify(repo).findById(imageId);
    verify(repo, never()).deleteById(any());
  }

  /** delete(): not found -> NotFoundException and no delete. */
  @Test
  void delete_notFound_throwsNotFound() {
    when(repo.findById(imageId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(ownerId, imageId))
        .isInstanceOf(NotFoundException.class);

    verify(repo).findById(imageId);
    verify(repo, never()).deleteById(any());
  }

  // ---- NEW BRANCH COVERAGE: upload / signed URL / purge --------------------

  /**
   * upload(): creates row, uploads bytes, then updates row with storage path.
   * Verifies storage key structure and save sequence.
   */
  @Test
  void upload_happyPath_setsStoragePathAndSaves() throws Exception {
    UUID newId = UUID.randomUUID();

    // First save creates row; mimic DB id assignment when id is null.
    when(repo.save(any(Image.class))).thenAnswer(inv -> {
      Image in = inv.getArgument(0);
      Image out = new Image();
      out.setId(in.getId() == null ? newId : in.getId());
      out.setUserId(in.getUserId());
      out.setFilename(in.getFilename());
      out.setStoragePath(in.getStoragePath());
      out.setLabels(in.getLabels());
      out.setNote(in.getNote());
      return out;
    });

    // findById used by update() to re-load and persist new storage path.
    Image found = new Image();
    found.setId(newId);
    found.setUserId(ownerId);
    found.setFilename("pic.png");
    when(repo.findById(newId)).thenReturn(Optional.of(found));

    when(storage.uploadObject(any(byte[].class), eq("image/png"),
      anyString(), eq("jwt"))).thenReturn("ok");

    MultipartFile file = new org.springframework.mock.web.MockMultipartFile(
        "file", "pic.png", "image/png", "DATA".getBytes());

    Image result = service.upload(ownerId, "jwt", file);

    String expectedKey = ownerId + "/" + newId + "--pic.png";
    assertThat(result.getId()).isEqualTo(newId);
    assertThat(result.getUserId()).isEqualTo(ownerId);
    assertThat(result.getFilename()).isEqualTo("pic.png");
    assertThat(result.getStoragePath()).isEqualTo(expectedKey);

    verify(storage).uploadObject(any(byte[].class), eq("image/png"),
        eq(expectedKey), eq("jwt"));

    // Two saves: create (no storage path) then update (with storage path).
    ArgumentCaptor<Image> saveCap = ArgumentCaptor.forClass(Image.class);
    verify(repo, org.mockito.Mockito.times(2)).save(saveCap.capture());

    List<Image> saves = saveCap.getAllValues();
    Image first = saves.get(0);
    Image second = saves.get(1);

    assertThat(first.getUserId()).isEqualTo(ownerId);
    assertThat(first.getFilename()).isEqualTo("pic.png");
    assertThat(first.getStoragePath()).isNull();

    assertThat(second.getUserId()).isEqualTo(ownerId);
    assertThat(second.getFilename()).isEqualTo("pic.png");
    assertThat(second.getStoragePath()).isEqualTo(expectedKey);
  }

  /** upload(): null content type falls back to application/octet-stream. */
  @Test
  void upload_nullContentType_defaultsToOctetStream() throws Exception {
    UUID newId = UUID.randomUUID();

    when(repo.save(any(Image.class))).thenAnswer(inv -> {
      Image in = inv.getArgument(0);
      Image out = dbEchoSave(in);
      if (in.getId() == null) {
        out.setId(newId);
      }
      return out;
    });

    Image found = new Image();
    found.setId(newId);
    found.setUserId(ownerId);
    found.setFilename("pic.bin");
    when(repo.findById(newId)).thenReturn(Optional.of(found));

    ArgumentCaptor<String> contentTypeCap = ArgumentCaptor.forClass(String.class);
    when(storage.uploadObject(any(byte[].class),
      contentTypeCap.capture(), anyString(), anyString()))
        .thenReturn("ok");

    MultipartFile file = new org.springframework.mock.web.MockMultipartFile(
        "file", "pic.bin", null, "DATA".getBytes());

    service.upload(ownerId, "jwt", file);

    assertThat(contentTypeCap.getValue())
        .isEqualTo(MediaType.APPLICATION_OCTET_STREAM_VALUE);
  }

  /** upload(): propagates IOException from MultipartFile.getBytes(). */
  @Test
  void upload_fileBytesThrowIoException_bubblesUp() throws Exception {
    UUID newId = UUID.randomUUID();
    when(repo.save(any(Image.class))).thenAnswer(inv -> {
      Image in = inv.getArgument(0);
      Image out = new Image();
      out.setId(newId);
      out.setUserId(in.getUserId());
      out.setFilename(in.getFilename());
      return out;
    });

    MultipartFile bad = org.mockito.Mockito.mock(MultipartFile.class);
    when(bad.getOriginalFilename()).thenReturn("x.png");
    when(bad.getContentType()).thenReturn("image/png");
    when(bad.getBytes()).thenThrow(new IOException("read fail"));

    assertThatThrownBy(() -> service.upload(ownerId, "jwt", bad))
        .isInstanceOf(IOException.class);
  }

  /** getSignedUrl(): missing/blank storage path -> NotFoundException. */
  @Test
  void getSignedUrl_noStoragePath_throwsNotFound() {
    Image img = new Image();
    img.setId(imageId);
    img.setUserId(ownerId);
    img.setFilename("f.png");
    img.setStoragePath(null);
    when(repo.findById(imageId)).thenReturn(Optional.of(img));

    assertThatThrownBy(() -> service.getSignedUrl(ownerId, "jwt", imageId))
        .isInstanceOf(NotFoundException.class);
  }

  /** deleteAndPurge(): has storage path -> delete storage then DB. */
  @Test
  void deleteAndPurge_withStoragePath_deletesStorageThenDb() {
    Image img = new Image();
    img.setId(imageId);
    img.setUserId(ownerId);
    img.setFilename("f.png");
    img.setStoragePath("owner/" + imageId + "--f.png");
    when(repo.findById(imageId)).thenReturn(Optional.of(img));

    doNothing().when(storage).deleteObject(eq(img.getStoragePath()), eq("jwt"));
    doNothing().when(repo).deleteById(imageId);

    service.deleteAndPurge(ownerId, "jwt", imageId);

    verify(storage).deleteObject(eq(img.getStoragePath()), eq("jwt"));
    verify(repo).deleteById(imageId);
  }

  /** deleteAndPurge(): blank storage path -> skip storage delete, remove DB row. */
  @Test
  void deleteAndPurge_noStoragePath_skipsStorageDelete() {
    Image img = new Image();
    img.setId(imageId);
    img.setUserId(ownerId);
    img.setFilename("f.png");
    img.setStoragePath("   ");
    when(repo.findById(imageId)).thenReturn(Optional.of(img));

    doNothing().when(repo).deleteById(imageId);

    service.deleteAndPurge(ownerId, "jwt", imageId);

    verify(storage, never()).deleteObject(anyString(), anyString());
    verify(repo).deleteById(imageId);
  }

  /** deleteAndPurge(): storage delete failure -> do not delete DB row. */
  @Test
  void deleteAndPurge_storageDeleteFails_doesNotDeleteDb() {
    Image img = new Image();
    img.setId(imageId);
    img.setUserId(ownerId);
    img.setFilename("f.png");
    img.setStoragePath("owner/" + imageId + "--f.png");
    when(repo.findById(imageId)).thenReturn(Optional.of(img));

    doThrow(new RuntimeException("storage down"))
      .when(storage).deleteObject(eq(img.getStoragePath()), eq("jwt"));

    assertThatThrownBy(() -> service.deleteAndPurge(ownerId, "jwt", imageId))
      .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("storage down");

    verify(repo, never()).deleteById(any());
  }

  /** getSignedUrl(): happy path returns signed URL from storage service. */
  @Test
  void getSignedUrl_happyPath_returnsSignedUrl() {
    UUID imgId = UUID.randomUUID();
    String key = ownerId + "/" + imgId + "--pic.png";

    Image img = new Image();
    img.setId(imgId);
    img.setUserId(ownerId);
    img.setFilename("pic.png");
    img.setStoragePath(key);
    when(repo.findById(imgId)).thenReturn(Optional.of(img));

    when(storage.createSignedUrl(eq(key), eq("jwt")))
        .thenReturn("https://signed.example/url");

    String url = service.getSignedUrl(ownerId, "jwt", imgId);

    assertThat(url).isEqualTo("https://signed.example/url");
    verify(storage).createSignedUrl(eq(key), eq("jwt"));
  }

  /** getSignedUrl(): blank storage path -> NotFoundException. */
  @Test
  void getSignedUrl_blankStoragePath_throwsNotFound() {
    UUID imgId = UUID.randomUUID();
    Image img = new Image();
    img.setId(imgId);
    img.setUserId(ownerId);
    img.setFilename("f.png");
    img.setStoragePath("   ");
    when(repo.findById(imgId)).thenReturn(Optional.of(img));

    assertThatThrownBy(() -> service.getSignedUrl(ownerId, "jwt", imgId))
        .isInstanceOf(NotFoundException.class);
  }

  /** update(): filename + note set when provided; other fields unchanged. */
  @Test
  void update_setsFilenameAndNote_whenProvided() {
    Image img = newImage(ownerId);
    when(repo.findById(imageId)).thenReturn(Optional.of(img));
    when(repo.save(any(Image.class))).thenAnswer(inv -> inv.getArgument(0));

    Image out = service.update(
        ownerId,
        imageId,
        "renamed.jpg",
        null,
        null,
        "new-note"
    );

    assertThat(out.getFilename()).isEqualTo("renamed.jpg");
    assertThat(out.getNote()).isEqualTo("new-note");
    assertThat(out.getStoragePath()).isEqualTo("images/orig.jpg");
    assertThat(out.getLabels()).containsExactly("a", "b");
  }

  /** deleteAndPurge(): null storage path -> skip storage delete. */
  @Test
  void deleteAndPurge_nullStoragePath_skipsStorageDelete() {
    Image img = new Image();
    img.setId(imageId);
    img.setUserId(ownerId);
    img.setFilename("f.png");
    img.setStoragePath(null);
    when(repo.findById(imageId)).thenReturn(Optional.of(img));

    doNothing().when(repo).deleteById(imageId);

    service.deleteAndPurge(ownerId, "jwt", imageId);

    verify(storage, never()).deleteObject(anyString(), anyString());
    verify(repo).deleteById(imageId);
  }
}
