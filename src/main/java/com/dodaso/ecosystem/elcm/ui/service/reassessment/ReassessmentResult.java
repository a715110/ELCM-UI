package com.dodaso.ecosystem.elcm.ui.service.reassessment;

import java.io.Serializable;

/**
 * Outcome of a reassessment write: the new case page when it worked, otherwise a plain message
 * the screen can show. The message is the reason elcm-service gave when it gave one.
 */
public final class ReassessmentResult implements Serializable {

  private final ReassessmentRows.CaseDetail detail;
  private final String message;

  private ReassessmentResult(final ReassessmentRows.CaseDetail detail, final String message) {
    this.detail = detail;
    this.message = message;
  }

  public static ReassessmentResult ok(final ReassessmentRows.CaseDetail detail) {
    return new ReassessmentResult(detail, null);
  }

  public static ReassessmentResult failed(final String message) {
    return new ReassessmentResult(null, message);
  }

  public boolean isOk() {
    return message == null;
  }

  public ReassessmentRows.CaseDetail getDetail() {
    return detail;
  }

  public String getMessage() {
    return message;
  }
}
