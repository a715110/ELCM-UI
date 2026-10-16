package com.dodaso.ecosystem.elcm.ui.service.quality;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The Extraction Quality report as the screen reads it (ADDED 2026-10-16), mirroring
 * elcm-service's ExtractionQualityReport (same field names, so the JSON maps one to one). A
 * percentage is null when there is nothing to measure yet.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtractionQualityView implements Serializable {

  private Summary summary = new Summary();
  private List<Stat> trend = new ArrayList<>();
  private List<Stat> byTemplate = new ArrayList<>();
  private List<Stat> byModel = new ArrayList<>();
  private List<Stat> byWorkspace = new ArrayList<>();
  private List<Stat> fields = new ArrayList<>();
  private List<Stat> confidence = new ArrayList<>();
  private Operations operations = new Operations();
  private Options options = new Options();

  /** The headline numbers. */
  @Getter
  @Setter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Summary implements Serializable {
    private long documents;
    private long fieldsTotal;
    private long decided;
    private long accepted;
    private long corrected;
    private long missed;
    private long notFound;
    private long unconfirmed;
    private Double accuracyPct;
    private long criticalDecided;
    private Double criticalAccuracyPct;
    private long flaggedDecided;
    private Double flaggedWrongPct;
  }

  /** One row of a breakdown. */
  @Getter
  @Setter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Stat implements Serializable {
    private String label;
    private long decided;
    private long accepted;
    private long corrected;
    private long missed;
    private Double accuracyPct;
    private long criticalDecided;
    private Double criticalAccuracyPct;
    private boolean critical;
  }

  /** Job and review counts. */
  @Getter
  @Setter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Operations implements Serializable {
    private long jobs;
    private long completed;
    private long failed;
    private long retried;
    private Double avgSeconds;
    private long reviewed;
    private long declined;
  }

  /** The values of the filter lists. */
  @Getter
  @Setter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Options implements Serializable {
    private List<String> workspaces = new ArrayList<>();
    private List<String> templates = new ArrayList<>();
    private List<String> models = new ArrayList<>();
  }
}
