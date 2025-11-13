package dev.coms4156.project.metadetect.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity mapping the `public.analysis_reports` table.
 * Represents a single forensic or authenticity analysis run associated
 * with a previously uploaded image. The database owns temporal defaults
 * (`created_at`), but they are guarded defensively in @PrePersist to
 * ensure consistency even if used outside Hibernate.
 * DB schema (summarized):
 *   id           UUID PK
 *   image_id     UUID NOT NULL (FK -> images.id)
 *   status       report_status NOT NULL DEFAULT 'PENDING'
 *   confidence   DOUBLE PRECISION NULL
 *   details      JSONB NULL
 *   created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
 * Notes:
 *  - `details` is stored as a raw JSON string for portability. It can
 *    be upgraded to `JsonNode` or a custom converter later.
 *  - `status` defaults to PENDING on both the Java and DB side.
 */
@Entity
@Table(name = "analysis_reports", schema = "public")
public class AnalysisReport {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "image_id", nullable = false)
  private UUID imageId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private ReportStatus status = ReportStatus.PENDING;

  @Column(name = "confidence")
  private Double confidence;

  /**
   * Raw JSONB payload. Stored as a String to avoid early binding
   * to a JSON library. Callers must pass well-formed JSON.
   */
  @Column(name = "details", columnDefinition = "jsonb")
  private String details;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /* ---------------------------------------------------------------------- */
  /* Lifecycle hook                                                         */
  /* ---------------------------------------------------------------------- */

  /**
   * Applies default values for fields normally supplied by the database.
   * Ensures the entity is valid when persisted through JPA without relying
   * on a DB-side trigger or default constraint being executed first.
   */
  @PrePersist
  protected void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    if (createdAt == null) {
      createdAt = Instant.now();
    }
    if (status == null) {
      status = ReportStatus.PENDING;
    }
  }

  /* ---------------------------------------------------------------------- */
  /* Constructors                                                           */
  /* ---------------------------------------------------------------------- */

  public AnalysisReport() {
    // Default constructor for JPA
  }

  /**
   * Convenience constructor ensuring the image FK is present.
   */
  public AnalysisReport(UUID imageId) {
    this.imageId = imageId;
    this.status = ReportStatus.PENDING;
  }

  /* ---------------------------------------------------------------------- */
  /* Accessors                                                              */
  /* ---------------------------------------------------------------------- */

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getImageId() {
    return imageId;
  }

  public void setImageId(UUID imageId) {
    this.imageId = imageId;
  }

  public ReportStatus getStatus() {
    return status;
  }

  public void setStatus(ReportStatus status) {
    this.status = status;
  }

  public Double getConfidence() {
    return confidence;
  }

  public void setConfidence(Double confidence) {
    this.confidence = confidence;
  }

  public String getDetails() {
    return details;
  }

  public void setDetails(String details) {
    this.details = details;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  /* ---------------------------------------------------------------------- */
  /* Equality / diagnostic helpers                                          */
  /* ---------------------------------------------------------------------- */

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AnalysisReport that)) {
      return false;
    }
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "AnalysisReport{"
      + "id=" + id
      + ", imageId=" + imageId
      + ", status=" + status
      + ", confidence=" + confidence
      + ", createdAt=" + createdAt
      + '}';
  }

  /* ---------------------------------------------------------------------- */
  /* Enum                                                                   */
  /* ---------------------------------------------------------------------- */

  /**
   * Codes representing the lifecycle of an analysis.
   * Mirrors `report_status` in Postgres, kept in sync statically.
   */
  public enum ReportStatus {
    PENDING,
    DONE,
    FAILED
  }
}
