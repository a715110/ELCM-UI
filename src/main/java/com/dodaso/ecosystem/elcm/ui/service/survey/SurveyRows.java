package com.dodaso.ecosystem.elcm.ui.service.survey;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** ADDED 2026-11-13. Row shapes of the Survey Builder page; they mirror elcm-service's answers. */
public final class SurveyRows {

  private SurveyRows() {
  }

  @Getter
  @Setter
  public static class TemplateRow implements Serializable {
    private Long id;
    private String code;
    private String name;
    private String description;
    private boolean published;
    private long questionCount;
    private long sentCount;

    public String getStateLabel() {
      return published ? "Published" : "Draft";
    }
  }

  @Getter
  @Setter
  public static class QuestionRow implements Serializable {
    private Long id;
    private int sortOrder;
    private String text;
    private String type;
    private String typeLabel;
    private String choices;
    private boolean required;
    private boolean active;

    /** The choices as a list, for the preview. */
    public List<String> getChoiceList() {
      final List<String> out = new ArrayList<>();
      if (choices != null) {
        for (final String line : choices.split("\\R")) {
          if (!line.isBlank()) {
            out.add(line.trim());
          }
        }
      }
      return out;
    }
  }

  @Getter
  @Setter
  public static class TemplateDetail implements Serializable {
    private TemplateRow summary;
    private List<QuestionRow> questions = new ArrayList<>();
  }

  /** One answer type in the type menu. */
  @Getter
  public static class TypeOption implements Serializable {
    private final String code;
    private final String label;

    public TypeOption(final String code, final String label) {
      this.code = code;
      this.label = label;
    }
  }
}
