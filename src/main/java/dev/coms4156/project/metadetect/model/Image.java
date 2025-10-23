package dev.coms4156.project.metadetect.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Entity representing a stored image and its associated metadata.
 * Persisted in the Postgres database via JPA.
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

  // Postgres text[] <-> List<String> works well in Spring Data JDBC
  private String[] labels;

  private String note;

  @Column("uploaded_at")
  @ReadOnlyProperty // let DB default timezone('utc', now()) set this
  private OffsetDateTime uploadedAt;

  public Image() {}

  // Getters/setters
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
  // no setter: DB populates it (read-only)
}
