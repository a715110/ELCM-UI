package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The Critical Dates screen's data. Mirrors elcm-service's CriticalDateOverview (ADDED 2026-11-16). */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CriticalDateView implements Serializable {
  private boolean enabled;
  private boolean ecwsEnabled;
  private String cron;
  private String zone;
  private String today;
  private List<RuleRow> rules = new ArrayList<>();
  private List<DateRow> dates = new ArrayList<>();

  /** One rule. */
  @Getter
  @Setter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class RuleRow implements Serializable {
    private String code;
    private String label;
    private String fieldKey;
    private String noticeFieldKey;
    private int leadDays;
    private String priority;
    private boolean enabled;

    /** The notice field, or a dash when the rule has none. */
    public String getNoticeText() {
      return noticeFieldKey == null || noticeFieldKey.isBlank() ? "-" : noticeFieldKey;
    }
  }

  /** One date being watched. status is WAITING, READY, TASK_REQUESTED, OVERDUE or PAST. */
  @Getter
  @Setter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class DateRow implements Serializable {
    private Long recordId;
    private String recordCode;
    private String counterparty;
    private String ruleLabel;
    private String contractDate;
    private String dueDate;
    private String createOn;
    private long daysLeft;
    private String status;
    private int noticeDays;

    /** The words the screen shows for the status. */
    public String getStatusText() {
      return switch (status == null ? "" : status) {
        case "WAITING" -> "Waiting";
        case "READY" -> "Task due to be made";
        case "TASK_REQUESTED" -> "Task requested";
        case "OVERDUE" -> "Overdue";
        case "PAST" -> "Past, no task";
        default -> "-";
      };
    }

    /** PrimeFaces tag severity of the status. */
    public String getSeverity() {
      return switch (status == null ? "" : status) {
        case "TASK_REQUESTED" -> "success";
        case "READY" -> "warning";
        case "OVERDUE" -> "danger";
        case "PAST" -> "secondary";
        default -> "info";
      };
    }

    /** "in 45 days", "today", "12 days ago". */
    public String getDaysText() {
      if (daysLeft == 0) {
        return "today";
      }
      final long n = Math.abs(daysLeft);
      final String unit = n == 1 ? " day" : " days";
      return daysLeft > 0 ? "in " + n + unit : n + unit + " ago";
    }
  }
}
