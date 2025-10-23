package dev.coms4156.project.metadetect.service;

import dev.coms4156.project.metadetect.db.RlsContext;
import dev.coms4156.project.metadetect.model.Image;
import dev.coms4156.project.metadetect.repository.ImageRepository;
import dev.coms4156.project.metadetect.service.errors.ForbiddenException;
import dev.coms4156.project.metadetect.service.errors.NotFoundException;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

/**
 * Provides CRUD and ownership-validated access to {@link Image} records,
 * executing all DB work under a per-request RLS context so Supabase policies
 * (… = auth.uid()) evaluate against the caller’s user id.
 */
@Service
public class ImageService {

  private final ImageRepository repo;
  private final RlsContext rls;

  public ImageService(ImageRepository repo, RlsContext rls) {
    this.repo = repo;
    this.rls = rls;
  }

  /**
   * Creates a new image record owned by the current user.
   */
  public Image create(UUID currentUserId,
                      String filename,
                      String storagePath,
                      String[] labels,
                      String note) {
    Objects.requireNonNull(currentUserId, "currentUserId");
    Objects.requireNonNull(filename, "filename");

    return rls.asUser(currentUserId, () -> {
      Image toSave = new Image(
        null,              // id (generated)
        currentUserId,     // userId (must match auth.uid() per RLS)
        filename,
        storagePath,
        labels,
        note,
        null               // uploadedAt (db default or set in repo)
      );
      return repo.save(toSave); // passes INSERT policy (userId == auth.uid())
    });
  }

  /**
   * Returns an image if and only if the requesting user owns it.
   * (RLS select already scopes rows to the caller; Forbidden check is a safeguard.)
   */
  public Image getById(UUID currentUserId, UUID imageId) {
    return rls.asUser(currentUserId, () -> {
      Image img = repo.findById(imageId)
        .orElseThrow(() -> new NotFoundException("Image not found: " + imageId));
      requireOwner(currentUserId, img); // redundant with RLS, but explicit
      return img;
    });
  }

  /**
   * Lists all images belonging to a user in descending upload order.
   * (RLS select also prevents cross-tenant leakage.)
   */
  public List<Image> listByOwner(UUID currentUserId) {
    return rls.asUser(currentUserId,
      () -> repo.findAllByUserIdOrderByUploadedAtDesc(currentUserId));
  }

  /**
   * Updates optional metadata on an image if owned by the user.
   */
  public Image update(UUID currentUserId,
                      UUID imageId,
                      String newFilename,
                      String newStoragePath,
                      String[] newLabels,
                      String newNote) {
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

      return repo.save(img); // passes UPDATE policy
    });
  }

  /**
   * Deletes an image the user owns.
   */
  public void delete(UUID currentUserId, UUID imageId) {
    rls.asUser(currentUserId, () -> {
      Image img = repo.findById(imageId)
        .orElseThrow(() -> new NotFoundException("Image not found: " + imageId));
      requireOwner(currentUserId, img);
      repo.deleteById(imageId); // passes DELETE policy
    });
  }

  private static void requireOwner(UUID userId, Image img) {
    if (!img.getUserId().equals(userId)) {
      throw new ForbiddenException("You do not own this image.");
    }
  }
}
