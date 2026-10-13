package com.dodaso.ecosystem.elcm.ui.bean;

import com.dodaso.ecosystem.elcm.ui.service.pipeline.ContractRecordRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.ContractRecordTermsRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.ContractRecordUiService;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.TermRow;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * Backing bean of the Contract Records screen (ADDED 2026-10-12): search the records, open one to
 * see its applied terms, and optionally show the values a later Apply replaced.
 */
@Named
@ViewScoped
@Getter
@RequiredArgsConstructor
public class ContractRecordsBean implements Serializable {

    private final ContractRecordUiService contractRecordUiService;

    @Setter
    private String query = "";
    private List<ContractRecordRow> records = new ArrayList<>();
    private boolean loadFailed;

    /** The record shown in the detail card; null when none is open. */
    private ContractRecordRow selected;
    private List<TermRow> terms = new ArrayList<>();
    private boolean termsFailed;
    @Setter
    private boolean showHistory;

    @PostConstruct
    void init() {
        search();
    }

    public void search() {
        final List<ContractRecordRow> rows = contractRecordUiService.list(query);
        loadFailed = rows == null;
        records = rows == null ? new ArrayList<>() : rows;
        if (selected != null) {
            loadTerms();
        }
    }

    public void select(final ContractRecordRow row) {
        selected = row;
        showHistory = false;
        loadTerms();
    }

    public void close() {
        selected = null;
        terms = new ArrayList<>();
    }

    /** The "show previous values" switch changed. */
    public void reloadTerms() {
        if (selected != null) {
            loadTerms();
        }
    }

    private void loadTerms() {
        final ContractRecordTermsRow answer = contractRecordUiService.terms(selected.getId(), showHistory);
        termsFailed = answer == null;
        terms = answer == null || answer.getTerms() == null ? new ArrayList<>() : answer.getTerms();
        if (answer != null && answer.getRecord() != null) {
            selected = answer.getRecord();
        }
    }

    public int getCurrentTermCount() {
        int n = 0;
        for (final TermRow t : terms) {
            if (t.isCurrent()) {
                n++;
            }
        }
        return n;
    }
}
