package dev.coms4156.project.metadetect.model;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;


/**
 * Unit tests for AnalysisReport entity behavior and mapping.
 * Covers lifecycle defaults, accessors, enum branches, and mapping.
 */
class AnalysisReportTest {

  /**
   * Verifies default constructor leaves most fields null.
   * Status should default to PENDING by field initializer.
   */
  @Test
  @DisplayName("Default constructor leaves fields null except status default")
  void defaultConstructor_initialState() {
    AnalysisReport r = new AnalysisReport();
    assertThat(r.getId()).isNull();
    assertThat(r.getImageId()).isNull();
    assertThat(r.getConfidence()).isNull();
    assertThat(r.getDetails()).isNull();
    assertThat(r.getCreatedAt()).isNull();
    assertThat(r.getStatus()).isEqualTo(AnalysisReport.ReportStatus.PENDING);
  }

  /**
   * Verifies convenience constructor sets imageId and PENDING.
   */
  @Test
  @DisplayName("Convenience constructor sets imageId and PENDING status")
  void convenienceConstructor_setsImageIdAndPending() {
    UUID imgId = UUID.randomUUID();
    AnalysisReport r = new AnalysisReport(imgId);
    assertThat(r.getImageId()).isEqualTo(imgId);
    assertThat(r.getStatus()).isEqualTo(AnalysisReport.ReportStatus.PENDING);
  }

  /**
   * Ensures getters and setters round-trip all fields.
   */
  @Test
  @DisplayName("Getters/setters round-trip values including JSON and confidence")
  void gettersSetters_roundTrip() {
    AnalysisReport r = new AnalysisReport();

    UUID id = UUID.randomUUID();
    UUID imageId = UUID.randomUUID();
    AnalysisReport.ReportStatus status = AnalysisReport.ReportStatus.DONE;
    Double confidence = 0.987;
    String details = "{\"score\":0.987,\"model\":\"x\"}";
    Instant createdAt = Instant.parse("2024-01-01T00:00:00Z");

    r.setId(id);
    r.setImageId(imageId);
    r.setStatus(status);
    r.setConfidence(confidence);
    r.setDetails(details);
    r.setCreatedAt(createdAt);

    assertThat(r.getId()).isEqualTo(id);
    assertThat(r.getImageId()).isEqualTo(imageId);
    assertThat(r.getStatus()).isEqualTo(status);
    assertThat(r.getConfidence()).isEqualTo(confidence);
    assertThat(r.getDetails()).isEqualTo(details);
    assertThat(r.getCreatedAt()).isEqualTo(createdAt);
  }

  /**
   * Confirms optional fields accept nulls.
   */
  @Test
  @DisplayName("Confidence and details are nullable")
  void nullableFields() {
    AnalysisReport r = new AnalysisReport();
    r.setConfidence(null);
    r.setDetails(null);
    assertThat(r.getConfidence()).isNull();
    assertThat(r.getDetails()).isNull();
  }

  /**
   * Verifies @PrePersist sets defaults when fields are unset.
   * Calls protected hook directly within same package.
   */
  @Test
  @DisplayName("@PrePersist sets defaults when values are null (id, createdAt, status)")
  void prePersist_setsDefaults_whenNulls() {
    AnalysisReport r = new AnalysisReport();
    r.setStatus(null); // force branch where status is repaired
    r.onCreate();
    assertThat(r.getId()).isNotNull();
    assertThat(r.getCreatedAt()).isNotNull();
    assertThat(r.getStatus()).isEqualTo(AnalysisReport.ReportStatus.PENDING);
  }

  /**
   * Verifies @PrePersist does not overwrite present values.
   */
  @Test
  @DisplayName("@PrePersist does not overwrite already-set values")
  void prePersist_respectsExistingValues() {
    UUID existingId = UUID.randomUUID();
    Instant existingCreated = Instant.parse("2023-05-05T12:34:56Z");
    AnalysisReport.ReportStatus existingStatus = AnalysisReport.ReportStatus.DONE;

    AnalysisReport r = new AnalysisReport();
    r.setId(existingId);
    r.setCreatedAt(existingCreated);
    r.setStatus(existingStatus);

    r.onCreate();

    assertThat(r.getId()).isEqualTo(existingId);
    assertThat(r.getCreatedAt()).isEqualTo(existingCreated);
    assertThat(r.getStatus()).isEqualTo(existingStatus);
  }

  /**
   * Tests equals contract using id semantics.
   * Same id equals, different id not equals, null and other types differ.
   */
  @Test
  @DisplayName("equals is based on id; handles reflexive/symmetric/other-type")
  void equalsContract() {
    UUID id = UUID.randomUUID();

    AnalysisReport a = new AnalysisReport();
    a.setId(id);

    AnalysisReport b = new AnalysisReport();
    b.setId(id);

    AnalysisReport c = new AnalysisReport();
    c.setId(UUID.randomUUID());

    assertThat(a).isEqualTo(a); // reflexive
    assertThat(a).isEqualTo(b); // symmetric same id
    assertThat(b).isEqualTo(a);
    assertThat(a).isNotEqualTo(c); // different id
    assertThat(a).isNotEqualTo(null);
    assertThat(a).isNotEqualTo("not-report");
  }

