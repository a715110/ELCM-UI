package com.dodaso.ecosystem.elcm.ui.bean;

import com.dodaso.ecosystem.elcm.ui.service.pipeline.DeleteOutcome;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.StageDocumentService;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.StagedDocumentRow;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.primefaces.PrimeFaces;

/**
 * Backing bean for the Stage Documents section of dashboard.xhtml. Split
 * out of the old DashboardBean per the bean-per-section refactor -- see
 * that chat discussion for the reasoning.
 *
 * Holds only UI state (the loaded rows, selection) and delegates all actual
 * data access to StageDocumentService. selectedDocuments exists now for the
 * "Review & Group" button's eventual action -- not wired to a real
 * group-into-package operation yet, since that's real business logic this
 * refactor isn't scoped to implement, just to make room for.
 *
 * REVISED 2026-10-04 -- added refresh(). This bean is @ViewScoped and used to
 * load its rows exactly once, in init(), so a document added through the
 * Upload Files dialog never appeared until the page was reloaded (a new view,
 * hence a new bean) -- which is why logging in again "fixed" it. The dialog's
 * commitToPipeline remote command only re-renders the dialog's own form, and
 * UploadFilesBean and this bean are separate beans, so nothing told this one
 * its data was stale. dashboard.xhtml now calls refresh() through a remote
 * command once a submission succeeds.
 *
 * ADDED 2026-10-07 -- soft delete from the trash icon. prepareDelete() remembers the row the user
 * clicked and opens the confirmation dialog (dashboard.xhtml); confirmDelete() calls
 * elcm-service with the optional reason, shows the outcome as a message, and sets the "reload"
 * callback parameter so the page runs refreshStagedDocuments() (table and metric cards). The
 * rules (uploader only, not in a package, not submitted) are enforced by elcm-service; canDelete()
 * here only decides whether the icon is enabled.
 */
@Named
@ViewScoped
@Getter
@Setter
@RequiredArgsConstructor
public class StageDocumentsBean extends BaseBean {

    private final StageDocumentService stageDocumentService;

    private List<StagedDocumentRow> stagedDocuments;
    private List<StagedDocumentRow> selectedDocuments;

    /** Row whose trash icon was clicked; shown in the confirmation dialog. */
    private StagedDocumentRow documentPendingDelete;

    /** Optional reason typed into the confirmation dialog. Longest accepted: 500 characters. */
    private String deleteReason;

    @PostConstruct
    void init() {
        stagedDocuments = stageDocumentService.findStaged(null);
    }

    /**
     * Reloads the table from elcm-service. Also clears the current selection:
     * selectedDocuments holds row objects from the OLD list, and after a reload
     * those no longer belong to the rendered rows, so keeping them would leave
     * a stale selection driving the "Create Document Set" button.
     * StageDocumentService.findStaged() already degrades to an empty list on
     * a failed call rather than throwing, so a refresh can't break the page.
     */
    public void refresh() {
        stagedDocuments = stageDocumentService.findStaged(null);
        selectedDocuments = null;
    }

    /**
     * Enables the trash icon only for documents the current user uploaded, matching the
     * server rule. A convenience only: elcm-service decides, and answers 403 otherwise.
     */
    public boolean canDelete(final StagedDocumentRow row) {
        if (row == null || row.getUploadedBy() == null) {
            return false;
        }
        // The IAMS login id (what Upload Files records as uploadedBy) or, when the IAMS profile was
        // unavailable, the authenticated login id.
        final String me = getLoginId();
        final String authenticated = userHelper.getCurrentLoginId();
        return (me != null && me.equalsIgnoreCase(row.getUploadedBy()))
            || (authenticated != null && authenticated.equalsIgnoreCase(row.getUploadedBy()));
    }

    /** Trash icon clicked: remember the row and clear any earlier reason. The dialog is opened by the page. */
    public void prepareDelete(final StagedDocumentRow row) {
        documentPendingDelete = row;
        deleteReason = null;
    }

    /** Cancel or close: forget the row and the reason. */
    public void cancelDelete() {
        documentPendingDelete = null;
        deleteReason = null;
    }

    /**
     * Confirm clicked: soft delete through elcm-service, then report. The "reload" callback
     * parameter is true whenever the list on screen may be out of date (deleted, already
     * deleted, or now in a package), so the page refreshes it; it is false for a refusal that
     * changes nothing.
     */
    public void confirmDelete() {
        final StagedDocumentRow row = documentPendingDelete;
        if (row == null) {
            return;
        }
        final DeleteOutcome outcome = stageDocumentService.deleteStaged(row.getId(), deleteReason);
        final String name = row.getFileName();
        boolean reload = false;
        switch (outcome) {
            case DELETED -> {
                addMessage(FacesMessage.SEVERITY_INFO, "Document deleted", name);
                reload = true;
            }
            case NOT_FOUND -> {
                addMessage(FacesMessage.SEVERITY_INFO, "Already deleted", name + " was already deleted.");
                reload = true;
            }
            case CONFLICT -> {
                addMessage(FacesMessage.SEVERITY_WARN, "Cannot delete",
                    name + " is already in a document set or has been submitted.");
                reload = true;
            }
            case FORBIDDEN -> addMessage(FacesMessage.SEVERITY_WARN, "Cannot delete",
                "Only the person who uploaded " + name + " can delete it.");
            case TOO_LONG -> addMessage(FacesMessage.SEVERITY_WARN, "Reason too long",
                "Please keep the reason to 500 characters or fewer.");
            default -> addMessage(FacesMessage.SEVERITY_ERROR, "Delete failed",
                "The document could not be deleted. Please try again.");
        }
        PrimeFaces.current().ajax().addCallbackParam("reload", reload);
        // Keep the dialog open only for a reason that needs editing; every other outcome closes it.
        PrimeFaces.current().ajax().addCallbackParam("keepOpen", outcome == DeleteOutcome.TOO_LONG);
        if (outcome != DeleteOutcome.TOO_LONG) {
            documentPendingDelete = null;
            deleteReason = null;
        }
    }

    private void addMessage(final FacesMessage.Severity severity, final String summary, final String detail) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severity, summary, detail));
    }
}