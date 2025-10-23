package dev.coms4156.project.metadetect.service;

import dev.coms4156.project.metadetect.db.RlsContext;
import dev.coms4156.project.metadetect.model.Image;
import dev.coms4156.project.metadetect.repository.ImageRepository;
import dev.coms4156.project.metadetect.service.errors.ForbiddenException;
import dev.coms4156.project.metadetect.service.errors.NotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Orchestrates image lifecycle across DB (metadata) and Supabase Storage (binary).
 * All repository access flows through RLS via {@link RlsContext}.
 */
@Service
public class ImageService {

  private final ImageRepository repo;
  private final RlsContext rls;
  private final SupabaseStorageService storage;

  /**
   * Constructs the service that coordinates repository access under RLS and
   * integrates with Supabase Storage for binary uploads/deletes.
   *
   * @param repo image JPA repository
   * @param rls row-level security context helper to impersonate the current user
   * @param storage Supabase storage client for object operations
   */
  public ImageService(ImageRepository repo, RlsContext rls, SupabaseStorageService storage) {
    this.repo = repo;
    this.rls = rls;
    this.storage = storage;
  }

  /**
   * Uploads an image file on behalf of {@code userId} and persists metadata.
   * <ol>
   *   <li>Create the Image row under RLS to establish ownership.</li>
   *   <li>Build a stable storage key {@code userId/imageId--filename}.</li>
   *   <li>Upload bytes using the caller's bearer token.</li>
   *   <li>Update the row with the storage path and return the updated entity.</li>
   * </ol>
   *
   * @param userId the owner performing the upload
   * @param bearer the user's JWT used for Supabase storage policies
   * @param file the multipart file to upload
   * @return the persisted {@link Image} including the storage path
   * @throws IOException if reading the multipart bytes fails
   */
  @Transactional
  public Image upload(UUID userId, String bearer, MultipartFile file) throws IOException {
    String original = Optional.ofNullable(file.getOriginalFilename())
        .orElse("upload.bin")
        .replaceAll("[/\\\\]", "_");

    // 1) Create row under RLS
    Image created = rls.asUser(userId, () -> {
      Image img = new Image();
      img.setUserId(userId);
      img.setFilename(original);
      return repo.save(img);
    });

    // 2) Storage key derived from DB id (stable & unique)
    String storageKey = userId + "/" + created.getId() + "--" + original;

    // 3) Upload to Supabase
    storage.uploadObject(
        file.getBytes(),
        Optional.ofNullable(file.getContentType()).orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE),
        storageKey,
        bearer
    );

