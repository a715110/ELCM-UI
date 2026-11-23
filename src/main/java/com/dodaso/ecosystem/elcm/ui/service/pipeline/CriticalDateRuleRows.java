package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ADDED 2026-11-21. What the Critical Date Rules screen reads from elcm-service. Mirrors
 * CriticalDateRuleDtos there; unknown fields are ignored so a newer service never breaks the screen.
 */
public final class CriticalDateRuleRows {

  private CriticalDateRuleRows() {
  }

  /** The whole screen. */
  @Getter
  @Setter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class AdminView implements Serializable {
    private boolean enabled;
    private boolean ecwsEnabled;
    private List<RuleRow> rules = new ArrayList<>();
    private List<FieldOption> dateFields = new ArrayList<>();
    private List<FieldOption> numberFields = new ArrayList<>();
    private List<String> priorities = new ArrayList<>();
  }

  /** One rule. */
  @Getter
  @Setter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class RuleRow implements Serializable {
    private Long id;
    private String code;
    private String label;
    private String fieldKey;
    private String noticeFieldKey;
    private int leadDays;
    private String priority;
    private boolean enabled;
    private int openTasks;
    private boolean deletable;
    private String updatedBy;
    private String updatedAt;

    /** The notice field, or a dash when the rule has none. */
    public String getNoticeText() {
      return noticeFieldKey == null || noticeFieldKey.isBlank() ? "-" : noticeFieldKey;
    }

    /** Who changed it and when, or a dash for a rule nobody has changed on the screen. */
    public String getChangedText() {
      if (updatedBy == null || updatedBy.isBlank()) {
        return "-";
      }
      final String day = updatedAt == null || updatedAt.length() < 10 ? ""
          : ", " + updatedAt.substring(0, 10);
      return updatedBy + day;
    }
  }

  /** A contract field that can be picked. */
  @Getter
  @Setter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class FieldOption implements Serializable {
    private String key;
    private String label;

    /** "Expiration Date (expiration_date)" for the pick list. */
    public String getDisplay() {
      return label == null || label.isBlank() ? key : label + " (" + key + ")";
    }
  }

  /** What "check now" did. */
  @Getter
  @Setter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class CheckResult implements Serializable {
    private boolean active;
    private int checked;
    private String message;
  }
}
