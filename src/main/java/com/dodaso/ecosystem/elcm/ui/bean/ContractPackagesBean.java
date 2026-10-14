package com.dodaso.ecosystem.elcm.ui.bean;

import com.dodaso.ecosystem.elcm.ui.service.pipeline.ExtractionStatusRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.ExtractionUiService;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.AssignableUserOptionRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.ContractPackageRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.ContractPackageService;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.DocumentRoleOptionRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.PackageDocumentRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.PackageOutcome;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.UploadFilesService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.PrimeFaces;

/**
 * Backing bean for the Contract Packages section of dashboard.xhtml.
 * <p>
 * REVISED 2026-10-07 - Create Document Set. refresh() reloads the table (called by the
 * refreshContractPackages remote command). open() loads the documents of one package into the Open
 * dialog. removeDocument() takes a document out of a draft package; the document goes back to Stage
 * Documents, so the page also refreshes that table (callback parameter "reload"). The rules (draft
 * only) are enforced by elcm-service; the Remove button only mirrors them.
 */
@Named
@ViewScoped
@Getter
@Slf4j
@RequiredArgsConstructor
public class ContractPackagesBean implements Serializable {

  private final ContractPackageService contractPackageService;
  private final UploadFilesService uploadFilesService;
  private final ExtractionUiService extractionUiService;

  private List<ContractPackageRow> contractPackages;

  /**
   * Package shown in the Open dialog.
   */
  private ContractPackageRow openPackage;

  /**
   * Why a reviewer sent the open package back to Assembly; null when it was not declined.
   */
  private String openDeclineNotice;

  /**
   * The roles a document can be given, for the dropdown in the Open dialog (without the "not defined
   * yet" entry, which the page adds).
   */
  private List<DocumentRoleOptionRow> roleOptions = new ArrayList<>();

  /**
   * Documents of the package shown in the Open dialog.
   */
  private List<PackageDocumentRow> openDocuments;

  /**
   * Package whose Reassign icon was clicked; shown in the Reassign dialog.
   */
  private ContractPackageRow reassignPackage;

  /**
   * Login id picked in the Reassign dialog.
   */
  @Setter
  private String reassignLoginId;

  private List<AssignableUserOptionRow> assignableUsers = new ArrayList<>();

  /**
   * Package picked for Submit or Unsubmit, with the mode, for the confirmation dialog.
   */
  private ContractPackageRow submitPackage;
  private boolean unsubmitMode;

  /**
   * Shown in the submit dialog when some documents have no extraction template; null when all are
   * covered.
   */
  private String submitWarning;

  /**
   * True when the package being submitted was declined before: the dialog then asks what was
   * corrected.
   */
  private boolean resubmit;

  /**
   * What the submitter corrected; sent with a resubmission.
   */
  @Setter
  private String correctionNote;

  /**
   * Finer progress for packages past Assembly, by package id: Queued, Extracting, Ready for review,
   * and so on.
   */
  private final java.util.Map<Long, String> stageByPackage = new java.util.HashMap<>();

  @PostConstruct
  void init() {
    reload();
  }

  public void refresh() {
    reload();
  }

  private void reload() {
    contractPackages = contractPackageService.findPackages();
    stageByPackage.clear();
    if (contractPackages == null) {
      return;
    }
    for (final ContractPackageRow row : contractPackages) {
      if ("VALIDATED".equals(row.getStatusCode())) {
        final ExtractionStatusRow ex = extractionUiService.status(row.getId());
        if (ex != null && ex.getState() != null) {
          stageByPackage.put(row.getId(), stageLabel(ex.getState()));
        }
      }
    }
  }

  private static String stageLabel(final String state) {
    return switch (state) {
      case "QUEUED" -> "Queued";
      case "RUNNING" -> "Extracting";
      case "DONE" -> "Ready for review";
      case "FAILED" -> "Extraction failed";
      case "REVIEWED" -> "Awaiting approval";
      default -> null;
    };
  }

