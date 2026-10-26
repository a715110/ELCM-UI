package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ADDED 2026-10-25. Everything the Package Details screen shows about one package. Mirrors
 * elcm-service's PackageDetail. statusCode is ASSEMBLY (draft), VALIDATED (submitted) or COMPLETED
 * (closed); relation is ASSIGNED, CREATED, SUBMITTER, REVIEWER or OTHER. Times are UTC.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PackageDetailRow implements Serializable {

  private Long packageId;
  private String packageCode;
  private String statusCode;
  private String statusLabel;
  private String workspaceCode;
  private String workspaceName;
  private String targetRecord;
  private String assigneeLoginId;
  private String assignee;
  private String createdBy;
  private String createdAt;
  private String updatedBy;
  private String updatedAt;
  private String relation;
  private List<Document> documents = new ArrayList<>();
  private List<Submission> submissions = new ArrayList<>();

  /** A draft package is still being assembled on the Dashboard; it has no review yet. */
  public boolean isDraft() {
    return "ASSEMBLY".equals(statusCode);
  }

  /** Submitted or closed: the package has a review screen. */
  public boolean isReviewable() {
    return !isDraft();
  }

  /** Tag colour by status. */
  public String getSeverity() {
    if (statusCode == null) {
      return "info";
    }
    return switch (statusCode) {
      case "COMPLETED" -> "success";
      case "VALIDATED" -> "info";
      default -> "warn";
    };
  }

  /** The workspace as "Name (CODE)" when both are known. */
  public String getWorkspaceText() {
    if (workspaceName == null || workspaceName.isBlank()) {
      return workspaceCode == null ? "" : workspaceCode;
    }
    return workspaceCode == null || workspaceCode.isBlank()
        ? workspaceName : workspaceName + " (" + workspaceCode + ")";
  }

  /** The target record, or a dash when none is linked yet. */
  public String getTargetRecordText() {
    return targetRecord == null || targetRecord.isBlank() ? "Not linked yet" : targetRecord;
  }

  /** How the viewer is involved, in words. */
  public String getRelationText() {
    if (relation == null) {
      return "";
    }
    return switch (relation) {
      case "ASSIGNED" -> "Assigned to you";
      case "CREATED" -> "You created it";
      case "SUBMITTER" -> "You submitted it";
      case "REVIEWER" -> "You reviewed it";
      default -> "";
    };
  }

  public String getCreatedAtText() {
    return text(createdAt);
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

  /** One document of the package. fileUploadId opens it in the document viewer. */
  @Getter
  @Setter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Document implements Serializable {

    private Long stagedDocumentId;
    private Long fileUploadId;
    private String fileName;
    private String roleCode;
    private String roleLabel;
    private String targetRecord;

    public boolean isViewable() {
      return fileUploadId != null;
    }

    public String getTargetRecordText() {
      return targetRecord == null || targetRecord.isBlank() ? "Unassigned" : targetRecord;
    }
  }

  /** One submission of the package; a declined package can be submitted again. */
  @Getter
  @Setter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Submission implements Serializable {

    private Long submissionId;
    private String statusCode;
    private String statusLabel;
    private String submittedBy;
    private String submittedAt;
    private String claimedBy;
    private String reviewedBy;
    private String reviewedAt;
    private String declineReason;
    private String declinedBy;
    private String declinedAt;
    private String correctionNote;

    /** "Reviewed" is shown as "Awaiting approval", like My Submissions. */
    public String getStatusText() {
      return "REVIEWED".equals(statusCode) ? "Awaiting approval" : statusLabel;
    }

    public String getSubmittedAtText() {
      return text(submittedAt);
    }

    public String getReviewedAtText() {
      return text(reviewedAt);
    }

    public String getDeclinedAtText() {
      return text(declinedAt);
    }

    public boolean isDeclined() {
      return declineReason != null && !declineReason.isBlank();
    }

    public boolean isHasCorrectionNote() {
      return correctionNote != null && !correctionNote.isBlank();
    }
  }
}
