package com.dodaso.ecosystem.elcm.ui.bean;

import com.dodaso.ecosystem.elcm.ui.service.pipeline.StageDocumentService;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.StagedDocumentRow;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.io.Serializable;
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
}