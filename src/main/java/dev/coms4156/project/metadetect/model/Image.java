package dev.coms4156.project.metadetect.model;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Domain entity representing an uploaded image and its metadata.
 * This is a Spring Data JDBC model (not JPA). The row is stored in the
 * `images` table and is used for authorization checks (ownership) as
 * well as metadata queries. The binary object itself lives in remote
 * storage (e.g., Supabase Storage), referenced by `storagePath`.
 * `uploadedAt` is populated by Postgres using its default timestamp,
 * so the field is annotated read-only to prevent accidental overwrite.
 */
@Table("images")
public class Image {

  @Id
  private UUID id;

  @Column("user_id")
  private UUID userId;

  private String filename;

  @Column("storage_path")
  private String storagePath;

  /**
   * Stored as Postgres text[].
   * Spring Data JDBC handles the array conversion automatically.
   */
  private String[] labels;

  private String note;

  /**
   * Timestamp when the image record was inserted.
   * Managed entirely by Postgres (`timezone('utc', now())`).
   */
  @Column("uploaded_at")
  @ReadOnlyProperty
  private OffsetDateTime uploadedAt;

  public Image() {
    // Default constructor for Spring Data
  }

  /* --------------------- Getters / setters --------------------- */

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public String getFilename() {
    return filename;
  }

  public void setFilename(String filename) {
    this.filename = filename;
  }

  public String getStoragePath() {
    return storagePath;
  }

  public void setStoragePath(String storagePath) {
    this.storagePath = storagePath;
  }

  public String[] getLabels() {
    return labels;
  }

  public void setLabels(String[] labels) {
    this.labels = labels;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

  public OffsetDateTime getUploadedAt() {
    return uploadedAt;
  }
  // no setter: populated by DB
}
