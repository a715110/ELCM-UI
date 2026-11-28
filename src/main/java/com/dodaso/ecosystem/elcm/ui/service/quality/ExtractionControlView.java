package com.dodaso.ecosystem.elcm.ui.service.quality;

import com.dodaso.ecosystem.elcm.ui.service.ShownTime;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ADDED 2026-10-18 -- what elcm-service says about the extraction agent: paused or not (by whom,
 * since when, why), how many packages wait or are being read, and the latest control events.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtractionControlView implements Serializable {

  private boolean paused;
  private String pausedBy;
  private String pausedAt;
  private String reason;
  private long queued;
  private long processing;
  private List<Event> recent = new ArrayList<>();

  /** One line of the control log. */
  @Getter
  @Setter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Event implements Serializable {
    private String action;
    private Long packageId;
    private String actor;
    private String reason;
    private String at;

    /** Plain words for the action. */
    public String getActionText() {
      if (action == null) {
        return "";
      }
      return switch (action) {
        case "PAUSE" -> "Paused";
        case "RESUME" -> "Resumed";
        case "STOP" -> "Stopped package " + packageId;
        default -> action;
      };
    }

    /** The time without the fraction of a second, "T" replaced by a space (shown in your time zone). */
    public String getAtText() {
      if (at == null) {
        return "";
      }
      return ShownTime.text(at);
    }
  }

  /** The pause time for display, "2026-10-18 05:30 AM EDT". */
  public String getPausedAtText() {
    if (pausedAt == null) {
      return "";
    }
    return ShownTime.text(pausedAt);
  }
}
