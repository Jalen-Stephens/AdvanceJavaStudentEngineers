package dev.coms4156.project.metadetect.repository;

import dev.coms4156.project.metadetect.model.Image;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link Image} entities.
 * Provides ownership-scoped access patterns so callers can enforce user-level
 * isolation (either via service checks or Postgres RLS).
 * Storage path and binary data are not persisted here; this repository controls
 * metadata only. Binary objects live in Supabase Storage and are referenced by
 * `storage_path`.
 */
@Repository
public interface ImageRepository extends CrudRepository<Image, UUID> {

  /**
   * Fetches an image by ID only if it belongs to the given user.
   * This prevents cross-tenant reads at the repository layer.
   *
   * @param id     image ID
   * @param userId authenticated owner's ID
   * @return Optional containing the image if authorized
   */
  Optional<Image> findByIdAndUserId(UUID id, UUID userId);

  /**
   * Lists all images belonging to a user, ordered newest-first.
   * Suitable for dashboard/gallery views.
   *
   * @param userId authenticated owner's ID
   * @return ordered list of images for that user
   */
  List<Image> findAllByUserIdOrderByUploadedAtDesc(UUID userId);
}
