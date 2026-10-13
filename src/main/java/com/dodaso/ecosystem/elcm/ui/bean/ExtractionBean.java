package com.dodaso.ecosystem.elcm.ui.bean;

import com.dodaso.ecosystem.elcm.ui.service.pipeline.ContractPackageRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.ApplyResultRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.ExtractionResultsRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.ExtractionStatusRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.ExtractionUiService;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.FieldHistoryRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.PackageOutcome;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.TargetCandidateRow;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.primefaces.PrimeFaces;

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

    private static final String NO_REVIEW_PERMISSION = "Reviewing extracted values needs the review permission (Reviewer role).";

    private final ExtractionUiService extractionUiService;

    private ContractPackageRow extractionPackage;
    private ExtractionStatusRow status;
    private ExtractionResultsRow results;
    private boolean loadFailed;

    /** History of the field whose history icon was clicked; null when it could not be loaded. */
    private FieldHistoryRow history;

    /** Apply dialog: the record chosen for a package that has no target, and the records to choose from. */
    @Setter
    private Long applyTargetId;
    private List<TargetCandidateRow> applyCandidates = new ArrayList<>();

    /** Reason typed in the Decline dialog. */
    @Setter
    private String declineReason;

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

    /** True while the reviewer can still change values: extraction done, review not completed. */
    public boolean isEditable() {
        return status != null && status.isDone() && results != null;
    }

    /** History icon clicked: load what reviewers did to this field. The page shows the dialog. */
    public void openHistory(final ExtractionResultsRow.FieldRow field) {
        history = null;
        if (extractionPackage == null || field == null) {
            return;
        }
        history = extractionUiService.history(extractionPackage.getId(), field.getResultId());
        if (history == null) {
            add(FacesMessage.SEVERITY_ERROR, "History not available", "Please try again.");
        }
    }

    /** True when the open package has no target contract record and the reviewer must choose one. */
    public boolean isApplyNeedsTarget() {
        return extractionPackage != null && "Unassigned".equals(extractionPackage.getTargetRecord());
    }

    /** Apply clicked: load the records to choose from when the package has no target. The page shows the dialog. */
    public void prepareApply() {
        applyTargetId = null;
        applyCandidates = new ArrayList<>();
        if (extractionPackage == null || !isApplyNeedsTarget()) {
            return;
        }
        final List<TargetCandidateRow> rows = extractionUiService.targetCandidates(extractionPackage.getId());
        if (rows == null) {
            add(FacesMessage.SEVERITY_ERROR, "Records not available", "Please try again.");
        } else {
            applyCandidates = rows;
        }
    }

    /** Apply to contract: write the reviewed values to the package's target contract record. */
    public void applyToContract() {
        if (extractionPackage == null) {
            return;
        }
        final boolean chose = isApplyNeedsTarget() && applyTargetId != null;
        final ApplyResultRow row = extractionUiService.apply(extractionPackage.getId(), chose ? applyTargetId : null);
        switch (row.getOutcome()) {
            case OK -> {
                final StringBuilder detail = new StringBuilder();
                detail.append(row.getWritten()).append(" value(s) written to ").append(row.getRecordCode());
                if (row.getUnchanged() > 0) {
                    detail.append(", ").append(row.getUnchanged()).append(" already up to date");
                }
                detail.append('.');
                if ("CREATED".equals(row.getCounterpartyAction())) {
                    detail.append(" Counterparty ").append(row.getCounterparty()).append(" was created and linked.");
                } else if ("LINKED".equals(row.getCounterpartyAction())) {
                    detail.append(" Counterparty ").append(row.getCounterparty()).append(" was linked.");
                }
                add(FacesMessage.SEVERITY_INFO, "Applied to contract record", detail.toString());
                if (!row.getSkipped().isEmpty()) {
                    add(FacesMessage.SEVERITY_WARN, "Not applied", String.join("; ", row.getSkipped()));
                }
            }
            case WORKSPACE_MISMATCH -> add(FacesMessage.SEVERITY_WARN, "No contract record",
                chose ? "The chosen contract record cannot be used (another workspace, or archived or superseded)."
                    : "This package has no contract record. Choose one in the Apply dialog.");
            case CONFLICT -> add(FacesMessage.SEVERITY_WARN, "Cannot apply",
                "The review is not completed, or the values were already applied.");
            case NOT_FOUND -> add(FacesMessage.SEVERITY_WARN, "Not available", "The package has no submission.");
            case FORBIDDEN -> add(FacesMessage.SEVERITY_WARN, "No permission", NO_REVIEW_PERMISSION);
            default -> add(FacesMessage.SEVERITY_ERROR, "Apply failed", "Please try again.");
        }
        results = null;
        load();
    }

    /** Decline is possible while the values wait for review, or after a failed extraction. */
    public boolean isDeclinable() {
        return status != null && (status.isDone() || status.isFailed());
    }

    /** Decline clicked: start with an empty reason. The page shows the dialog. */
    public void prepareDecline() {
        declineReason = null;
    }

    /**
     * Decline confirmed. Callback parameter "done" is true when the package went back to
     * Assembly; on a refusal the dialog stays open so the reviewer can fix the reason.
     */
    public void confirmDecline() {
        if (extractionPackage == null) {
            return;
        }
        final String reason = declineReason == null ? "" : declineReason.trim();
        if (reason.isEmpty()) {
            add(FacesMessage.SEVERITY_WARN, "Reason needed", "Tell the submitter why the package is sent back.");
            PrimeFaces.current().ajax().addCallbackParam("done", false);
            return;
        }
        final PackageOutcome outcome = extractionUiService.decline(extractionPackage.getId(), reason);
        boolean done = false;
        switch (outcome) {
            case OK -> {
                done = true;
                add(FacesMessage.SEVERITY_INFO, "Package declined",
                    extractionPackage.getPackageCode() + " is back in Assembly.");
            }
            case INVALID -> add(FacesMessage.SEVERITY_WARN, "Reason not accepted",
                "The reason is required and at most 1000 characters.");
            case CONFLICT -> {
                add(FacesMessage.SEVERITY_WARN, "Cannot decline",
                    "The package is not waiting for review (anymore).");
                done = true;
            }
            case NOT_FOUND -> add(FacesMessage.SEVERITY_WARN, "Not available", "The package has no submission.");
            case FORBIDDEN -> add(FacesMessage.SEVERITY_WARN, "No permission", NO_REVIEW_PERMISSION);
            default -> add(FacesMessage.SEVERITY_ERROR, "Decline failed", "Please try again.");
        }
        if (done) {
            declineReason = null;
            results = null;
            load();
        }
        PrimeFaces.current().ajax().addCallbackParam("done", done);
    }

    /** Fields that must be confirmed before the review can be completed: critical ones and ones marked Review. */
    public int getOpenCount() {
        if (results == null) {
            return 0;
        }
        int open = 0;
        for (final ExtractionResultsRow.DocumentResult doc : results.getDocuments()) {
            for (final ExtractionResultsRow.FieldRow f : doc.getFields()) {
                if ((f.isCritical() || f.isNeedsReview()) && !f.isVerified()) {
                    open++;
                }
            }
        }
        return open;
    }

    /** Called when the reviewer edits a value or ticks Confirmed. On a refusal the saved values are reloaded. */
    public void saveField(final ExtractionResultsRow.FieldRow field) {
        if (extractionPackage == null || field == null) {
            return;
        }
        // elcm-service strips $ , and spaces from numbers; mirror it so the box shows what was stored.
        final String typed = field.getCorrectedValue();
        final String cleaned = cleanNumber(field.getDataType(), typed);
        final boolean reformatted = !java.util.Objects.equals(typed, cleaned);
        if (reformatted) {
            field.setCorrectedValue(cleaned);
        }
        final PackageOutcome outcome = extractionUiService.saveField(extractionPackage.getId(), field);
        boolean reload = true;
        switch (outcome) {
            case OK -> reload = reformatted;
            case INVALID -> add(FacesMessage.SEVERITY_WARN, "Value not accepted",
                "Numbers must be plain (15500 or 3.5), dates yyyy-mm-dd, yes/no fields true or false.");
            case CONFLICT -> add(FacesMessage.SEVERITY_WARN, "Review closed",
                "The package can no longer be reviewed.");
            case FORBIDDEN -> add(FacesMessage.SEVERITY_WARN, "No permission", NO_REVIEW_PERMISSION);
            default -> add(FacesMessage.SEVERITY_ERROR, "Save failed", "Please try again.");
        }
        if (reload) {
            results = null;
            load();
        }
        PrimeFaces.current().ajax().addCallbackParam("reload", reload);
    }

    /** Same rule as ReviewRules.normalize in elcm-service: numbers lose $ , spaces and USD. */
    private static String cleanNumber(final String dataType, final String value) {
        if (!"NUMBER".equals(dataType) || value == null) {
            return value;
        }
        final String cleaned = value.trim().replaceAll("(?i)usd", "").replaceAll("[$,\\s]", "");
        return cleaned.isEmpty() ? value : cleaned;
    }

    /** Confirms every field the extractor verified, so the reviewer only looks at the rest. */
    public void confirmAll() {
        if (extractionPackage == null) {
            return;
        }
        final int confirmed = extractionUiService.confirmVerified(extractionPackage.getId());
        if (confirmed == ExtractionUiService.CONFIRM_FORBIDDEN) {
            add(FacesMessage.SEVERITY_WARN, "No permission", NO_REVIEW_PERMISSION);
        } else if (confirmed < 0) {
            add(FacesMessage.SEVERITY_ERROR, "Confirm failed", "Please try again.");
        } else {
            add(FacesMessage.SEVERITY_INFO, "Fields confirmed", confirmed + " field(s) the extractor verified were confirmed.");
        }
        results = null;
        load();
    }

    public void completeReview() {
        if (extractionPackage == null) {
            return;
        }
        final PackageOutcome outcome = extractionUiService.completeReview(extractionPackage.getId());
        switch (outcome) {
            case OK -> add(FacesMessage.SEVERITY_INFO, "Review completed", extractionPackage.getPackageCode());
            case WORKSPACE_MISMATCH -> add(FacesMessage.SEVERITY_WARN, "Not ready",
                "Confirm every critical field and every field marked Review first.");
            case CONFLICT -> add(FacesMessage.SEVERITY_WARN, "Cannot complete",
                "The review was already completed or the extraction is not finished.");
            case NOT_FOUND -> add(FacesMessage.SEVERITY_WARN, "Not available", "The package has no submission.");
            case FORBIDDEN -> add(FacesMessage.SEVERITY_WARN, "No permission", NO_REVIEW_PERMISSION);
            default -> add(FacesMessage.SEVERITY_ERROR, "Complete failed", "Please try again.");
        }
        results = null;
        load();
    }

    /** Dialog closed: forget everything so the next open starts clean. */
    public void close() {
        extractionPackage = null;
        status = null;
        results = null;
        history = null;
        loadFailed = false;
    }

    private void load() {
        final ExtractionStatusRow fresh = extractionUiService.status(extractionPackage.getId());
        loadFailed = fresh == null;
        if (fresh != null) {
            status = fresh;
            if ((fresh.isDone() || fresh.isReviewed() || fresh.isApplied()) && results == null) {
                results = extractionUiService.results(extractionPackage.getId());
            }
        }
    }

    private static void add(final FacesMessage.Severity severity, final String summary, final String detail) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severity, summary, detail));
    }
}