  /**
   * True while any package is still queued or extracting, so the list should keep refreshing by
   * itself.
   */
  public boolean isHasActiveStage() {
    for (final String stage : stageByPackage.values()) {
      if ("Queued".equals(stage) || "Extracting".equals(stage)) {
        return true;
      }
    }
    return false;
  }

  /**
   * What the Status column shows: the finer stage while Validated, else the package status.
   */
  public String statusText(final ContractPackageRow row) {
    final String stage = row == null ? null : stageByPackage.get(row.getId());
    return stage != null ? row.getStatus() + " - " + stage : row == null ? "" : row.getStatus();
  }

  /**
   * Tag colour: green when finished or ready, amber while working, red when failed, blue
   * otherwise.
   */
  public String statusSeverity(final ContractPackageRow row) {
    if (row == null) {
      return "info";
    }
    final String stage = stageByPackage.get(row.getId());
    if (stage != null) {
      return switch (stage) {
        case "Extraction failed" -> "danger";
        case "Queued", "Extracting", "Awaiting approval" -> "warn";
        default -> "info";
      };
    }
    return "VALIDATED".equals(row.getStatusCode()) ? "info" : "success";
  }

  /**
   * Open clicked: load the package's documents. The dialog is shown by the page.
   */
  public void open(final ContractPackageRow row) {
    openPackage = row;
    openDocuments =
        row != null ? contractPackageService.findPackageDocuments(row.getId()) : List.of();
    openDeclineNotice = null;
    roleOptions = new ArrayList<>();
    for (final DocumentRoleOptionRow option : contractPackageService.findDocumentRoles()) {
      if (!"UNDEFINED".equals(option.getCode())) {
        roleOptions.add(option);
      }
    }
    if (row != null && "ASSEMBLY".equals(row.getStatusCode())) {
      final ExtractionStatusRow ex = extractionUiService.status(row.getId());
      if (ex != null && ex.isDeclined()) {
        openDeclineNotice = ex.getMessage();
      }
    }
  }

  /**
   * True while the open package can still be classified: before it is completed (applied).
   */
  public boolean isOpenPackageClassifiable() {
    return openPackage != null && !"COMPLETED".equals(openPackage.getStatusCode());
  }

  /**
   * The role dropdown of a document in the Open dialog changed: the row already holds the new code;
   * save it, then refresh the package list so the roles count follows.
   */
  public void saveRole(final PackageDocumentRow doc) {
    if (openPackage == null || doc == null) {
      return;
    }
    final PackageOutcome outcome = contractPackageService.setDocumentRole(openPackage.getId(),
        doc.getStagedDocumentId(), doc.getRoleCode());
    switch (outcome) {
      case OK -> {
        // nothing to say; the dropdown shows the new value
      }
      case CONFLICT -> addMessage(FacesMessage.SEVERITY_WARN, "Cannot change the role",
          "The package is completed.");
      case INVALID -> addMessage(FacesMessage.SEVERITY_WARN, "Role not accepted",
          "That role is not available.");
      case NOT_FOUND -> addMessage(FacesMessage.SEVERITY_WARN, "Not available",
          "The package or the document no longer exists.");
      case FORBIDDEN -> addMessage(FacesMessage.SEVERITY_WARN, "No permission",
          "Setting a role needs the submitter or reviewer role.");
      default -> addMessage(FacesMessage.SEVERITY_ERROR, "Role not saved", "Please try again.");
    }
    reload();
    if (outcome != PackageOutcome.OK) {
      openDocuments = contractPackageService.findPackageDocuments(openPackage.getId());
    }
  }

  /**
   * True while the open package is still a draft, so documents can be removed.
   */
  public boolean isOpenPackageDraft() {
    return openPackage != null && "ASSEMBLY".equals(openPackage.getStatusCode());
  }

