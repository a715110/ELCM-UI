package com.dodaso.ecosystem.elcm.ui.bean;

import com.dodaso.ecosystem.elcm.ui.service.survey.SurveyBuilderResult;
import com.dodaso.ecosystem.elcm.ui.service.survey.SurveyBuilderUiService;
import com.dodaso.ecosystem.elcm.ui.service.survey.SurveyRows;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * ADDED 2026-11-13. Survey Builder page: the list of surveys, and one survey open for editing with
 * its questions, a preview, publish and copy. Every change goes to elcm-service, which checks it;
 * a refusal is shown as a message and nothing changes on screen.
 */
@Named
@ViewScoped
@Getter
@RequiredArgsConstructor
public class SurveyBuilderBean implements Serializable {

  /** Answer types in the order shown in the menu. Codes match elcm-service. */
  private static final List<SurveyRows.TypeOption> TYPES = List.of(
      new SurveyRows.TypeOption("TEXT", "Short text"),
      new SurveyRows.TypeOption("NUMBER", "Number"),
      new SurveyRows.TypeOption("DATE", "Date"),
      new SurveyRows.TypeOption("YES_NO", "Yes or no"),
      new SurveyRows.TypeOption("CHOICE", "Pick one from a list"));

  private final SurveyBuilderUiService service;

  private List<SurveyRows.TemplateRow> templates = new ArrayList<>();
  private boolean loadFailed;
  /** The survey open for editing; null shows the list. */
  private SurveyRows.TemplateDetail selected;

  /** Fields of the new survey and copy dialog. */
  @Setter
  private String newName;
  @Setter
  private String newDescription;
  /** Survey being copied; null when the dialog creates a blank survey. */
  private Long copySourceId;

  /** Fields of the survey details. */
  @Setter
  private String editName;
  @Setter
  private String editDescription;

  /** Fields of the question dialog; questionId is null when adding. */
  private Long questionId;
  @Setter
  private String questionText;
  @Setter
  private String questionType = "TEXT";
  @Setter
  private String questionChoices;
  @Setter
  private boolean questionRequired = true;
  @Setter
  private boolean questionActive = true;

  @PostConstruct
  void init() {
    reload();
  }

  public List<SurveyRows.TypeOption> getTypes() {
    return TYPES;
  }

  public void reload() {
    final List<SurveyRows.TemplateRow> rows = service.list();
    loadFailed = rows == null;
    templates = rows == null ? new ArrayList<>() : rows;
  }

  // ---------------------------------------------------------------- surveys

  public void open(final Long id) {
    final SurveyRows.TemplateDetail detail = service.detail(id);
    if (detail == null) {
      warn("Not opened", "The survey could not be loaded.");
      return;
    }
    show(detail);
  }

  public void back() {
    selected = null;
    reload();
  }

  /** Prepares the dialog for a blank survey (id null) or a copy of one. */
  public void prepareNew(final Long sourceId) {
    copySourceId = sourceId;
    newDescription = null;
    newName = null;
    if (sourceId != null) {
      for (final SurveyRows.TemplateRow t : templates) {
        if (sourceId.equals(t.getId())) {
          newName = "Copy of " + t.getName();
        }
      }
    }
  }

  public void createOrCopy() {
    final SurveyBuilderResult result = copySourceId == null
        ? service.create(newName, newDescription) : service.copy(copySourceId, newName);
    if (accept(result)) {
      show(result.getDetail());
      done(copySourceId == null ? "Survey created. Add its questions, then publish it."
          : "Copy created. It is not published yet.");
      org.primefaces.PrimeFaces.current().executeScript("PF('newSurveyDlg').hide()");
    }
  }

  public void saveDetails() {
    if (selected != null) {
      final SurveyBuilderResult result = service.update(selected.getSummary().getId(), editName,
          editDescription, selected.getSummary().isPublished());
      if (accept(result)) {
        show(result.getDetail());
        done("Saved.");
      }
    }
  }

  public void togglePublished() {
    if (selected != null) {
      final boolean publish = !selected.getSummary().isPublished();
      final SurveyBuilderResult result = service.update(selected.getSummary().getId(),
          selected.getSummary().getName(), selected.getSummary().getDescription(), publish);
      if (accept(result)) {
        show(result.getDetail());
        done(publish ? "Published. It can now be chosen when sending a survey."
            : "Unpublished. It can no longer be chosen. Surveys already sent are not affected.");
      }
    }
  }

  public void deleteSurvey(final Long id) {
    final SurveyBuilderResult result = service.delete(id);
    if (accept(result)) {
      selected = null;
      templates = result.getRows() == null ? new ArrayList<>() : result.getRows();
      done("Survey deleted.");
    }
  }

  // -------------------------------------------------------------- questions

  /** Fills the question dialog: a new question (id null) or an existing one. */
  public void prepareQuestion(final Long id) {
    questionId = id;
    questionText = null;
    questionType = "TEXT";
    questionChoices = null;
    questionRequired = true;
    questionActive = true;
    if (id != null && selected != null) {
      for (final SurveyRows.QuestionRow q : selected.getQuestions()) {
        if (id.equals(q.getId())) {
          questionText = q.getText();
          questionType = q.getType();
          questionChoices = q.getChoices();
          questionRequired = q.isRequired();
          questionActive = q.isActive();
        }
      }
    }
  }

  public void saveQuestion() {
    if (selected == null) {
      return;
    }
    final SurveyBuilderResult result = questionId == null
        ? service.addQuestion(selected.getSummary().getId(), questionText, questionType,
            questionChoices, questionRequired, questionActive)
        : service.updateQuestion(questionId, questionText, questionType, questionChoices,
            questionRequired, questionActive);
    if (accept(result)) {
      show(result.getDetail());
      done("Question saved.");
      org.primefaces.PrimeFaces.current().executeScript("PF('questionDlg').hide()");
    }
  }

  public void move(final Long id, final String direction) {
    final SurveyBuilderResult result = service.moveQuestion(id, direction);
    if (accept(result)) {
      show(result.getDetail());
    }
  }

  public void deleteQuestion(final Long id) {
    final SurveyBuilderResult result = service.deleteQuestion(id);
    if (accept(result)) {
      show(result.getDetail());
      done("Question deleted.");
    }
  }

  /** True when the question dialog is for a list question, to show the choices box. */
  public boolean isChoiceType() {
    return "CHOICE".equals(questionType);
  }

  // ---------------------------------------------------------------- helpers

  private void show(final SurveyRows.TemplateDetail detail) {
    selected = detail;
    editName = detail.getSummary().getName();
    editDescription = detail.getSummary().getDescription();
  }

  private boolean accept(final SurveyBuilderResult result) {
    if (result.isOk()) {
      return true;
    }
    warn("Not saved", result.getMessage());
    return false;
  }

  private static void done(final String text) {
    FacesContext.getCurrentInstance().addMessage(null,
        new FacesMessage(FacesMessage.SEVERITY_INFO, "Done", text));
  }

  private static void warn(final String title, final String text) {
    FacesContext.getCurrentInstance().addMessage(null,
        new FacesMessage(FacesMessage.SEVERITY_WARN, title, text));
  }
}
