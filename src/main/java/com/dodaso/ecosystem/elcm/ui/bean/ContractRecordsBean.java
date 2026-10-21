package com.dodaso.ecosystem.elcm.ui.bean;

import com.dodaso.ecosystem.elcm.ui.helper.CsvBuilder;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.ContractRecordRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.ContractRecordTermsRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.ContractRecordUiService;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.TermRow;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

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

    /**
     * ADDED 2026-10-20. CSV of the records the list shows now (the search applies, the table's page
     * and sort order do not). Times are UTC, like the screen.
     */
    public StreamedContent getRecordsCsv() {
        final CsvBuilder csv = new CsvBuilder().row("Record", "Counterparty", "Type", "Workspace",
            "Status", "Applied terms", "Last applied (UTC)");
        for (final ContractRecordRow r : records) {
            csv.row(r.getRecordCode(), r.getCounterparty(), r.getContractType(), r.getWorkspace(),
                r.getStatus(), String.valueOf(r.getTermCount()), blankDash(r.getLastAppliedText()));
        }
        return download("contract-records", csv);
    }

    /**
     * ADDED 2026-10-20. CSV of the terms of the open record, as the terms table shows them (with
     * the replaced values when "Show previous values" is on).
     */
    public StreamedContent getTermsCsv() {
        final String code = selected == null ? "" : selected.getRecordCode();
        final CsvBuilder csv = new CsvBuilder().row("Record", "Term", "Value", "State",
            "Source package", "Corrected by reviewer", "Applied by", "Applied at (UTC)",
            "Replaced at (UTC)");
        for (final TermRow t : terms) {
            csv.row(code, t.getLabel(), t.getValue(), t.isCurrent() ? "Current" : "Replaced",
                t.getPackageCode(), t.isCorrected() ? "Yes" : "No", t.getAppliedBy(),
                blankDash(t.getAppliedAtText()), blankDash(t.getSupersededAtText()));
        }
        return download("contract-terms-" + safeName(code), csv);
    }

    private static StreamedContent download(final String baseName, final CsvBuilder csv) {
        final byte[] bytes = csv.toBytes();
        return DefaultStreamedContent.builder()
            .name(baseName + "-" + LocalDate.now(ZoneOffset.UTC) + ".csv")
            .contentType("text/csv; charset=UTF-8")
            .stream(() -> new ByteArrayInputStream(bytes))
            .build();
    }

    /** The screen shows a dash for "no time"; the file leaves the cell empty. */
    private static String blankDash(final String text) {
        return "-".equals(text) ? "" : text;
    }

    /** A record code made safe for a file name. */
    private static String safeName(final String code) {
        return code == null || code.isBlank() ? "record" : code.replaceAll("[^A-Za-z0-9._-]", "_");
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
