package dev.coms4156.project.metadetect.repo;

import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Repository for performing CRUD operations on the {@code images} table.
 *
 * <p>This class provides data-access methods related to uploaded images,
 * such as inserting new image metadata into the database.</p>
 */
@Repository
public class ImageRepository {
  private final JdbcTemplate jdbc;

  /**
   * Creates a new {@code ImageRepository} with the given JDBC template.
   *
   * @param jdbc the {@link JdbcTemplate} used for executing SQL statements
   */
  public ImageRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Inserts a new image record into the {@code images} table.
   *
   * @param ownerId the ID of the user who uploaded the image
   * @param filename the original filename of the image
   * @param storagePath the storage path or URI of the uploaded image
   * @return the generated {@link UUID} of the new image record
   */
  public UUID insert(UUID ownerId, String filename, String storagePath) {
    return jdbc.queryForObject(
      "insert into images(owner_user_id, filename, storage_path, uploaded_at) "
        + "values (?, ?, ?, ?) returning id",
      (rs, i) -> (UUID) rs.getObject("id"),
      ownerId, filename, storagePath, Instant.now());
  }
}
