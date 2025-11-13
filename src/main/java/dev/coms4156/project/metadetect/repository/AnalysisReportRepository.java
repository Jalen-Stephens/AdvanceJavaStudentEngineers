package dev.coms4156.project.metadetect.repository;

import dev.coms4156.project.metadetect.model.AnalysisReport;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for `AnalysisReport` entities.
 * Provides convenience queries for fetching analysis history or the most recent
 * entry for a given image. Backed by `public.analysis_reports`.
 */
@Repository
public interface AnalysisReportRepository
    extends JpaRepository<AnalysisReport, UUID> {

  /**
   * Overridden only to make the Optional-returning contract explicit to
   * callers. (JpaRepository already declares this method.)
   *
   * @param id analysis primary key
   * @return Optional containing the report if found
   */
  @Override
  Optional<AnalysisReport> findById(UUID id);

  /**
   * Returns all analyses associated with the given image, ordered newest-first.
   * Useful for audit/history views or UI timelines.
   *
   * @param imageId FK to the `images` table
   * @return ordered list of reports
   */
  List<AnalysisReport> findAllByImageIdOrderByCreatedAtDesc(UUID imageId);

  /**
   * Returns only the most recent analysis for a given image, if one exists.
   * This is typically what callers want when polling or retrieving a summary.
   *
   * @param imageId FK to the `images` table
   * @return Optional containing the most recent analysis
   */
  Optional<AnalysisReport> findTopByImageIdOrderByCreatedAtDesc(UUID imageId);
}
