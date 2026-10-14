package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The correction note of a resubmission and the decline it answers. Mirrors elcm-service's
 * Resubmission.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResubmissionRow implements Serializable {
  private boolean resubmitted;
  private String correctionNote;
  private String submittedBy;
  private String submittedAt;
  private String previousDeclineReason;
  private String previousDeclinedBy;
  private String previousDeclinedAt;

  public String getSubmittedAtText() {
    return text(submittedAt);
  }

  public String getPreviousDeclinedAtText() {
    return text(previousDeclinedAt);
  }

  private static String text(final String iso) {
    if (iso == null) {
      return "";
    }
    final String t = iso.replace('T', ' ');
    return (t.length() >= 16 ? t.substring(0, 16) : t) + " UTC";
  }
}