  /**
   * Documents current behavior: two null ids compare equal.
   * Matches Objects.equals(null, null) in equals implementation.
   */
  @Test
  @DisplayName("equals treats two null-ids as equal (documents current behavior)")
  void equals_nullIds_equalByDesign() {
    AnalysisReport a = new AnalysisReport();
    AnalysisReport b = new AnalysisReport();
    assertThat(a).isEqualTo(b);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
  }

  /**
   * Verifies hashCode derives from id value.
   */
  @Test
  @DisplayName("hashCode derives from id")
  void hashCodeFromId() {
    UUID id = UUID.randomUUID();
    AnalysisReport a = new AnalysisReport();
    a.setId(id);
    AnalysisReport b = new AnalysisReport();
    b.setId(id);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
  }

  /**
   * Ensures toString includes key fields for diagnostics.
   */
  @Test
  @DisplayName("toString contains key fields")
  void toString_includesKeyFields() {
    UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID imgId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    AnalysisReport r = new AnalysisReport(imgId);
    r.setId(id);
    r.setConfidence(0.42);
    r.setCreatedAt(Instant.parse("2024-02-02T00:00:00Z"));

    String s = r.toString();
    assertThat(s).contains("AnalysisReport{");
    assertThat(s).contains(id.toString());
    assertThat(s).contains(imgId.toString());
    assertThat(s).contains("PENDING");
    assertThat(s).contains("0.42");
    assertThat(s).contains("2024-02-02T00:00:00Z");
  }

  /**
   * Verifies entity and table mapping metadata via annotations.
   */
  @Nested
  @DisplayName("JPA mapping annotations")
  class MappingAnnotations {

    /**
     * Confirms entity marker and table schema/name.
     */
    @Test
    @DisplayName("Entity + Table(schema='public', name='analysis_reports')")
    void entityAndTable() {
      Entity entity = AnalysisReport.class.getAnnotation(Entity.class);
      assertThat(entity).isNotNull();

      Table table = AnalysisReport.class.getAnnotation(Table.class);
      assertThat(table).isNotNull();
      assertThat(table.schema()).isEqualTo("public");
      assertThat(table.name()).isEqualTo("analysis_reports");
    }

    /**
     * Verifies enum is stored as STRING and column is non-nullable.
     */
    @Test
    @DisplayName("status is EnumType.STRING and non-nullable")
    void statusEnumMapping() throws Exception {
      Field f = AnalysisReport.class.getDeclaredField("status");
      Enumerated en = f.getAnnotation(Enumerated.class);
      Column col = f.getAnnotation(Column.class);

      assertThat(en).isNotNull();
      assertThat(en.value()).isEqualTo(EnumType.STRING);

      assertThat(col).isNotNull();
      assertThat(col.nullable()).isFalse();
      assertThat(col.name()).isEqualTo("status");
    }

    /**
     * Checks id and imageId column constraints and names.
     */
    @Test
    @DisplayName("id non-nullable, not updatable; imageId non-nullable")
    void idAndImageIdColumns() throws Exception {
      Field id = AnalysisReport.class.getDeclaredField("id");
      Column idCol = id.getAnnotation(Column.class);
      assertThat(idCol).isNotNull();
      assertThat(idCol.nullable()).isFalse();
      assertThat(idCol.updatable()).isFalse();
      assertThat(idCol.name()).isEqualTo("id");

      Field img = AnalysisReport.class.getDeclaredField("imageId");
      Column imgCol = img.getAnnotation(Column.class);
      assertThat(imgCol).isNotNull();
      assertThat(imgCol.nullable()).isFalse();
      assertThat(imgCol.name()).isEqualTo("image_id");
    }

    /**
     * Validates JSONB column and created_at constraints.
     */
    @Test
    @DisplayName("details has jsonb; created_at is non-nullable")
    void detailsAndCreatedAtColumns() throws Exception {
      Field det = AnalysisReport.class.getDeclaredField("details");
      Column detCol = det.getAnnotation(Column.class);
      assertThat(detCol).isNotNull();
      assertThat(detCol.columnDefinition()).isEqualTo("jsonb");
      assertThat(detCol.name()).isEqualTo("details");

      Field created = AnalysisReport.class.getDeclaredField("createdAt");
      Column createdCol = created.getAnnotation(Column.class);
      assertThat(createdCol).isNotNull();
      assertThat(createdCol.nullable()).isFalse();
      assertThat(createdCol.name()).isEqualTo("created_at");
    }
  }

  /**
   * Ensures enum values can be set explicitly.
   */
  @Test
  @DisplayName("Status enum can be set to DONE and FAILED explicitly")
  void statusEnum_values() {
    AnalysisReport r = new AnalysisReport();
    r.setStatus(AnalysisReport.ReportStatus.DONE);
    assertThat(r.getStatus()).isEqualTo(AnalysisReport.ReportStatus.DONE);
    r.setStatus(AnalysisReport.ReportStatus.FAILED);
    assertThat(r.getStatus()).isEqualTo(AnalysisReport.ReportStatus.FAILED);
  }

  /**
   * Confirms null status before persist resets to PENDING.
   */
  @Test
  @DisplayName("Setting status to null before @PrePersist resets to PENDING")
  void statusNull_thenPrePersist() {
    AnalysisReport r = new AnalysisReport();
    r.setStatus(null);
    r.onCreate();
    assertThat(r.getStatus()).isEqualTo(AnalysisReport.ReportStatus.PENDING);
  }
}

