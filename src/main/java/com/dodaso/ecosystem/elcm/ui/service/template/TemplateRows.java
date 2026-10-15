package com.dodaso.ecosystem.elcm.ui.service.template;

import com.dodaso.ecosystem.elcm.ui.service.pipeline.PackageOutcome;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Shapes the Extraction Templates screen reads from elcm-service (ADDED 2026-10-14). They mirror
 * TemplateModels of elcm-service.
 */
public final class TemplateRows {

  private TemplateRows() {}

  /** One version of a template, without its fields. */
  @Getter
  @Setter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Summary implements Serializable {
    private Long id;
    private String name;
    private Long contractTypeId;
    private String contractType;
    private String version;
    private String status;
    private boolean active;
    private int fieldCount;
    private String changeReason;
    private Long basedOnId;
    private String publishedBy;
    private String publishedAt;
    private String updatedBy;
    private String updatedAt;

    public boolean isDraft() {
      return "DRAFT".equals(status);
    }

    public String getPublishedText() {
      return timeText(publishedAt);
    }

    public String getUpdatedText() {
      return timeText(updatedAt);
    }

    /** "Property Lease, version 3". */
    public String getTitle() {
      return name + ", version " + version;
    }
  }

  /** One field of a version. The edit dialog works on a copy. */
  @Getter
  @Setter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Field implements Serializable {
    private Long id;
    private String key;
    private String label;
    private String dataType;
    private boolean critical;
    private boolean required;
    private int order;
    private String description;
    private String keywords;
    private String allowedValues;
    private String valueHints;

    public Field copy() {
      final Field c = new Field();
      c.id = id;
      c.key = key;
      c.label = label;
      c.dataType = dataType;
      c.critical = critical;
      c.required = required;
      c.order = order;
      c.description = description;
      c.keywords = keywords;
      c.allowedValues = allowedValues;
      c.valueHints = valueHints;
      return c;
    }

    /** True when any hint for the extractor is set. */
    @JsonIgnore
    public boolean isHasHints() {
      return notBlank(description)
          || notBlank(keywords)
          || notBlank(allowedValues)
          || notBlank(valueHints);
    }
  }

  /** A version with its fields. */
  @Getter
  @Setter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Detail implements Serializable {
    private Summary summary;
    private List<Field> fields = new ArrayList<>();
    private Long openDraftId;
  }

  /** One entry of the change log. */
  @Getter
  @Setter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Event implements Serializable {
    private Long templateId;
    private String versionLabel;
    private String eventType;
    private String reason;
    private String summary;
    private String createdBy;
    private String createdAt;

    public String getCreatedText() {
      return timeText(createdAt);
    }

    /** Plain words for the event type. */
    public String getEventText() {
      if (eventType == null) {
        return "";
      }
      return switch (eventType) {
        case "IMPORTED" -> "Existing template";
        case "DRAFT_CREATED" -> "Draft created";
        case "DRAFT_SAVED" -> "Draft saved";
        case "DRAFT_DISCARDED" -> "Draft discarded";
        case "PUBLISHED" -> "Published";
        default -> eventType;
      };
    }
  }

  /** One difference between two versions. */
  @Getter
  @Setter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Change implements Serializable {
    private String kind;
    private String fieldKey;
    private String label;
    private String what;
  }

  /** What differs between two versions. */
  @Getter
  @Setter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Diff implements Serializable {
    private Long fromId;
    private String fromVersion;
    private Long toId;
    private String toVersion;
    private List<Change> changes = new ArrayList<>();
  }

  /** A choice in a list. */
  @Getter
  @Setter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Option implements Serializable {
    private Long id;
    private String code;
    private String label;
  }

  /** The lists the screen needs. */
  @Getter
  @Setter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Options implements Serializable {
    private List<Option> dataTypes = new ArrayList<>();
    private List<Option> contractTypes = new ArrayList<>();
  }

  /**
   * The answer of save, publish and create; problems are shown to the administrator as they are.
   */
  @Getter
  @Setter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Result implements Serializable {
    private List<String> problems = new ArrayList<>();
    private Detail detail;

    /** Set by the UI service, not by elcm-service. */
    private PackageOutcome outcome = PackageOutcome.OK;

    public static Result of(final PackageOutcome outcome) {
      final Result r = new Result();
      r.outcome = outcome;
      return r;
    }

    public boolean isOk() {
      return outcome == PackageOutcome.OK && (problems == null || problems.isEmpty());
    }
  }

  /** "2026-10-14 21:30" from an ISO time, or a dash. */
  static String timeText(final String iso) {
    if (iso == null || iso.length() < 16) {
      return "-";
    }
    return iso.substring(0, 16).replace('T', ' ');
  }

  private static boolean notBlank(final String s) {
    return s != null && !s.isBlank();
  }
}
