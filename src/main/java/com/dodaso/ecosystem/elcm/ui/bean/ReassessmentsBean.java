package com.dodaso.ecosystem.elcm.ui.bean;

import com.dodaso.ecosystem.elcm.ui.service.pipeline.ContractRecordRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.ContractRecordUiService;
import com.dodaso.ecosystem.elcm.ui.service.reassessment.ReassessmentResult;
import com.dodaso.ecosystem.elcm.ui.service.reassessment.ReassessmentRows;
import com.dodaso.ecosystem.elcm.ui.service.reassessment.ReassessmentUiService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.primefaces.PrimeFaces;

/**
 * ADDED 2026-11-11 -- list page of lease reassessments, with the "New reassessment" dialog. Opens
 * with the dialog filled in when the address has ?record=ID (the link on Contract Records).
 */
@Named
@ViewScoped
@Getter
@RequiredArgsConstructor
public class ReassessmentsBean implements Serializable {

  private final ReassessmentUiService service;
  private final ContractRecordUiService recordService;

  /** "mine" or "all". */
  @Setter
  private String scope = "mine";
  private List<ReassessmentRows.CaseRow> cases = new ArrayList<>();
  private boolean loadFailed;
  /** True when the page was opened from a record, so the dialog shows at once. */
  private boolean openDialogOnLoad;

  private List<ReassessmentRows.Option> triggerTypes = new ArrayList<>();
  private List<ContractRecordRow> activeRecords = new ArrayList<>();

  @Setter
  private Long newRecordId;
  @Setter
  private String newTrigger;
  @Setter
  private String newDescription;
  @Setter
  private LocalDate newEventDate;
  @Setter
  private LocalDate newDueDate;

  @PostConstruct
  void init() {
    reload();
    final String record = FacesContext.getCurrentInstance().getExternalContext()
        .getRequestParameterMap().get("record");
    if (record != null && record.matches("\\d{1,18}")) {
      prepareNew();
      newRecordId = Long.valueOf(record);
      openDialogOnLoad = true;
    }
  }

  public void reload() {
    final List<ReassessmentRows.CaseRow> rows = service.list(scope, null);
    loadFailed = rows == null;
    cases = rows == null ? new ArrayList<>() : rows;
  }

  /** Loads the choices of the dialog and clears its fields. */
  public void prepareNew() {
    triggerTypes = service.triggerTypes();
    activeRecords = new ArrayList<>();
    final List<ContractRecordRow> all = recordService.list("");
    if (all != null) {
      for (final ContractRecordRow r : all) {
        if ("ACTIVE".equalsIgnoreCase(r.getStatus())) {
          activeRecords.add(r);
        }
      }
    }
    newRecordId = null;
    newTrigger = null;
    newDescription = null;
    newEventDate = null;
    newDueDate = null;
  }

  public void create() {
    final ReassessmentResult result =
        service.open(newRecordId, newTrigger, newDescription, newEventDate, newDueDate);
    if (!result.isOk()) {
      FacesContext.getCurrentInstance().addMessage(null,
          new FacesMessage(FacesMessage.SEVERITY_WARN, "Not opened", result.getMessage()));
      FacesContext.getCurrentInstance().validationFailed();
      return;
    }
    PrimeFaces.current().executeScript("PF('newCaseDlg').hide()");
    FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO,
        "Reassessment opened", result.getDetail().getSummary().getCaseCode()));
    reload();
  }

  /** Label for a record choice: code and counterparty. */
  public String recordLabel(final ContractRecordRow r) {
    return r.getCounterparty() == null || r.getCounterparty().isBlank()
        ? r.getRecordCode() : r.getRecordCode() + " (" + r.getCounterparty() + ")";
  }
}
