package dev.coms4156.project.metadetect.service;

import dev.coms4156.project.metadetect.db.RlsContext;
import dev.coms4156.project.metadetect.model.Image;
import dev.coms4156.project.metadetect.repository.ImageRepository;
import dev.coms4156.project.metadetect.service.errors.ForbiddenException;
import dev.coms4156.project.metadetect.service.errors.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles creation, retrieval, listing, and ownership validation of images
 * before interacting with Supabase storage or analysis services.
 */
@Service
public class ImageService {

  private final ImageRepository repo;
  private final RlsContext rls;

  public ImageService(ImageRepository repo, RlsContext rls) {
    this.repo = repo;
    this.rls = rls;
  }

  /** Creates a new image record owned by the current user. */
  @Transactional
  public Image create(UUID userId,
                      String filename,
                      @Nullable String storagePath,
                      @Nullable String[] labels,   // <-- match model
                      @Nullable String note) {
    return rls.asUser(userId, () -> {
      Image img = new Image();
      img.setUserId(userId);         // REQUIRED (FK to auth.users)
      img.setFilename(filename);
      img.setStoragePath(storagePath);
      img.setLabels(labels);
      img.setNote(note);
      // Do NOT set uploadedAt — DB default handles it
      return repo.save(img);
    });
  }

  /** Updates optional metadata on an image if owned by the user. */
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

  /** Returns an image if and only if the requesting user owns it. */
  public Image getById(UUID currentUserId, UUID imageId) {
    return rls.asUser(currentUserId, () -> {
      Image img = repo.findById(imageId)
            .orElseThrow(() -> new NotFoundException("Image not found: " + imageId));
      requireOwner(currentUserId, img);
      return img;
    });
  }

  /** Lists all images belonging to a user in descending upload order. */
  public List<Image> listByOwner(UUID currentUserId) {
    return rls.asUser(currentUserId,
      () -> repo.findAllByUserIdOrderByUploadedAtDesc(currentUserId));
  }

  /** Deletes an image the user owns. */
  @Transactional
  public void delete(UUID currentUserId, UUID imageId) {
    rls.asUser(currentUserId, () -> {
      Image img = repo.findById(imageId)
            .orElseThrow(() -> new NotFoundException("Image not found: " + imageId));
      requireOwner(currentUserId, img);
      repo.deleteById(imageId);
    });
  }

  private static void requireOwner(UUID userId, Image img) {
    if (!img.getUserId().equals(userId)) {
      throw new ForbiddenException("You do not own this image.");
    }
  }
}
