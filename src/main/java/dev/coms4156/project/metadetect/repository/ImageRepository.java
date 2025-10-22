package dev.coms4156.project.metadetect.repository;

import dev.coms4156.project.metadetect.model.Image;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for accessing and querying {@link Image} entities.
 * Provides user-scoped lookup operations to ensure ownership checks
 * can be enforced at the service layer.
 */
@Repository
public interface ImageRepository extends CrudRepository<Image, UUID> {
  Optional<Image> findByIdAndUserId(UUID id, UUID userId);

  List<Image> findAllByUserIdOrderByUploadedAtDesc(UUID userId);
}