    // 4) Persist storage path
    return update(userId, created.getId(), null, storageKey, null, null);
  }

  /**
   * Deletes the image both from storage and the database. Storage delete is attempted first;
   * if it fails, the DB row is not removed to avoid silent orphaning.
   *
   * @param userId the current user (must own the image)
   * @param bearer bearer JWT used for storage deletion
   * @param imageId id of the image to delete
   * @throws NotFoundException if the image does not exist
   * @throws ForbiddenException if the user does not own the image
   */
  @Transactional
  public void deleteAndPurge(UUID userId, String bearer, UUID imageId) {
    Image img = getById(userId, imageId); // enforces ownership
    String storagePath = img.getStoragePath();

    if (storagePath != null && !storagePath.isBlank()) {
      storage.deleteObject(storagePath, bearer);
    }

    delete(userId, imageId);
  }

  /**
   * Produces a short-lived signed URL for the caller to access a private image.
   *
   * @param userId the current user (must own the image)
   * @param bearer bearer JWT used by the storage service to sign the URL
   * @param imageId id of the image to sign
   * @return a time-limited HTTPS URL
   * @throws NotFoundException if the image has no storage object
   * @throws ForbiddenException if the user does not own the image
   */
  public String getSignedUrl(UUID userId, String bearer, UUID imageId) {
    Image img = getById(userId, imageId);
    if (img.getStoragePath() == null || img.getStoragePath().isBlank()) {
      throw new NotFoundException("Image has no storage object");
    }
    return storage.createSignedUrl(img.getStoragePath(), bearer);
  }

  /**
   * Creates a new image metadata row owned by {@code userId}.
   *
   * @param userId owner id
   * @param filename logical filename to display
   * @param storagePath optional storage path (may be null at creation)
   * @param labels optional labels/tag array
   * @param note optional user note
   * @return the persisted {@link Image}
   */
  @Transactional
  public Image create(UUID userId,
                      String filename,
                      @Nullable String storagePath,
                      @Nullable String[] labels,
                      @Nullable String note) {
    return rls.asUser(userId, () -> {
      Image img = new Image();
      img.setUserId(userId);
      img.setFilename(filename);
      img.setStoragePath(storagePath);
      img.setLabels(labels);
      img.setNote(note);
      return repo.save(img);
    });
  }

  /**
   * Updates mutable fields on an image, enforcing ownership under RLS.
   *
   * @param currentUserId the acting user
   * @param imageId the image id to modify
   * @param newFilename optional new filename (ignored if null/blank)
   * @param newStoragePath optional new storage path (ignored if null/blank)
   * @param newLabels optional replacement labels array (null = no change)
   * @param newNote optional replacement note (null = no change)
   * @return the updated {@link Image}
   * @throws NotFoundException if the image does not exist
   * @throws ForbiddenException if the user does not own the image
   */
  @Transactional
  public Image update(UUID currentUserId,
                      UUID imageId,
                      @Nullable String newFilename,
                      @Nullable String newStoragePath,
                      @Nullable String[] newLabels,
                      @Nullable String newNote) {
    return rls.asUser(currentUserId, () -> {
      Image img = repo.findById(imageId)
          .orElseThrow(() -> new NotFoundException("Image not found: " + imageId));
      requireOwner(currentUserId, img);

      if (newFilename != null && !newFilename.isBlank()) {
        img.setFilename(newFilename);
      }
      if (newStoragePath != null && !newStoragePath.isBlank()) {
        img.setStoragePath(newStoragePath);
      }
      if (newLabels != null) {
        img.setLabels(newLabels);
      }
      if (newNote != null) {
        img.setNote(newNote);
      }
      return repo.save(img);
    });
  }

  /**
   * Fetches an image by id if and only if the user owns it (enforced via RLS + check).
   *
   * @param currentUserId acting user id
   * @param imageId target image id
   * @return the owned {@link Image}
   * @throws NotFoundException if the image does not exist
   * @throws ForbiddenException if the user does not own the image
   */
  public Image getById(UUID currentUserId, UUID imageId) {
    return rls.asUser(currentUserId, () -> {
      Image img = repo.findById(imageId)
          .orElseThrow(() -> new NotFoundException("Image not found: " + imageId));
      requireOwner(currentUserId, img);
      return img;
    });
  }

  /**
   * Lists images owned by the current user, newest first. Uses repository method
   * without {@code Pageable}; falls back to safe in-memory paging.
   *
   * @param currentUserId acting user id
   * @param page zero-based page index
   * @param size page size (must be &gt; 0)
   * @return a sub-list representing the requested page
   * @throws IllegalArgumentException if {@code page} or {@code size} are invalid
   */
  public List<Image> listByOwner(UUID currentUserId, int page, int size) {
    if (page < 0 || size <= 0) {
      throw new IllegalArgumentException("Invalid paging arguments");
    }
    List<Image> all = rls.asUser(currentUserId,
        () -> repo.findAllByUserIdOrderByUploadedAtDesc(currentUserId));

    int from = Math.min(page * size, all.size());
    int to = Math.min(from + size, all.size());
    return all.subList(from, to);
  }

  /**
   * Permanently deletes the image row after verifying ownership.
   *
   * @param currentUserId acting user id
   * @param imageId id to delete
   * @throws NotFoundException if the image does not exist
   * @throws ForbiddenException if the user does not own the image
   */
  @Transactional
  public void delete(UUID currentUserId, UUID imageId) {
    rls.asUser(currentUserId, () -> {
      Image img = repo.findById(imageId)
          .orElseThrow(() -> new NotFoundException("Image not found: " + imageId));
      requireOwner(currentUserId, img);
      repo.deleteById(imageId);
    });
  }

  // ---- Helpers ----

  /**
   * Ensures the supplied {@code userId} matches the image owner.
   *
   * @param userId expected owner id
   * @param img image to check
   * @throws ForbiddenException if ownership does not match
   */
  private static void requireOwner(UUID userId, Image img) {
    if (!img.getUserId().equals(userId)) {
      throw new ForbiddenException("You do not own this image.");
    }
  }
}
