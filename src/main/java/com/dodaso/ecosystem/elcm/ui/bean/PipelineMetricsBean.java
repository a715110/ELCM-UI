package com.dodaso.ecosystem.elcm.ui.bean;

import org.springframework.core.ParameterizedTypeReference;

import com.dodaso.ecosystem.baseline.common.constant.ServiceDiscoveryEnum;
import com.dodaso.ecosystem.elcm.ui.constant.PipelineMetricsControllerAPIEnum;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

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
@Getter 
@Setter 
@Slf4j
public class PipelineMetricsBean extends BaseBean {

    //private final PipelineMetricsService pipelineMetricsService;

    private int uploadingCount;
    private int validatingCount;
    private int validCount;
    private int submittedCount;

    @PostConstruct
    void init() throws Exception {
        //PipelineMetricsService.PipelineMetrics metrics = pipelineMetricsService.getMetrics();
        //this.uploadingCount = metrics.uploading();
        //this.validatingCount = metrics.validating();
        //this.validCount = metrics.valid();
        //this.submittedCount = metrics.submitted();
        restServiceClient.get(
          ServiceDiscoveryEnum.elcm_service.getServiceDiscoveryName(),
          PipelineMetricsControllerAPIEnum.getMetrics.getEndPoint(), new ParameterizedTypeReference<>() {
          });
    }

    public int getUploadingCount() { return uploadingCount; }
    public int getValidatingCount() { return validatingCount; }
    public int getValidCount() { return validCount; }
    public int getSubmittedCount() { return submittedCount; }
}