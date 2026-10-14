package com.dodaso.ecosystem.elcm.ui.bean;

import com.dodaso.ecosystem.elcm.ui.service.pipeline.ContractPackageRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.ContractPackageService;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.DocumentRoleOptionRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.PackageDocumentRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.ApplyResultRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.ExtractionResultsRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.ExtractionStatusRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.ExtractionUiService;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.FieldHistoryRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.PackageOutcome;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.ResubmissionRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.ReviewAccessRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.SendBackRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.TargetCandidateRow;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.primefaces.PrimeFaces;

/**
 * Backing bean for the Extraction dialog on dashboard.xhtml (ADDED 2026-10-11).
 * <p>
 * Extraction runs in the background and can take minutes, so nobody waits on a request: the dialog
 * opens on the current progress and polls poll() every few seconds while the state is QUEUED or
 * RUNNING. When the state turns DONE the results are loaded once and the polling stops.
 */
@Named
@ViewScoped
@Getter
@RequiredArgsConstructor
public class ExtractionBean implements Serializable {

  private static final int MIN_REASON = 10;
  private static final String NO_REVIEW_PERMISSION = "Reviewing needs the review permission (Reviewer role), and the reviewer must be someone other than the person who submitted the package.";

  private static final String NO_APPROVE_PERMISSION = "Approving needs the Approver role, and the approver must be someone other than the person who submitted or reviewed the package.";

  private final ExtractionUiService extractionUiService;

  private final ContractPackageService contractPackageService;

  private ContractPackageRow extractionPackage;
  private ExtractionStatusRow status;
  private ExtractionResultsRow results;
  private boolean loadFailed;

  /**
   * What the signed-in user may do in this review; null until read or when it could not be read.
   */
  private ReviewAccessRow access;

  /**
   * History of the field whose history icon was clicked; null when it could not be loaded.
   */
  private FieldHistoryRow history;

  /**
   * Apply dialog: the record chosen for a package that has no target, and the records to choose
   * from.
   */
  @Setter
  private Long applyTargetId;
  private List<TargetCandidateRow> applyCandidates = new ArrayList<>();

  /**
   * Role code per document (staged document id), read when the results are loaded. "UNDEFINED" or
   * blank means not set yet. The dropdown under each file name edits this map.
   */
  private Map<Long, String> roleByDocument = new HashMap<>();

  /**
   * The roles a document can be given (without "not defined yet", which the page adds).
   */
  private List<DocumentRoleOptionRow> roleOptions = new ArrayList<>();

  /**
   * The submitter's correction note when the package was submitted again after a decline; null for
   * a first submission or when it could not be read.
   */
  private ResubmissionRow resubmission;

  /**
   * Why the approver sent the review back, newest first; empty when it never was.
   */
  private List<SendBackRow> sendBacks = new ArrayList<>();

  /**
   * Reason typed in the Send back dialog.
   */
  @Setter
  private String sendBackReason;

  /**
   * Reason typed in the Decline dialog.
   */
  @Setter
  private String declineReason;

  /**
   * Reason typed in the "Reason for change" dialog (ADDED 2026-10-14).
   */
  @Setter
  private String changeReason;

  /**
   * Reason typed in the "Re-process" dialog (ADDED 2026-10-14).
   */
  @Setter
  private String reprocessReason;

  /**
   * The document the Re-process dialog is about.
   */
  private Long reprocessDocId;
  private String reprocessFileName;

  /**
   * The field whose new value waits for a reason.
   */
  private ExtractionResultsRow.FieldRow pendingField;

  /**
   * The corrected value of every field as last saved, to tell a real change from a tick.
   */
  private Map<Long, String> savedCorrected = new HashMap<>();

  /**
   * Extraction icon clicked: load the progress. The page shows the dialog and starts polling.
   */
  public void open(final ContractPackageRow row) {
    extractionPackage = row;
    status = null;
    results = null;
    access = null;
    sendBacks = new ArrayList<>();
    resubmission = null;
    roleByDocument = new HashMap<>();
    load();
  }

