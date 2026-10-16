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
 * Backing bean for the metric cards on dashboard.xhtml.
 *
 * <p>REVISED 2026-10-16: five cards that follow the real flow (Staged documents, In assembly,
 * Extracting, Ready for review, Awaiting approval) replace the old Uploading/Validating/Valid/
 * Submitted cards. The first card counts documents, the others count packages. Failed extractions
 * and sent-back packages feed the red badge in the page header.
 *
 * <p>The page binds flat getters, and the small lines under each number are built here so the
 * page needs no logic. The response is read as a generic Map (no DTO binding). The JSON shape is
 * the service's PipelineOverview record, flat. Fails open: on any error the cards stay at 0 and
 * the dashboard still renders.
 */
@Named
@ViewScoped
@Getter
@Setter
@Slf4j
public class PipelineMetricsBean extends BaseBean {

    private int stagedDocuments;
    private int uploadedToday;
    private int inAssembly;
    private int sentBack;
    private int extracting;
    private int queued;
    private int reading;
    private int readyForReview;
    private int fieldsNeedReview;
    private int awaitingApproval;
    private int oldestWaitingDays;
    private int failedExtractions;

    @PostConstruct
    void init() {
        refresh();
    }

    /**
     * Re-reads the counts. Called on page load via init() and again from the dashboard's
     * refreshStagedDocuments remote command after an action that moves documents or packages.
     */
    @Override
    public void refresh() {
        try {
            final Map<String, Object> response = restServiceClient.get(
                ServiceDiscoveryEnum.elcm_service.getServiceDiscoveryName(),
                PipelineMetricsControllerAPIEnum.getMetrics.getEndPoint(),
                new ParameterizedTypeReference<Map<String, Object>>() {
                });
            if (response != null) {
                this.stagedDocuments = toInt(response.get("stagedDocuments"));
                this.uploadedToday = toInt(response.get("uploadedToday"));
                this.inAssembly = toInt(response.get("inAssembly"));
                this.sentBack = toInt(response.get("sentBack"));
                this.extracting = toInt(response.get("extracting"));
                this.queued = toInt(response.get("queued"));
                this.reading = toInt(response.get("reading"));
                this.readyForReview = toInt(response.get("readyForReview"));
                this.fieldsNeedReview = toInt(response.get("fieldsNeedReview"));
                this.awaitingApproval = toInt(response.get("awaitingApproval"));
                this.oldestWaitingDays = toInt(response.get("oldestWaitingDays"));
                this.failedExtractions = toInt(response.get("failedExtractions"));
            }
        } catch (Exception e) {
            log.warn("Could not load pipeline metrics, showing zeros: {}", e.getMessage());
        }
    }

    public String getStagedNote() {
        return "documents, " + uploadedToday + " uploaded today";
    }

    public String getAssemblyNote() {
        return "packages, " + sentBack + " sent back by a reviewer";
    }

    public String getExtractingNote() {
        return "packages, " + queued + " queued, " + reading + " reading";
    }

    public String getReadyNote() {
        return "packages, " + fieldsNeedReview + (fieldsNeedReview == 1 ? " field needs" : " fields need")
            + " review";
    }

    public String getAwaitingNote() {
        if (awaitingApproval == 0) {
            return "packages";
        }
        return "packages, oldest waiting "
            + (oldestWaitingDays == 0 ? "less than a day" : oldestWaitingDays + (oldestWaitingDays == 1 ? " day" : " days"));
    }

    /** True when something needs attention: a failed extraction or a package sent back. */
    public boolean isAlert() {
        return failedExtractions > 0 || sentBack > 0;
    }

    /** Text of the red badge, for example "1 extraction failed, 2 sent back". */
    public String getAlertText() {
        final StringBuilder text = new StringBuilder();
        if (failedExtractions > 0) {
            text.append(failedExtractions).append(failedExtractions == 1 ? " extraction failed" : " extractions failed");
        }
        if (sentBack > 0) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(sentBack).append(" sent back");
        }
        return text.toString();
    }

    private static int toInt(final Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

}
