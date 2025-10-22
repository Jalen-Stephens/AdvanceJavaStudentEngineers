package dev.coms4156.project.metadetect.model;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Represents an uploaded image belonging to a user.
 * Each image stores metadata such as filename, optional labels,
 * storage location, and its upload timestamp.
 */
@Table("images")
public class Image {

  @Id
  private UUID id;

  @Column("user_id")
  private UUID userId;

  @Column("filename")
  private String filename;

  @Column("storage_path")
  private String storagePath;

  @Column("labels")
  private String[] labels;

  @Column("note")
  private String note;

  @Column("uploaded_at")
  private Instant uploadedAt;

  public Image() {}

  /**
   * Constructs a new Image record.
   *
   * @param id          unique identifier of the image
   * @param userId      id of the owning user
   * @param filename    original filename from the upload
   * @param storagePath path of the stored file (if present)
   * @param labels      optional labels (Postgres text[])
   * @param note        user note attached to the image
   * @param uploadedAt  timestamp when the image was uploaded
   */
  public Image(UUID id, UUID userId, String filename, String storagePath,
               String[] labels, String note, Instant uploadedAt) {
    this.id = id;
    this.userId = userId;
    this.filename = filename;
    this.storagePath = storagePath;
    this.labels = labels;
    this.note = note;
    this.uploadedAt = uploadedAt;
  }

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

  public Instant getUploadedAt() {
    return uploadedAt;
  }

  public void setUploadedAt(Instant uploadedAt) {
    this.uploadedAt = uploadedAt;
  }
}
