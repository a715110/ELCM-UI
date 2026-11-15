package com.dodaso.ecosystem.elcm.ui.bean;

import com.dodaso.ecosystem.elcm.ui.service.pipeline.AssignableUserOptionRow;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * ADDED 2026-11-11 -- one reassessment: summary, survey (send and answer), conclusion, approval,
 * replacement link, close, and history. The case id comes from ?id= on the address. What each
 * person may do is decided by elcm-service and arrives as the can* flags of the case page; this
 * bean only shows the matching card.
 */
@Named
@ViewScoped
@Getter
@RequiredArgsConstructor
public class ReassessmentCaseBean implements Serializable {

  private final ReassessmentUiService service;
  private final ContractRecordUiService recordService;

  private Long caseId;
  private ReassessmentRows.CaseDetail detail;
  private boolean loadFailed;

  private List<ReassessmentRows.Option> templates = new ArrayList<>();
  private List<ReassessmentRows.Option> outcomes = new ArrayList<>();
  private List<AssignableUserOptionRow> people = new ArrayList<>();
  private List<ContractRecordRow> records = new ArrayList<>();

  @Setter
  private String templateCode;
  @Setter
  private String respondent;
  @Setter
  private String businessUnit;
  @Setter
  private LocalDate surveyDue;

  /** Survey answers by question number. */
  private Map<String, String> answers = new LinkedHashMap<>();

  @Setter
  private String outcomeCode;
  @Setter
  private String summary;

  @Setter
  private String decisionComment;

  @Setter
  private Long replacementId;
  @Setter
  private LocalDate effectiveDate;
  @Setter
  private String replacementNotes;

  @Setter
  private String closeComment;

  @PostConstruct
  void init() {
    final String id = FacesContext.getCurrentInstance().getExternalContext()
        .getRequestParameterMap().get("id");
    if (id != null && id.matches("\\d{1,18}")) {
      caseId = Long.valueOf(id);
      load(service.detail(caseId));
    } else {
      loadFailed = true;
    }
  }

  public void sendSurvey() {
    apply(service.sendSurvey(caseId, templateCode, respondent, businessUnit, surveyDue),
        "Survey sent");
  }

  public void saveAnswers() {
    apply(service.answer(caseId, answers, false), "Answers saved");
  }

  public void submitAnswers() {
    apply(service.answer(caseId, answers, true), "Answers submitted");
  }

  public void saveConclusion() {
    apply(service.conclude(caseId, outcomeCode, summary, false), "Conclusion saved");
  }

  public void submitConclusion() {
    apply(service.conclude(caseId, outcomeCode, summary, true), "Submitted for approval");
  }

  public void approve() {
    apply(service.decide(caseId, "APPROVED", decisionComment), "Conclusion approved");
  }

  public void reject() {
    apply(service.decide(caseId, "REJECTED", decisionComment), "Conclusion sent back");
  }

  public void linkReplacement() {
    apply(service.linkReplacement(caseId, replacementId, effectiveDate, replacementNotes),
        "Replacement linked");
  }

  public void closeCase() {
    apply(service.close(caseId, closeComment), "Reassessment closed");
  }

  /** Label for a record choice: code and counterparty. */
  public String recordLabel(final ContractRecordRow r) {
    return r.getCounterparty() == null || r.getCounterparty().isBlank()
        ? r.getRecordCode() : r.getRecordCode() + " (" + r.getCounterparty() + ")";
  }

  private void apply(final ReassessmentResult result, final String done) {
    final FacesContext context = FacesContext.getCurrentInstance();
    if (result.isOk()) {
      load(result.getDetail());
      context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, done, null));
    } else {
      context.addMessage(null,
          new FacesMessage(FacesMessage.SEVERITY_WARN, "Not done", result.getMessage()));
    }
  }

  /** Takes a case page and prepares the cards that the caller may use. */
  private void load(final ReassessmentRows.CaseDetail loaded) {
    detail = loaded;
    loadFailed = loaded == null;
    if (loaded == null) {
      return;
    }
    answers = new LinkedHashMap<>();
    for (final ReassessmentRows.SurveyLine line : loaded.getSurvey()) {
      answers.put(line.getKey(), line.getAnswer());
    }
    outcomeCode = loaded.getOutcomeCode();
    summary = loaded.getConclusionSummary();
    decisionComment = null;
    if (loaded.isCanSendSurvey() && templates.isEmpty()) {
      templates = service.surveyTemplates();
      people = service.people();
    }
    if (loaded.isCanConclude() && outcomes.isEmpty()) {
      outcomes = service.outcomes();
    }
    if (loaded.isCanLinkReplacement() && records.isEmpty()) {
      final List<ContractRecordRow> all = recordService.list("");
      if (all != null) {
        for (final ContractRecordRow r : all) {
          if (!r.getId().equals(loaded.getSummary().getRecordId())) {
            records.add(r);
          }
        }
      }
    }
  }
}
