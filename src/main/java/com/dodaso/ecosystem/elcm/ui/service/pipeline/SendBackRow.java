package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One time an approver sent the review back, with the reason. Mirrors elcm-service's SendBackEntry.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SendBackRow implements Serializable {
  private Long id;
  private String reason;
  private String sentBy;
  private String sentAt;

  /** 2026-10-14T14:03:22.123 shown as 2026-10-14 14:03 UTC. */
  public String getSentAtText() {
    if (sentAt == null) {
      return "";
    }
    final String t = sentAt.replace('T', ' ');
    return (t.length() >= 16 ? t.substring(0, 16) : t) + " UTC";
  }
}
