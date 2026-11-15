package com.dodaso.ecosystem.elcm.ui.service.survey;

import java.io.Serializable;
import java.util.List;

/**
 * ADDED 2026-11-13. Outcome of a survey builder write: the survey (or the remaining list after a
 * delete) when it worked, otherwise a plain message for the screen.
 */
public final class SurveyBuilderResult implements Serializable {

  private final SurveyRows.TemplateDetail detail;
  private final List<SurveyRows.TemplateRow> rows;
  private final String message;

  private SurveyBuilderResult(final SurveyRows.TemplateDetail detail,
      final List<SurveyRows.TemplateRow> rows, final String message) {
    this.detail = detail;
    this.rows = rows;
    this.message = message;
  }

  public static SurveyBuilderResult ok(final SurveyRows.TemplateDetail detail) {
    return new SurveyBuilderResult(detail, null, null);
  }

  public static SurveyBuilderResult okRows(final List<SurveyRows.TemplateRow> rows) {
    return new SurveyBuilderResult(null, rows, null);
  }

  public static SurveyBuilderResult failed(final String message) {
    return new SurveyBuilderResult(null, null, message);
  }

  public boolean isOk() {
    return message == null;
  }

  public SurveyRows.TemplateDetail getDetail() {
    return detail;
  }

  public List<SurveyRows.TemplateRow> getRows() {
    return rows;
  }

  public String getMessage() {
    return message;
  }
}
