package dev.coms4156.project.metadetect.service;

import dev.coms4156.project.metadetect.model.Image;
import dev.coms4156.project.metadetect.repository.ImageRepository;
import dev.coms4156.project.metadetect.service.errors.ForbiddenException;
import dev.coms4156.project.metadetect.service.errors.NotFoundException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provides CRUD and ownership-validated access to {@link Image} records.
 */
@Service
public class ImageService {

  private final ImageRepository repo;

  public ImageService(ImageRepository repo) {
    this.repo = repo;
  }

  /**
   * Creates a new image record owned by the current user.
   *
   * @param currentUserId the id of the user creating the image
   * @param filename the original filename
   * @param storagePath optional storage location
   * @param labels optional labels
   * @param note optional user-supplied description
   * @return the persisted image
   */
  @Transactional
  public Image create(UUID currentUserId,
                      String filename,
                      String storagePath,
                      String[] labels,
                      String note) {
    Objects.requireNonNull(currentUserId, "currentUserId");
    Objects.requireNonNull(filename, "filename");

    Image toSave = new Image(
        null,
        currentUserId,
        filename,
        storagePath,
        labels,
        note,
        null
    );
    return repo.save(toSave);
  }

  /**
   * Returns an image if and only if the requesting user owns it.
   *
   * @param currentUserId the calling user
   * @param imageId the image to fetch
   * @return the image
   */
  @Transactional(readOnly = true)
  public Image getById(UUID currentUserId, UUID imageId) {
    Image img = repo.findById(imageId)
        .orElseThrow(() -> new NotFoundException("Image not found: " + imageId));
    requireOwner(currentUserId, img);
    return img;
  }

  /**
   * Lists all images belonging to a user in descending upload order.
   *
   * @param currentUserId the owner id
   * @return list of images
   */
  @Transactional(readOnly = true)
  public List<Image> listByOwner(UUID currentUserId) {
    return repo.findAllByUserIdOrderByUploadedAtDesc(currentUserId);
  }

  /**
   * Updates optional metadata on an image if owned by the user.
   *
   * @param currentUserId the calling user
   * @param imageId the image id
   * @param newFilename optional new filename
   * @param newStoragePath optional new path
   * @param newLabels optional new labels
   * @param newNote optional updated note
   * @return the updated image
   */
  @Transactional
  public Image update(UUID currentUserId,
                      UUID imageId,
                      String newFilename,
                      String newStoragePath,
                      String[] newLabels,
                      String newNote) {
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
  }

  /**
   * Deletes an image the user owns.
   *
   * @param currentUserId the calling user
   * @param imageId id of the image to delete
   */
  @Transactional
  public void delete(UUID currentUserId, UUID imageId) {
    Image img = repo.findById(imageId)
        .orElseThrow(() -> new NotFoundException("Image not found: " + imageId));
    requireOwner(currentUserId, img);
    repo.deleteById(imageId);
  }

  private static void requireOwner(UUID userId, Image img) {
    if (!img.getUserId().equals(userId)) {
      throw new ForbiddenException("You do not own this image.");
    }
  }
}
