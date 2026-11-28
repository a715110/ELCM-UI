package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import com.dodaso.ecosystem.elcm.ui.service.ShownTime;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ADDED 2026-10-20. One line of a package's Activity Log. Mirrors elcm-service's ActivityEntry.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActivityEntryRow implements Serializable {

  private Long id;
  private String occurredAt;
  private String actor;
  private String actorName;
  private String event;
  private String message;

  /** 2026-10-14T14:03:22.123Z shown as 2026-10-14 10:03 AM EDT (the viewer's zone). */
  public String getOccurredAtText() {
    if (occurredAt == null) {
      return "";
    }
    return ShownTime.text(occurredAt);
  }

  /** The same time in UTC, for exports. */
  public String getOccurredAtUtc() {
    return ShownTime.utc(occurredAt);
  }

  /** The display name, or the login id, or a dash. */
  public String getWho() {
    if (actorName != null && !actorName.isBlank()) {
      return actorName;
    }
    return actor != null && !actor.isBlank() ? actor : "-";
  }
}
