package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One line of the Task Rail (ADDED 2026-10-18), mirroring elcm-service's TaskRailItem (same field
 * names, so the JSON maps one to one). event is REVIEW or APPROVAL. relation is ASSIGNED, SUBMITTER,
 * REVIEWER, POOL or WATCHING. dueState is OVERDUE, TODAY, LATER or NONE (open tasks only).
 * ecwsTaskId (ADDED 2026-10-18) is the ECWS task id, null until the task has been sent to ECWS.
 */
@Getter
@Setter
@NoArgsConstructor
public class TaskRailRow implements Serializable {

  private static final DateTimeFormatter DAY =
      DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);

  private Long taskId;
  private Long packageId;
  private Integer ecwsTaskId;
  private String packageCode;
  private String event;
  private String eventLabel;
  private String summary;
  private String assigneeLoginId;
  private String assignee;
  private String relation;
  private String dueDate;
  private String dueState;
  private String closedAt;
  private String outcome;

  /** Why the viewer sees this line, in words: who has it and how the viewer is involved. */
  public String getRelationText() {
    final String who = assignee == null || assignee.isBlank() ? "Unassigned" : assignee;
    return switch (relation == null ? "" : relation) {
      case "ASSIGNED" -> "Assigned to you";
      case "SUBMITTER" -> who + ", your package";
      case "REVIEWER" -> who + ", you reviewed it";
      case "POOL" -> "Unassigned, open to your role";
      default -> who;
    };
  }

  /** The due badge text, such as "Overdue, Oct 14", "Due today" or "Due Oct 21"; empty if none. */
  public String getDueText() {
    if (dueDate == null || dueDate.isBlank() || "NONE".equals(dueState)) {
      return "";
    }
    String day = dueDate;
    try {
      day = LocalDate.parse(dueDate).format(DAY);
    } catch (final RuntimeException e) {
      // keep the raw text
    }
    return switch (dueState == null ? "" : dueState) {
      case "OVERDUE" -> "Overdue, " + day;
      case "TODAY" -> "Due today";
      default -> "Due " + day;
    };
  }

  /** Style suffix for the type chip. */
  public String getChipStyle() {
    return "APPROVAL".equals(event) ? "approval" : "review";
  }

  /** Style suffix for the due badge. */
  public String getDueStyle() {
    return dueState == null ? "none" : dueState.toLowerCase(Locale.ROOT);
  }
}
