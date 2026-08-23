package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import org.springframework.stereotype.Service;

/**
 * Aggregate counts for the four top metric cards (Uploading/Validating/
 * Valid/Submitted). Kept as its own service rather than folded into
 * StageDocumentService, since once real persistence exists these will
 * likely be simple COUNT(*) queries (possibly spanning both
 * staged_document and submission), not something that belongs owned by
 * either single-entity service.
 *
 * Mock counts here are intentionally independent of StageDocumentService's
 * and ContractPackageService's mock rows -- they were never derived from
 * those in the original DashboardBean either, just separately hardcoded
 * placeholder numbers. Not something this refactor should quietly "fix" by
 * making them consistent; that's a real-data problem to solve when this
 * becomes a real query.
 */
@Service
public class PipelineMetricsService {

    public PipelineMetrics getMetrics() {
        return new PipelineMetrics(0, 0, 3, 2);
    }

    public record PipelineMetrics(int uploading, int validating, int valid, int submitted) {}
}