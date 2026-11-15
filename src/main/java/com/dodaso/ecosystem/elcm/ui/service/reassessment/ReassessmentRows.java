package com.dodaso.ecosystem.elcm.ui.service.reassessment;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Rows the reassessment screens bind to. Field names match the JSON of elcm-service's
 * ReassessmentDtos, and unknown fields are ignored, so a field added on the service side does not
 * break the screens. Times are shown as text in UTC like the other ELCM screens.
 */
public final class ReassessmentRows {

  private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private ReassessmentRows() {
  }

  static String stamp(final LocalDateTime t) {
    return t == null ? "-" : STAMP.format(t);
  }

  /** A lookup choice. */
  @Getter
  @Setter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Option implements Serializable {
    private String code;
    private String label;
    private String description;
  }

  /** One line of the case list. */
  @Getter
  @Setter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class CaseRow implements Serializable {
    private Long id;
    private String caseCode;
    private Long recordId;
    private String recordCode;
    private String counterparty;
    private String triggerCode;
    private String triggerLabel;
    private String statusCode;
    private String statusLabel;
    private LocalDate dueDate;
    private String openedBy;
    private LocalDateTime openedAt;
    private String respondent;
    private String outcomeCode;

    @JsonIgnore
    public String getOpenedAtText() {
      return stamp(openedAt);
    }

    @JsonIgnore
    public boolean isOverdue() {
      return dueDate != null && dueDate.isBefore(LocalDate.now())
          && !"CLOSED".equals(statusCode) && !"CONCLUDED".equals(statusCode);
    }
  }

  /** One survey question with its answer. */
  @Getter
  @Setter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class SurveyLine implements Serializable {
    private int number;
    private String text;
    private String type;
    private List<String> choices = new ArrayList<>();
    private boolean required;
    private String answer;

    /** Key of the answer in the bean's answer map. */
    @JsonIgnore
    public String getKey() {
      return String.valueOf(number);
    }
  }

  /** One history entry. */
  @Getter
  @Setter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class EventRow implements Serializable {
    private String eventCode;
    private String message;
    private String actor;
    private LocalDateTime occurredAt;

    @JsonIgnore
    public String getOccurredAtText() {
      return stamp(occurredAt);
    }
  }

  /** The case page. */
  @Getter
  @Setter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class CaseDetail implements Serializable {
    private CaseRow summary;
    private String triggerDescription;
    private LocalDate triggerDate;
    private boolean surveySent;
    private String surveyTemplate;
    private String businessUnit;
    private String surveySentBy;
    private LocalDateTime surveySentAt;
    private LocalDate surveyDueDate;
    private LocalDateTime surveySubmittedAt;
    private List<SurveyLine> survey = new ArrayList<>();
    private String outcomeCode;
    private String outcomeLabel;
    private String conclusionSummary;
    private boolean conclusionSubmitted;
    private String concludedBy;
    private LocalDateTime concludedAt;
    private String decisionCode;
    private String approvalComment;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private Long replacementRecordId;
    private String replacementRecordCode;
    private List<EventRow> events = new ArrayList<>();
    private boolean canSendSurvey;
    private boolean canAnswerSurvey;
    private boolean canConclude;
    private boolean canDecide;
    private boolean canLinkReplacement;
    private boolean canClose;

    @JsonIgnore
    public String getSurveySentAtText() {
      return stamp(surveySentAt);
    }

    @JsonIgnore
    public String getSurveySubmittedAtText() {
      return stamp(surveySubmittedAt);
    }

    @JsonIgnore
    public String getConcludedAtText() {
      return stamp(concludedAt);
    }

    @JsonIgnore
    public String getApprovedAtText() {
      return stamp(approvedAt);
    }
  }
}
