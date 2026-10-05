package com.dodaso.ecosystem.elcm.ui.bean;

import java.util.Map;

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

    private int uploadingCount;
    private int validatingCount;
    private int validCount;
    private int submittedCount;

    /**
     * Loads the four counts from elcm-service. The response used to be
     * discarded, which is why every card showed 0. It is read as a generic
     * Map rather than PipelineMetricsDTOContainer on purpose: the DTO has no
     * no-arg constructor, so binding to it depends on Jackson's parameter-name
     * support, and a Map keeps this bean independent of that. The JSON shape is
     * {"pipelineMetricsDTO": {"uploading": n, "validating": n, "valid": n,
     * "submitted": n}}. Fails open: on any error the cards stay at 0 and the
     * dashboard still renders.
     */
    @PostConstruct
    void init() {
        try {
            final Map<String, Object> response = restServiceClient.get(
                ServiceDiscoveryEnum.elcm_service.getServiceDiscoveryName(),
                PipelineMetricsControllerAPIEnum.getMetrics.getEndPoint(),
                new ParameterizedTypeReference<Map<String, Object>>() {
                });
            final Object node = response == null ? null : response.get("pipelineMetricsDTO");
            if (node instanceof Map<?, ?> counts) {
                this.uploadingCount = toInt(counts.get("uploading"));
                this.validatingCount = toInt(counts.get("validating"));
                this.validCount = toInt(counts.get("valid"));
                this.submittedCount = toInt(counts.get("submitted"));
            }
        } catch (Exception e) {
            log.warn("Could not load pipeline metrics, showing zeros: {}", e.getMessage());
        }
    }

    private static int toInt(final Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    public int getUploadingCount() { return uploadingCount; }
    public int getValidatingCount() { return validatingCount; }
    public int getValidCount() { return validCount; }
    public int getSubmittedCount() { return submittedCount; }
}