package dev.coms4156.project.metadetect.repository;

import dev.coms4156.project.metadetect.model.AnalysisReport;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for AnalysisReport persistence and lookups.
 * Backed by table: public.analysis_reports
 */
@Repository
public interface AnalysisReportRepository extends JpaRepository<AnalysisReport, UUID> {

  /**
   * Default findById is inherited from JpaRepository, but we expose it with Optional
   * to make intent explicit for callers using Optional semantics.
   */
  @Override
  Optional<AnalysisReport> findById(UUID id);

  /**
   * List all analyses for a given image, newest first.
   */
  List<AnalysisReport> findAllByImageIdOrderByCreatedAtDesc(UUID imageId);

  /**
   * Get the most recent analysis for a given image (if any).
   */
  Optional<AnalysisReport> findTopByImageIdOrderByCreatedAtDesc(UUID imageId);
}
