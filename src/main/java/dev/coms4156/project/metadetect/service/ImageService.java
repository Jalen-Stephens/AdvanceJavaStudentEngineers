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
 * Coordinates image metadata persistence (DB) and binary object storage
 * (Supabase Storage). Repository calls execute inside {@link RlsContext}
 * for user-scoped visibility consistent with Postgres RLS.
 * Ownership is enforced by combining:
 *  - RLS-scoped queries
 *  - explicit owner checks (requireOwner)
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
   * @param repo Spring Data repository for Image entities
   * @param rls RLS context wrapper to force `request.jwt.claims` during queries
   * @param storage Supabase Storage integration for uploads/deletes
   */
  public ImageService(ImageRepository repo, RlsContext rls, SupabaseStorageService storage) {
    this.repo = repo;
    this.rls = rls;
    this.storage = storage;
  }

  /**
   * Uploads a file for the given user and persists its metadata.
   * Steps:
   * 1) Create DB row (under RLS) to establish ownership.
   * 2) Compute a stable storage key: userId/imageId--filename.
   * 3) Upload binary to Supabase using the caller's bearer token.
   * 4) Update DB row with the storage path.
   */
  @Transactional
  public Image upload(UUID userId, String bearer, MultipartFile file) throws IOException {
    String original = Optional.ofNullable(file.getOriginalFilename())
        .orElse("upload.bin")
        .replaceAll("[/\\\\]", "_");

    // 1) Create DB row under the user identity
    Image created = rls.asUser(userId, () -> {
      Image img = new Image();
      img.setUserId(userId);
      img.setFilename(original);
      return repo.save(img);
    });

    // 2) Compute canonical storage key
    String storageKey = userId + "/" + created.getId() + "--" + original;

    // 3) Upload binary to Supabase
    storage.uploadObject(
        file.getBytes(),
        Optional.ofNullable(file.getContentType())
        .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE),
        storageKey,
        bearer
    );

    // 4) Persist storage path
    return update(userId, created.getId(), null, storageKey, null, null);
  }

  /**
   * Deletes the image (binary + metadata). If deletion from storage fails,
   * the DB row is retained to avoid orphaned state.
   */
  @Transactional
  public void deleteAndPurge(UUID userId, String bearer, UUID imageId) {
    Image img = getById(userId, imageId);
    String path = img.getStoragePath();

    if (path != null && !path.isBlank()) {
      storage.deleteObject(path, bearer);
    }
    delete(userId, imageId);
  }

  /**
   * Returns a short-lived signed URL for private image access.
   */
  public String getSignedUrl(UUID userId, String bearer, UUID imageId) {
    Image img = getById(userId, imageId);
    if (img.getStoragePath() == null || img.getStoragePath().isBlank()) {
      throw new NotFoundException("Image has no storage object");
    }
    return storage.createSignedUrl(img.getStoragePath(), bearer);
  }

  /**
   * Creates an image metadata row owned by the user.
   * (This does not upload any binary data.)
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
   * Updates mutable metadata fields for an owned image.
   * Null fields are interpreted as "no change".
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
   * Fetches an image by id if the user owns it. Both RLS and a
   * local owner check are performed for defense-in-depth.
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
   * Returns images owned by the user, newest first.
   * Uses in-memory paging for simplicity at Iteration 1 size.
   */
  public List<Image> listByOwner(UUID currentUserId, int page, int size) {
    if (page < 0 || size <= 0) {
      throw new IllegalArgumentException("Invalid paging arguments");
    }

    List<Image> all = rls.asUser(
        currentUserId,
        () -> repo.findAllByUserIdOrderByUploadedAtDesc(currentUserId)
    );

    int from = Math.min(page * size, all.size());
    int to = Math.min(from + size, all.size());
    return all.subList(from, to);
  }

  /**
   * Permanently removes an owned image metadata row.
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

  /**
   * Required ownership check used after an RLS-scoped lookup.
   * Throws ForbiddenException if mismatched.
   */
  private static void requireOwner(UUID userId, Image img) {
    if (!img.getUserId().equals(userId)) {
      throw new ForbiddenException("You do not own this image.");
    }
  }
}
