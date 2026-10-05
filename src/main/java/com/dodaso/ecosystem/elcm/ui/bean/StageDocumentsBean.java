package com.dodaso.ecosystem.elcm.ui.bean;

import com.dodaso.ecosystem.elcm.ui.service.pipeline.StageDocumentService;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.StagedDocumentRow;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

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
}