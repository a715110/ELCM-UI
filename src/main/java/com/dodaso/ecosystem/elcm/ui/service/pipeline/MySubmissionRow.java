package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One submission the signed-in user made. Mirrors elcm-service's MySubmissionItem. groupCode is
 * PENDING, IN_PROGRESS, COMPLETED or DECLINED.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MySubmissionRow implements Serializable {
  private Long submissionId;
  private Long packageId;
  private String packageCode;
  private String workspace;
  private String targetRecord;
  private int docCount;
  private String statusCode;
  private String statusLabel;
  private String groupCode;
  private String submittedAt;
  private String updatedAt;
  private String correctionNote;
  private String declineReason;
  private String declinedBy;
  private String declinedAt;

  /** What the Status column shows: "Awaiting approval" instead of the raw "Reviewed". */
  public String getStatusText() {
    return "REVIEWED".equals(statusCode) ? "Awaiting approval" : statusLabel;
  }

  /** Tag colour by bucket. */
  public String getSeverity() {
    if (groupCode == null) {
      return "info";
    }
    return switch (groupCode) {
      case "COMPLETED" -> "success";
      case "DECLINED" -> "danger";
      case "PENDING" -> "secondary";
      default -> "warn";
    };
  }

  /** The note to show in the Details column: the decline reason, else the correction note. */
  public String getDetails() {
    if ("DECLINED".equals(groupCode) && declineReason != null) {
      final String by = declinedBy == null ? "" : " (" + declinedBy + ")";
      return "Declined" + by + ": " + declineReason;
    }
    return correctionNote == null ? "" : "Correction note: " + correctionNote;
  }

  /**
   * The Open button works for packages that are (or were) in the pipeline, not for declined ones.
   */
  public boolean isOpenable() {
    return !"DECLINED".equals(groupCode);
  }

  public String getSubmittedAtText() {
    return text(submittedAt);
  }

  public String getUpdatedAtText() {
    return text(updatedAt);
  }

  static String text(final String iso) {
    if (iso == null || iso.isBlank()) {
      return "";
    }
    final String spaced = iso.replace('T', ' ');
    return spaced.length() > 16 ? spaced.substring(0, 16) : spaced;
  }
}