  /**
   * A package is ready to submit: a draft with documents and an assignee. Roles are optional here
   * (changed 2026-10-14); they must be set before the review is completed.
   */
  public boolean isReady(final ContractPackageRow row) {
    return row != null && "ASSEMBLY".equals(row.getStatusCode()) && row.getDocCount() > 0
        && row.getAssigneeLoginId() != null && !row.getAssigneeLoginId().isBlank();
  }

  /**
   * Tooltip for the submit icon: the action, or what is still missing.
   */
  public String submitHint(final ContractPackageRow row) {
    if (row == null) {
      return "";
    }
    if (row.getDocCount() <= 0) {
      return "Add at least one document before submitting";
    }
    if (row.getAssigneeLoginId() == null || row.getAssigneeLoginId().isBlank()) {
      return "Assign the package to someone before submitting";
    }
    return "Submit for Extraction";
  }

  public void prepareSubmit(final ContractPackageRow row, final boolean unsubmit) {
    submitPackage = row;
    unsubmitMode = unsubmit;
    submitWarning = null;
    resubmit = false;
    correctionNote = null;
    if (!unsubmit && row != null) {
      final ExtractionStatusRow earlier = extractionUiService.status(row.getId());
      resubmit = earlier != null && earlier.isDeclined();
      final List<String> names = extractionUiService.documentsWithoutTemplate(row.getId());
      if (!names.isEmpty()) {
        submitWarning = "No extraction template exists for " + String.join(", ", names)
            + ". Those documents will not be read; the others will.";
      }
    }
  }

  /**
   * Confirm clicked in the submit or unsubmit dialog. The dialog closes and the table is reloaded,
   * except when a resubmission has no correction note: the dialog stays open (callback "keep").
   */
  public void confirmSubmit() {
    final ContractPackageRow row = submitPackage;
    if (row == null) {
      return;
    }
    if (!unsubmitMode && resubmit
        && (correctionNote == null || correctionNote.isBlank())) {
      addMessage(FacesMessage.SEVERITY_WARN, "Correction note needed",
          "Tell the reviewer what you corrected since the decline.");
      PrimeFaces.current().ajax().addCallbackParam("keep", true);
      return;
    }
    final PackageOutcome outcome = unsubmitMode
        ? contractPackageService.unsubmit(row.getId())
        : contractPackageService.submit(row.getId(), correctionNote);
    final String code = row.getPackageCode();
    switch (outcome) {
      case OK -> addMessage(FacesMessage.SEVERITY_INFO,
          unsubmitMode ? "Package unsubmitted" : "Package submitted",
          unsubmitMode ? code + " is a draft again." : code + " was submitted for extraction.");
      case NOT_FOUND ->
          addMessage(FacesMessage.SEVERITY_WARN, "Not available", code + " no longer exists.");
      case CONFLICT -> addMessage(FacesMessage.SEVERITY_WARN,
          unsubmitMode ? "Cannot unsubmit" : "Cannot submit",
          unsubmitMode ? "The submission was already picked up or the package is not submitted."
              : "Only a draft package can be submitted.");
      case INVALID -> addMessage(FacesMessage.SEVERITY_WARN, "Not ready",
          "A package needs at least one document and an assignee, and a resubmission needs a correction note of at most 1000 characters.");
      default -> addMessage(FacesMessage.SEVERITY_ERROR,
          unsubmitMode ? "Unsubmit failed" : "Submit failed", "Please try again.");
    }
    refresh();
    submitPackage = null;
    correctionNote = null;
    resubmit = false;
    PrimeFaces.current().ajax().addCallbackParam("reload", true);
  }

  /**
   * Reassign clicked: remember the package and load the people list. The dialog is shown by the
   * page.
   */
  public void prepareReassign(final ContractPackageRow row) {
    reassignPackage = row;
    reassignLoginId = null;
    try {
      assignableUsers = uploadFilesService.findAssignableUsers();
    } catch (final Exception e) {
      log.error("Failed to load assignable users for the Reassign dialog", e);
      assignableUsers = new ArrayList<>();
    }
  }