  /**
   * ADDED 2026-10-14 -- opens the package named by the "review" request parameter, which the Work
   * Queue puts on the link to the Dashboard. The page runs this once on load (a remote command with
   * autoRun) and shows the dialog only when the callback parameter "opened" comes back true.
   */
  public void openFromRequest() {
    final String raw =
        FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap()
            .get("review");
    if (raw == null || raw.isBlank()) {
      return;
    }
    final long id;
    try {
      id = Long.parseLong(raw.trim());
    } catch (final NumberFormatException e) {
      return;
    }
    for (final ContractPackageRow row : contractPackageService.findPackages()) {
      if (row.getId() != null && row.getId() == id) {
        open(row);
        PrimeFaces.current().ajax().addCallbackParam("opened", true);
        return;
      }
    }
    add(FacesMessage.SEVERITY_WARN, "Package not found", "The package may have been returned to draft.");
  }

  /**
   * Called by p:poll. Cheap when nothing changed.
   */
  public void poll() {
    if (extractionPackage != null) {
      load();
    }
  }

  /**
   * True while the dialog should keep polling.
   */
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
      case CONFLICT -> add(FacesMessage.SEVERITY_WARN, "Cannot retry",
          "The extraction is not in a failed state.");
      case NOT_FOUND ->
          add(FacesMessage.SEVERITY_WARN, "Not available", "The package has no submission.");
      default -> add(FacesMessage.SEVERITY_ERROR, "Retry failed", "Please try again.");
    }
    results = null;
    load();
  }

  /**
   * True while the reviewer can still change values: extraction done, review not completed.
   */
  public boolean isEditable() {
    return status != null && status.isDone() && results != null && isCanReview();
  }

  /**
   * May edit, confirm, complete and apply: has the Reviewer role and did not submit the package.
   * True when the access could not be read, since elcm-service enforces the rules anyway.
   */
  public boolean isCanReview() {
    return access == null || access.isCanReview();
  }

  /**
   * Has the review permission (decline, reopen); true when unknown, as above.
   */
  public boolean isPermitted() {
    return access == null || access.isPermitted();
  }

  /**
   * Has the approve permission (Approver role); true when unknown, since the service enforces it.
   */
  public boolean isApprovePermitted() {
    return access == null || access.isApprovePermitted();
  }

  /**
   * May approve and apply or send back: Approver role and not the submitter or the reviewer.
   */
  public boolean isCanApprove() {
    return access == null || access.isCanApprove();
  }

  /**
   * Reviewed, waiting for approval, and this user may approve it; the screen shows Approve and Send
   * back.
   */
  public boolean isApprovalBlocked() {
    return status != null && status.isReviewed() && isApprovePermitted() && !isCanApprove();
  }

  public String getApproveReason() {
    return access == null ? null : access.getApproveReason();
  }

  /**
   * Why the user cannot review, for the note above the values; null when they can.
   */
  public String getAccessReason() {
    return access == null ? null : access.getReason();
  }

  /**
   * The package is ready for review but this user may not do it: show the reason.
   */
  public boolean isReviewBlocked() {
    return status != null && status.isDone() && results != null && !isCanReview();
  }

  /**
   * History icon clicked: load what reviewers did to this field. The page shows the dialog.
   */
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

  /**
   * Fields that have a value but are not confirmed: Apply will not write them.
   */
  public int getApplyUnconfirmedCount() {
    if (results == null) {
      return 0;
    }
    int count = 0;
    for (final ExtractionResultsRow.DocumentResult doc : results.getDocuments()) {
      for (final ExtractionResultsRow.FieldRow f : doc.getFields()) {
        if (!f.isVerified() && !f.getShownValue().isBlank()) {
          count++;
        }
      }
    }
    return count;
  }

  /**
   * True when the open package has no target contract record and the reviewer must choose one.
   */
  public boolean isApplyNeedsTarget() {
    return extractionPackage != null && "Unassigned".equals(extractionPackage.getTargetRecord());
  }

  /**
   * Apply clicked: load the records to choose from when the package has no target. The page shows
   * the dialog.
   */
  public void prepareApply() {
    applyTargetId = null;
    applyCandidates = new ArrayList<>();
    if (extractionPackage == null || !isApplyNeedsTarget()) {
      return;
    }
    final List<TargetCandidateRow> rows = extractionUiService.targetCandidates(
        extractionPackage.getId());
    if (rows == null) {
      add(FacesMessage.SEVERITY_ERROR, "Records not available", "Please try again.");
    } else {
      applyCandidates = rows;
    }
  }

  /**
   * Apply to contract: write the reviewed values to the package's target contract record.
   */
  public void applyToContract() {
    if (extractionPackage == null) {
      return;
    }
    final boolean chose = isApplyNeedsTarget() && applyTargetId != null;
    final ApplyResultRow row = extractionUiService.apply(extractionPackage.getId(),
        chose ? applyTargetId : null);
    switch (row.getOutcome()) {
      case OK -> {
        final StringBuilder detail = new StringBuilder();
        detail.append(row.getWritten()).append(" value(s) written to ").append(row.getRecordCode());
        if (row.getUnchanged() > 0) {
          detail.append(", ").append(row.getUnchanged()).append(" already up to date");
        }
        detail.append('.');
        if ("CREATED".equals(row.getCounterpartyAction())) {
          detail.append(" Counterparty ").append(row.getCounterparty())
              .append(" was created and linked.");
        } else if ("LINKED".equals(row.getCounterpartyAction())) {
          detail.append(" Counterparty ").append(row.getCounterparty()).append(" was linked.");
        }
        add(FacesMessage.SEVERITY_INFO, "Applied to contract record", detail.toString());
        if (!row.getSkipped().isEmpty()) {
          add(FacesMessage.SEVERITY_WARN, "Not applied", String.join("; ", row.getSkipped()));
        }
      }
      case WORKSPACE_MISMATCH -> add(FacesMessage.SEVERITY_WARN, "No contract record",
          chose
              ? "The chosen contract record cannot be used (another workspace, or archived or superseded)."
              : "This package has no contract record. Choose one in the Apply dialog.");
      case CONFLICT -> add(FacesMessage.SEVERITY_WARN, "Cannot apply",
          "The review is not completed, or the values were already applied.");
      case NOT_FOUND ->
          add(FacesMessage.SEVERITY_WARN, "Not available", "The package has no submission.");
      case FORBIDDEN -> add(FacesMessage.SEVERITY_WARN, "No permission", NO_APPROVE_PERMISSION);
      default -> add(FacesMessage.SEVERITY_ERROR, "Apply failed", "Please try again.");
    }
    results = null;
    load();
  }

  /**
   * Decline is possible while the values wait for review, or after a failed extraction.
   */
  public boolean isDeclinable() {
    return status != null && (status.isDone() || status.isFailed());
  }

  /**
   * Decline clicked: start with an empty reason. The page shows the dialog.
   */
  public void prepareDecline() {
    declineReason = null;
  }

  /**
   * Decline confirmed. Callback parameter "done" is true when the package went back to Assembly; on
   * a refusal the dialog stays open so the reviewer can fix the reason.
   */
  public void confirmDecline() {
    if (extractionPackage == null) {
      return;
    }
    final String reason = declineReason == null ? "" : declineReason.trim();
    if (reason.isEmpty()) {
      add(FacesMessage.SEVERITY_WARN, "Reason needed",
          "Tell the submitter why the package is sent back.");
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
      case NOT_FOUND ->
          add(FacesMessage.SEVERITY_WARN, "Not available", "The package has no submission.");
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

  /**
   * Fields that must be confirmed before the review can be completed: critical ones and ones marked
   * Review.
   */
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

  /**
   * Called when the reviewer edits a value or ticks Confirmed. On a refusal the saved values are
   * reloaded.
   */
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
    // CHANGED 2026-10-14: a changed value needs a reason, asked for in a dialog.
    if (valueChanged(field)) {
      pendingField = field;
      changeReason = null;
      PrimeFaces.current().ajax().addCallbackParam("needReason", true);
      PrimeFaces.current().ajax().addCallbackParam("reload", false);
      return;
    }
    store(field, null, reformatted);
  }

  /**
   * Reviewer confirmed the reason for the pending value change.
   */
  public void confirmChange() {
    if (extractionPackage == null || pendingField == null) {
      PrimeFaces.current().ajax().addCallbackParam("done", true);
      return;
    }
    final String reason = changeReason == null ? "" : changeReason.trim();
    if (reason.length() < MIN_REASON) {
      add(FacesMessage.SEVERITY_WARN, "Reason needed",
          "Give a reason of at least " + MIN_REASON + " characters.");
      PrimeFaces.current().ajax().addCallbackParam("done", false);
      return;
    }
    final boolean saved = store(pendingField, reason, true);
    if (saved) {
      pendingField = null;
      changeReason = null;
    }
    PrimeFaces.current().ajax().addCallbackParam("done", true);
  }

  /**
   * Reviewer cancelled the reason dialog: the typed value is dropped.
   */
  public void cancelChange() {
    pendingField = null;
    changeReason = null;
    results = null;
    load();
  }

  /**
   * Saves one field; returns true when elcm-service accepted it.
   */
  private boolean store(final ExtractionResultsRow.FieldRow field, final String reason,
      final boolean forceReload) {
    final PackageOutcome outcome =
        extractionUiService.saveField(extractionPackage.getId(), field, reason);
    boolean reload = true;
    switch (outcome) {
      case OK -> reload = forceReload;
      case INVALID -> add(FacesMessage.SEVERITY_WARN, "Value not accepted",
          "Numbers must be plain (15500 or 3.5), dates yyyy-mm-dd, yes/no fields true or false. "
              + "A changed value also needs a reason of at least " + MIN_REASON + " characters.");
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
    return outcome == PackageOutcome.OK;
  }

  private boolean valueChanged(final ExtractionResultsRow.FieldRow field) {
    return !java.util.Objects.equals(blankToNull(field.getCorrectedValue()),
        blankToNull(savedCorrected.get(field.getResultId())));
  }

  private static String blankToNull(final String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private void rememberSaved() {
    savedCorrected = new HashMap<>();
    if (results == null) {
      return;
    }
    for (final ExtractionResultsRow.DocumentResult doc : results.getDocuments()) {
      for (final ExtractionResultsRow.FieldRow f : doc.getFields()) {
        savedCorrected.put(f.getResultId(), f.getCorrectedValue());
      }
    }
  }

  /**
   * Opens the Re-process dialog for one document.
   */
  public void prepareReprocess(final Long stagedDocumentId, final String fileName) {
    reprocessDocId = stagedDocumentId;
    reprocessFileName = fileName;
    reprocessReason = null;
  }

  /**
   * Reviewer: have the chosen document read again. Callback parameter "done" closes the dialog.
   */
  public void confirmReprocess() {
    if (extractionPackage == null || reprocessDocId == null) {
      PrimeFaces.current().ajax().addCallbackParam("done", true);
      return;
    }
    final String reason = reprocessReason == null ? "" : reprocessReason.trim();
    if (reason.length() < MIN_REASON) {
      add(FacesMessage.SEVERITY_WARN, "Reason needed",
          "Tell what looks wrong, at least " + MIN_REASON + " characters.");
      PrimeFaces.current().ajax().addCallbackParam("done", false);
      return;
    }
    final PackageOutcome outcome =
        extractionUiService.reprocess(extractionPackage.getId(), reprocessDocId, reason);
    boolean done = false;
    switch (outcome) {
      case OK -> {
        done = true;
        add(FacesMessage.SEVERITY_INFO, "Read again",
            reprocessFileName + " is being read again. Your other corrections are kept.");
      }
      case INVALID -> add(FacesMessage.SEVERITY_WARN, "Reason not accepted",
          "The reason needs " + MIN_REASON + " to 500 characters.");
      case CONFLICT -> {
        add(FacesMessage.SEVERITY_WARN, "Cannot re-process",
            "The package can no longer be reviewed.");
        done = true;
      }
      case NOT_FOUND ->
          add(FacesMessage.SEVERITY_WARN, "Not available", "The document has no extraction.");
      case FORBIDDEN -> add(FacesMessage.SEVERITY_WARN, "No permission", NO_REVIEW_PERMISSION);
      default -> add(FacesMessage.SEVERITY_ERROR, "Re-process failed", "Please try again.");
    }
    if (done) {
      reprocessReason = null;
      reprocessDocId = null;
      results = null;
      load();
    }
    PrimeFaces.current().ajax().addCallbackParam("done", done);
  }

  /**
   * Same rule as ReviewRules.normalize in elcm-service: numbers lose $ , spaces and USD.
   */
  private static String cleanNumber(final String dataType, final String value) {
    if (!"NUMBER".equals(dataType) || value == null) {
      return value;
    }
    final String cleaned = value.trim().replaceAll("(?i)usd", "").replaceAll("[$,\\s]", "");
    return cleaned.isEmpty() ? value : cleaned;
  }

  /**
   * Confirms every field the extractor verified, so the reviewer only looks at the rest.
   */
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
      add(FacesMessage.SEVERITY_INFO, "Fields confirmed",
          confirmed + " field(s) the extractor verified were confirmed.");
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
      case OK ->
          add(FacesMessage.SEVERITY_INFO, "Review completed", extractionPackage.getPackageCode());
      case WORKSPACE_MISMATCH -> add(FacesMessage.SEVERITY_WARN, "Not ready",
          "Confirm every critical field and every field marked Review first.");
      case INVALID -> add(FacesMessage.SEVERITY_WARN, "Not ready",
          "Every document needs a role. Set it under the file name.");
      case CONFLICT -> add(FacesMessage.SEVERITY_WARN, "Cannot complete",
          "The review was already completed or the extraction is not finished.");
      case NOT_FOUND ->
          add(FacesMessage.SEVERITY_WARN, "Not available", "The package has no submission.");
      case FORBIDDEN -> add(FacesMessage.SEVERITY_WARN, "No permission", NO_REVIEW_PERMISSION);
      default -> add(FacesMessage.SEVERITY_ERROR, "Complete failed", "Please try again.");
    }
    results = null;
    load();
  }

  /**
   * Send back icon clicked: start with an empty reason. The page shows the dialog.
   */
  public void prepareSendBack() {
    sendBackReason = null;
  }

  /**
   * True when the package was submitted again after a decline, so the banner is shown.
   */
  public boolean isResubmitted() {
    return resubmission != null && resubmission.isResubmitted();
  }

  /**
   * True when the review was sent back at least once.
   */
  public boolean isHasSendBacks() {
    return !sendBacks.isEmpty();
  }

  /**
   * The newest send-back; null when none.
   */
  public SendBackRow getLatestSendBack() {
    return sendBacks.isEmpty() ? null : sendBacks.get(0);
  }

  /**
   * Approver: send a completed review back to the reviewer with a reason. Callback parameter "done"
   * is true when the dialog can close; on a refusal of the reason it stays open.
   */
  public void confirmSendBack() {
    if (extractionPackage == null) {
      return;
    }
    final String reason = sendBackReason == null ? "" : sendBackReason.trim();
    if (reason.isEmpty()) {
      add(FacesMessage.SEVERITY_WARN, "Reason needed",
          "Tell the reviewer what to look at again.");
      PrimeFaces.current().ajax().addCallbackParam("done", false);
      return;
    }
    final PackageOutcome outcome =
        extractionUiService.sendBack(extractionPackage.getId(), reason);
    boolean done = false;
    switch (outcome) {
      case OK -> {
        done = true;
        add(FacesMessage.SEVERITY_INFO, "Sent back to the reviewer",
            "The reviewer sees your reason, can change values and complete the review again.");
      }
      case INVALID -> add(FacesMessage.SEVERITY_WARN, "Reason not accepted",
          "The reason is required and at most 1000 characters.");
      case CONFLICT -> {
        add(FacesMessage.SEVERITY_WARN, "Cannot send back",
            "The review is not waiting for approval, or the values were already applied.");
        done = true;
      }
      case NOT_FOUND ->
          add(FacesMessage.SEVERITY_WARN, "Not available", "The package has no submission.");
      case FORBIDDEN -> add(FacesMessage.SEVERITY_WARN, "No permission", NO_APPROVE_PERMISSION);
      default -> add(FacesMessage.SEVERITY_ERROR, "Send back failed", "Please try again.");
    }
    if (done) {
      sendBackReason = null;
      results = null;
      load();
    }
    PrimeFaces.current().ajax().addCallbackParam("done", done);
  }

  /**
   * Reopen a completed review: the values become editable again.
   */
  public void reopenReview() {
    if (extractionPackage == null) {
      return;
    }
    final PackageOutcome outcome = extractionUiService.reopenReview(extractionPackage.getId());
    switch (outcome) {
      case OK -> add(FacesMessage.SEVERITY_INFO, "Review reopened",
          "You can change values again. Complete the review when you are done.");
      case CONFLICT -> add(FacesMessage.SEVERITY_WARN, "Cannot reopen",
          "The review is not completed, or the values were already applied.");
      case NOT_FOUND ->
          add(FacesMessage.SEVERITY_WARN, "Not available", "The package has no submission.");
      case FORBIDDEN -> add(FacesMessage.SEVERITY_WARN, "No permission", NO_REVIEW_PERMISSION);
      default -> add(FacesMessage.SEVERITY_ERROR, "Reopen failed", "Please try again.");
    }
    results = null;
    load();
  }

  /**
   * Dialog closed: forget everything so the next open starts clean.
   */
  public void close() {
    extractionPackage = null;
    status = null;
    results = null;
    access = null;
    history = null;
    sendBacks = new ArrayList<>();
    resubmission = null;
    roleByDocument = new HashMap<>();
    loadFailed = false;
  }

  private void load() {
    final ExtractionStatusRow fresh = extractionUiService.status(extractionPackage.getId());
    loadFailed = fresh == null;
    if (fresh != null) {
      status = fresh;
      if (access == null && (fresh.isDone() || fresh.isReviewed() || fresh.isFailed())) {
        access = extractionUiService.reviewAccess(extractionPackage.getId());
      }
      if (fresh.isDone() || fresh.isReviewed()) {
        sendBacks = extractionUiService.sendBacks(extractionPackage.getId());
        resubmission = extractionUiService.resubmission(extractionPackage.getId());
      }
      if ((fresh.isDone() || fresh.isReviewed() || fresh.isApplied()) && results == null) {
        results = extractionUiService.results(extractionPackage.getId());
        rememberSaved();
        loadRoles();
      }
    }
  }

  private void loadRoles() {
    roleByDocument = new HashMap<>();
    for (final PackageDocumentRow d :
        contractPackageService.findPackageDocuments(extractionPackage.getId())) {
      roleByDocument.put(d.getStagedDocumentId(), d.getRoleCode());
    }
    roleOptions = new ArrayList<>();
    for (final DocumentRoleOptionRow option : contractPackageService.findDocumentRoles()) {
      if (!"UNDEFINED".equals(option.getCode())) {
        roleOptions.add(option);
      }
    }
  }

  /**
   * Documents of the package that have no role yet; Complete review needs this to be zero.
   */
  public int getUndefinedRoleCount() {
    int count = 0;
    for (final String code : roleByDocument.values()) {
      if (code == null || code.isBlank() || "UNDEFINED".equals(code)) {
        count++;
      }
    }
    return count;
  }

  /**
   * The role dropdown under a file name changed (the map already holds the new code): save it.
   */
  public void saveRole(final Long stagedDocumentId) {
    if (extractionPackage == null || stagedDocumentId == null) {
      return;
    }
    final PackageOutcome outcome = contractPackageService.setDocumentRole(
        extractionPackage.getId(), stagedDocumentId, roleByDocument.get(stagedDocumentId));
    switch (outcome) {
      case OK -> {
        // nothing to say; the dropdown shows the new value
      }
      case CONFLICT -> add(FacesMessage.SEVERITY_WARN, "Cannot change the role",
          "The package is completed.");
      case INVALID ->
          add(FacesMessage.SEVERITY_WARN, "Role not accepted", "That role is not available.");
      case FORBIDDEN -> add(FacesMessage.SEVERITY_WARN, "No permission",
          "Setting a role needs the submitter or reviewer role.");
      case NOT_FOUND -> add(FacesMessage.SEVERITY_WARN, "Not available",
          "The package or the document no longer exists.");
      default -> add(FacesMessage.SEVERITY_ERROR, "Role not saved", "Please try again.");
    }
    if (outcome != PackageOutcome.OK) {
      loadRoles();
    }
  }

  private static void add(final FacesMessage.Severity severity, final String summary,
      final String detail) {
    FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severity, summary, detail));
  }
}