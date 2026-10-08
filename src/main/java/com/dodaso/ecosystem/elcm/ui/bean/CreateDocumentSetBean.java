package com.dodaso.ecosystem.elcm.ui.bean;

import com.dodaso.ecosystem.elcm.ui.service.pipeline.AssignableUserOptionRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.ContractPackageRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.ContractPackageService;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.DocumentRoleOptionRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.PackageOutcome;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.StagedDocumentRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.UploadFilesService;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.PrimeFaces;

/**
 * Backing bean for the Create Document Set dialog (dashboard.xhtml).
 *
 * prepare() takes the rows ticked in Stage Documents. A package has exactly one workspace, so a
 * selection that spans workspaces is refused here with a message (elcm-service refuses it too).
 * The user either creates a new package (and picks its assignee) or adds the selection to an
 * existing draft package of the same workspace. A role per document is optional; a blank role is
 * stored as "not defined yet".
 *
 * Callback parameters: "open" (prepare: show the dialog) and "done" (submit: close the dialog and
 * refresh the page). On a validation refusal "done" is false and the dialog stays open.
 */
@Named
@ViewScoped
@Getter
@Setter
@Slf4j
@RequiredArgsConstructor
public class CreateDocumentSetBean implements Serializable {

    public static final String MODE_NEW = "NEW";
    public static final String MODE_EXISTING = "EXISTING";

    private final ContractPackageService contractPackageService;
    private final UploadFilesService uploadFilesService;

    private List<StagedDocumentRow> documents = new ArrayList<>();
    private Map<Long, String> roleByDocument = new HashMap<>();
    private List<DocumentRoleOptionRow> roleOptions = new ArrayList<>();
    private List<ContractPackageRow> eligiblePackages = new ArrayList<>();
    private List<AssignableUserOptionRow> assignableUsers = new ArrayList<>();

    private String mode = MODE_NEW;
    private Long existingPackageId;
    private String assigneeLoginId;
    private String workspace;

    /** Opens the dialog for the selected rows, or explains why not. */
    public void prepare(final List<StagedDocumentRow> selected) {
        if (selected == null || selected.isEmpty()) {
            addMessage(FacesMessage.SEVERITY_WARN, "Nothing selected", "Select at least one document first.");
            PrimeFaces.current().ajax().addCallbackParam("open", false);
            return;
        }
        final Set<String> workspaces = selected.stream()
            .map(StagedDocumentRow::getWorkspace)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (workspaces.size() > 1) {
            addMessage(FacesMessage.SEVERITY_WARN, "One workspace per document set",
                "The selected documents belong to different workspaces. Select documents from a single workspace.");
            PrimeFaces.current().ajax().addCallbackParam("open", false);
            return;
        }
        documents = new ArrayList<>(selected);
        workspace = workspaces.iterator().next();
        roleByDocument = new HashMap<>();
        mode = MODE_NEW;
        existingPackageId = null;
        assigneeLoginId = null;
        roleOptions = contractPackageService.findDocumentRoles();
        try {
            assignableUsers = uploadFilesService.findAssignableUsers();
        } catch (final Exception e) {
            log.error("Failed to load assignable users for the Create Document Set dialog", e);
            assignableUsers = new ArrayList<>();
        }
        eligiblePackages = contractPackageService.findPackages().stream()
            .filter(p -> "ASSEMBLY".equals(p.getStatusCode()))
            .filter(p -> workspace != null && workspace.equals(p.getWorkspace()))
            .collect(Collectors.toList());
        PrimeFaces.current().ajax().addCallbackParam("open", true);
    }

    public boolean isExistingMode() {
        return MODE_EXISTING.equals(mode);
    }

    public boolean isExistingAvailable() {
        return !eligiblePackages.isEmpty();
    }

    /** Same in-memory filter as UploadFilesBean: name or login id, case-insensitive; blank returns all. */
    public List<AssignableUserOptionRow> completeAssignableUsers(final String query) {
        if (assignableUsers == null || assignableUsers.isEmpty()) {
            return List.of();
        }
        if (query == null || query.isBlank()) {
            return assignableUsers;
        }
        final String needle = query.trim().toLowerCase();
        return assignableUsers.stream()
            .filter(u -> (u.getDisplayName() != null && u.getDisplayName().toLowerCase().contains(needle))
                || (u.getLoginId() != null && u.getLoginId().toLowerCase().contains(needle)))
            .collect(Collectors.toList());
    }

    public void submit() {
        if (documents == null || documents.isEmpty()) {
            PrimeFaces.current().ajax().addCallbackParam("done", true);
            return;
        }
        final List<Long> ids = documents.stream().map(StagedDocumentRow::getId).collect(Collectors.toList());
        final PackageOutcome outcome;
        if (isExistingMode()) {
            if (existingPackageId == null) {
                addMessage(FacesMessage.SEVERITY_WARN, "Choose a package", "Select the package to add the documents to.");
                PrimeFaces.current().ajax().addCallbackParam("done", false);
                return;
            }
            outcome = contractPackageService.addDocuments(existingPackageId, ids, roleByDocument);
        } else {
            outcome = contractPackageService.createPackage(ids, roleByDocument, blankToNull(assigneeLoginId));
        }
        boolean done = true;
        switch (outcome) {
            case OK -> addMessage(FacesMessage.SEVERITY_INFO, isExistingMode() ? "Documents added" : "Document set created",
                ids.size() + " document(s) grouped.");
            case NOT_FOUND -> addMessage(FacesMessage.SEVERITY_WARN, "Not available",
                "A document or the package no longer exists. The lists were refreshed.");
            case CONFLICT -> addMessage(FacesMessage.SEVERITY_WARN, "Cannot group",
                "A document is already in a package or submitted, or the package is no longer a draft. The lists were refreshed.");
            case WORKSPACE_MISMATCH -> {
                addMessage(FacesMessage.SEVERITY_WARN, "One workspace per document set",
                    "All documents must belong to the same workspace as the package.");
                done = false;
            }
            case INVALID -> {
                addMessage(FacesMessage.SEVERITY_WARN, "Check the selection", "The request was not accepted. Review the roles and try again.");
                done = false;
            }
            default -> {
                addMessage(FacesMessage.SEVERITY_ERROR, "Grouping failed", "The document set could not be saved. Please try again.");
                done = false;
            }
        }
        PrimeFaces.current().ajax().addCallbackParam("done", done);
    }

    private static String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private void addMessage(final FacesMessage.Severity severity, final String summary, final String detail) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severity, summary, detail));
    }
}
