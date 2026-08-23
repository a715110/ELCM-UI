package com.dodaso.ecosystem.elcm.ui.bean;

import com.dodaso.ecosystem.elcm.ui.service.pipeline.PipelineMetricsService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import lombok.RequiredArgsConstructor;

/**
 * Backing bean for the four top metric cards on dashboard.xhtml
 * (Uploading/Validating/Valid/Submitted). Split out of the old
 * DashboardBean per the bean-per-section refactor.
 *
 * Exposes flat int getters rather than the service's PipelineMetrics
 * record directly -- the page binds #{pipelineMetricsBean.uploadingCount},
 * never a nested #{pipelineMetricsBean.metrics.uploading} path. That keeps
 * the record entirely on the Java side; EL never has to resolve a property
 * on it, so the earlier record-vs-EL accessor issue (see StagedDocumentRow)
 * doesn't apply here even though this service happens to use a record
 * internally.
 */
@Named
@ViewScoped
@RequiredArgsConstructor
public class PipelineMetricsBean implements Serializable {

    private final PipelineMetricsService pipelineMetricsService;

    private int uploadingCount;
    private int validatingCount;
    private int validCount;
    private int submittedCount;

    @PostConstruct
    void init() {
        PipelineMetricsService.PipelineMetrics metrics = pipelineMetricsService.getMetrics();
        this.uploadingCount = metrics.uploading();
        this.validatingCount = metrics.validating();
        this.validCount = metrics.valid();
        this.submittedCount = metrics.submitted();
    }

    public int getUploadingCount() { return uploadingCount; }
    public int getValidatingCount() { return validatingCount; }
    public int getValidCount() { return validCount; }
    public int getSubmittedCount() { return submittedCount; }
}