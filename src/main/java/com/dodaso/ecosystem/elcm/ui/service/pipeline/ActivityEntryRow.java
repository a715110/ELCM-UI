package com.dodaso.ecosystem.elcm.ui.service.pipeline;

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

  /** 2026-10-14T14:03:22.123Z shown as 2026-10-14 14:03 UTC. */
  public String getOccurredAtText() {
    if (occurredAt == null) {
      return "";
    }
    final String t = occurredAt.replace('T', ' ');
    return (t.length() >= 16 ? t.substring(0, 16) : t) + " UTC";
  }

  /** The display name, or the login id, or a dash. */
  public String getWho() {
    if (actorName != null && !actorName.isBlank()) {
      return actorName;
    }
    return actor != null && !actor.isBlank() ? actor : "-";
  }
}
