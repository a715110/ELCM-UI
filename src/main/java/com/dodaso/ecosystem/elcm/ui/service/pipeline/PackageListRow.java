package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ADDED 2026-10-25. One line of the Contract Packages list. Mirrors elcm-service's PackageRailItem
 * (the list uses the same read as the Package Rail in ECWS).
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PackageListRow implements Serializable {

  private Long packageId;
  private String packageCode;
  private String statusCode;
  private String statusLabel;
  private String workspace;
  private String assigneeLoginId;
  private String assignee;
  private int docCount;
  private String relation;
  private String updatedAt;

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

  public String getUpdatedAtText() {
    return PackageDetailRow.text(updatedAt);
  }
}
