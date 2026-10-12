package com.dodaso.ecosystem.elcm.ui.bean;

import com.dodaso.ecosystem.elcm.ui.service.pipeline.ContractPackageRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.ExtractionResultsRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.ExtractionStatusRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.ExtractionUiService;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.PackageOutcome;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Backing bean for the Extraction dialog on dashboard.xhtml (ADDED 2026-10-11).
 *
 * Extraction runs in the background and can take minutes, so nobody waits on a request: the
 * dialog opens on the current progress and polls poll() every few seconds while the state is
 * QUEUED or RUNNING. When the state turns DONE the results are loaded once and the polling stops.
 */
@Named
@ViewScoped
@Getter
@RequiredArgsConstructor
public class ExtractionBean implements Serializable {

    private final ExtractionUiService extractionUiService;

    private ContractPackageRow extractionPackage;
    private ExtractionStatusRow status;
    private ExtractionResultsRow results;
    private boolean loadFailed;

    /** Extraction icon clicked: load the progress. The page shows the dialog and starts polling. */
    public void open(final ContractPackageRow row) {
        extractionPackage = row;
        status = null;
        results = null;
        load();
    }

    /** Called by p:poll. Cheap when nothing changed. */
    public void poll() {
        if (extractionPackage != null) {
            load();
        }
    }

    /** True while the dialog should keep polling. */
    public boolean isPolling() {
        return extractionPackage != null && (status == null ? !loadFailed : status.isActive());
    }

    public void retry() {
        if (extractionPackage == null) {
            return;
        }
        final PackageOutcome outcome = extractionUiService.retry(extractionPackage.getId());
        switch (outcome) {
            case OK -> add(FacesMessage.SEVERITY_INFO, "Extraction restarted",
                "Only the documents that failed will run again.");
            case CONFLICT -> add(FacesMessage.SEVERITY_WARN, "Cannot retry", "The extraction is not in a failed state.");
            case NOT_FOUND -> add(FacesMessage.SEVERITY_WARN, "Not available", "The package has no submission.");
            default -> add(FacesMessage.SEVERITY_ERROR, "Retry failed", "Please try again.");
        }
        results = null;
        load();
    }

    /** Dialog closed: forget everything so the next open starts clean. */
    public void close() {
        extractionPackage = null;
        status = null;
        results = null;
        loadFailed = false;
    }

    private void load() {
        final ExtractionStatusRow fresh = extractionUiService.status(extractionPackage.getId());
        loadFailed = fresh == null;
        if (fresh != null) {
            status = fresh;
            if (fresh.isDone() && results == null) {
                results = extractionUiService.results(extractionPackage.getId());
            }
        }
    }

    private static void add(final FacesMessage.Severity severity, final String summary, final String detail) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severity, summary, detail));
    }
}
