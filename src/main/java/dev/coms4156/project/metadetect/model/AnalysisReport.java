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
 * JPA entity for rows in public.analysis_reports.
 * Matches the current schema:
 *   id UUID PK (default gen_random_uuid())
 *   image_id UUID NOT NULL (FK -> images)
 *   status report_status NOT NULL DEFAULT 'PENDING'
 *   confidence DOUBLE PRECISION NULL
 *   details JSONB NULL
 *   created_at TIMESTAMPTZ NOT NULL DEFAULT now()
 * Notes:
 * - 'details' is stored as a String for portability;
 *   you can switch to JsonNode or a custom converter later.
 * - We set createdAt defensively if absent; DB default will also handle it.
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
   * Stored as JSONB in Postgres. Kept as a raw JSON string in the entity
   * to avoid extra dependencies. Ensure you pass valid JSON content.
   */
  @Column(name = "details", columnDefinition = "jsonb")
  private String details;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /* ------------------------ Lifecycle hooks ------------------------ */

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

  /* ------------------------ Constructors ------------------------ */

  public AnalysisReport() {}

  public AnalysisReport(UUID imageId) {
    this.imageId = imageId;
    this.status = ReportStatus.PENDING;
  }

  /* ------------------------ Getters / Setters ------------------------ */

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

  /* ------------------------ Equality / toString ------------------------ */

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

  /* ------------------------ Enum ------------------------ */

  /**
   * Mirror of public.report_status enum in the database.
   */
  public enum ReportStatus {
    PENDING,
    DONE,
    FAILED
  }
}