  /**
   * Same in-memory filter as the Upload Files dialog: name or login id; blank returns everyone.
   */
  public List<AssignableUserOptionRow> completeAssignableUsers(final String query) {
    if (assignableUsers == null || assignableUsers.isEmpty()) {
      return List.of();
    }
    if (query == null || query.isBlank()) {
      return assignableUsers;
    }
    final String needle = query.trim().toLowerCase();
    return assignableUsers.stream()
        .filter(
            u -> (u.getDisplayName() != null && u.getDisplayName().toLowerCase().contains(needle))
                || (u.getLoginId() != null && u.getLoginId().toLowerCase().contains(needle)))
        .collect(Collectors.toList());
  }

  /**
   * Reassign confirmed. Callback parameter "done" is false when the dialog should stay open.
   */
  public void confirmReassign() {
    final ContractPackageRow row = reassignPackage;
    if (row == null) {
      return;
    }
    if (reassignLoginId == null || reassignLoginId.isBlank()) {
      addMessage(FacesMessage.SEVERITY_WARN, "Choose a person",
          "Select who the package is assigned to.");
      PrimeFaces.current().ajax().addCallbackParam("done", false);
      return;
    }
    final PackageOutcome outcome = contractPackageService.reassign(row.getId(), reassignLoginId);
    boolean done = true;
    switch (outcome) {
      case OK -> addMessage(FacesMessage.SEVERITY_INFO, "Package reassigned", row.getPackageCode());
      case NOT_FOUND -> addMessage(FacesMessage.SEVERITY_WARN, "Not available",
          row.getPackageCode() + " no longer exists.");
      case CONFLICT -> addMessage(FacesMessage.SEVERITY_WARN, "Cannot reassign",
          "Only a draft package can be reassigned.");
      case INVALID -> {
        addMessage(FacesMessage.SEVERITY_WARN, "Choose a person", "The assignee was not accepted.");
        done = false;
      }
      default -> {
        addMessage(FacesMessage.SEVERITY_ERROR, "Reassign failed", "Please try again.");
        done = false;
      }
    }
    if (done) {
      refresh();
      reassignPackage = null;
      reassignLoginId = null;
    }
    PrimeFaces.current().ajax().addCallbackParam("done", done);
  }

  public void removeDocument(final Long stagedDocumentId, final String fileName) {
    if (openPackage == null || stagedDocumentId == null) {
      return;
    }
    final PackageOutcome outcome = contractPackageService.removeDocument(openPackage.getId(),
        stagedDocumentId);
    boolean reload = true;
    switch (outcome) {
      case OK -> addMessage(FacesMessage.SEVERITY_INFO, "Document removed",
          fileName + " is back in Stage Documents.");
      case NOT_FOUND -> addMessage(FacesMessage.SEVERITY_INFO, "Already removed",
          fileName + " is no longer in this package.");
      case CONFLICT -> addMessage(FacesMessage.SEVERITY_WARN, "Cannot remove",
          "Documents can only be removed while the package is a draft.");
      default -> {
        addMessage(FacesMessage.SEVERITY_ERROR, "Remove failed",
            "The document could not be removed. Please try again.");
        reload = false;
      }
    }
    if (reload) {
      refresh();
      openDocuments = contractPackageService.findPackageDocuments(openPackage.getId());
      for (final ContractPackageRow row : contractPackages) {
        if (row.getId() != null && row.getId().equals(openPackage.getId())) {
          openPackage = row;
          break;
        }
      }
    }
    PrimeFaces.current().ajax().addCallbackParam("reload", reload);
  }

  private void addMessage(final FacesMessage.Severity severity, final String summary,
      final String detail) {
    FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severity, summary, detail));
  }
}