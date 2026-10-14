package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One line of the Work Queue, mirroring elcm-service's WorkQueueItem (same field names, so the JSON
 * maps one to one). stageCode is EXTRACTING, REVIEW, APPROVAL, FAILED or APPLIED.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkQueueRow implements Serializable {
  private Long packageId;
  private String packageCode;
  private String workspace;
  private String targetRecord;
  private int docCount;
  private String assignee;
  private String assigneeLoginId;
  private String submittedBy;
  private String submittedByLoginId;
  private String reviewedBy;
  private String submissionStatusCode;
  private String stageCode;
  private String stageLabel;
  private String since;
  private String submittedAt;

  /** The time in the state, as "yyyy-MM-dd HH:mm" (UTC). */
  public String getSinceText() {
    return text(since);
  }

  public String getSubmittedAtText() {
    return text(submittedAt);
  }

  private static String text(final String iso) {
    if (iso == null || iso.isBlank()) {
      return "";
    }
    final String spaced = iso.replace('T', ' ');
    return spaced.length() > 16 ? spaced.substring(0, 16) : spaced;
  }
}
