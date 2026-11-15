package com.dodaso.ecosystem.elcm.ui.service.perf;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * ADDED 2026-11-12. Row shapes of the Performance Incidents page. Times arrive as text (ISO, UTC)
 * so no date module is needed on this side. Unknown fields are ignored by the REST client mapper
 * configuration used for the other pages; every field here exists in common-service's answer.
 */
public final class PerfRows {

  private PerfRows() {
  }

  /** One incident. The stack is only filled by the detail call. */
  @Getter
  @Setter
  public static class Incident implements Serializable {
    private Long id;
    private String occurredAt;
    private String service;
    private String reason;
    private String layer;
    private String className;
    private String methodName;
    private String kind;
    private Long ownWallMs;
    private Long ownCpuMs;
    private Long wallMs;
    private Long cpuMs;
    private String outcome;
    private String threadName;
    private String requestId;
    private String userLogin;
    private String callChain;
    private String stackText;

    /** Time for the table: date and minutes, T replaced by a space. */
    public String getOccurredAtText() {
      if (occurredAt == null) {
        return "";
      }
      final String t = occurredAt.replace('T', ' ');
      return t.length() > 19 ? t.substring(0, 19) : t;
    }

    /** Short name of the class with its method. */
    public String getCallName() {
      final String c = className == null ? "" : className;
      final int dot = c.lastIndexOf('.');
      return (dot >= 0 ? c.substring(dot + 1) : c) + "." + methodName;
    }
  }

  /** One page of incidents. */
  @Getter
  @Setter
  public static class IncidentPage implements Serializable {
    private List<Incident> items = new ArrayList<>();
    private long total;
    private int page;
    private int size;
  }

  /** One line of the top calls summary. */
  @Getter
  @Setter
  public static class TopCall implements Serializable {
    private String className;
    private String methodName;
    private long incidents;
    private long totalOwnCpuMs;
    private long maxOwnWallMs;

    public String getCallName() {
      final String c = className == null ? "" : className;
      final int dot = c.lastIndexOf('.');
      return (dot >= 0 ? c.substring(dot + 1) : c) + "." + methodName;
    }
  }
}
