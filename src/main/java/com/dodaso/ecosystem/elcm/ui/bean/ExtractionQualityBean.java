package com.dodaso.ecosystem.elcm.ui.bean;

import com.dodaso.ecosystem.elcm.ui.service.quality.ExtractionQualityUiService;
import com.dodaso.ecosystem.elcm.ui.service.quality.ExtractionQualityView;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Backing bean of the Extraction Quality screen (ADDED 2026-10-16): how accurate the extractor is,
 * measured from what reviewers kept and corrected. The report is read from elcm-service when the
 * page opens and again on Apply, so changing a filter needs the Apply button. Read only.
 */
@Named
@ViewScoped
@Getter
@Setter
public class ExtractionQualityBean extends BaseBean {

  private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;
  private static final int DEFAULT_DAYS = 90;

  @Autowired
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private ExtractionQualityUiService uiService;

  private Date from;
  private Date to;
  private String workspace = "";
  private String template = "";
  private String model = "";

  /** WEEK or MONTH. */
  private String granularity = "WEEK";

  private ExtractionQualityView report = new ExtractionQualityView();
  private boolean loadFailed;

  @PostConstruct
  void init() {
    to = new Date();
    from = new Date(to.getTime() - DEFAULT_DAYS * DAY_MILLIS);
    load();
  }

  /** Reads the report with the current filters. */
  public void load() {
    final ExtractionQualityView result =
        uiService.report(format(from), format(to), workspace, template, model, granularity);
    loadFailed = result == null;
    if (result != null) {
      report = result;
    }
  }

  /** Sets the date range to the last given number of days and reads the report. */
  public void lastDays(final int days) {
    to = new Date();
    from = new Date(to.getTime() - days * DAY_MILLIS);
    load();
  }

  /** Clears the workspace, template and model filters and reads the report. */
  public void clearFilters() {
    workspace = "";
    template = "";
    model = "";
    load();
  }

  /** True when nothing has been reviewed in the range, so there is nothing to measure. */
  public boolean isNothingReviewed() {
    return report.getSummary().getDecided() == 0 && report.getSummary().getFieldsTotal() == 0;
  }

  /** The trend rows, oldest first, for the bars. */
  public List<ExtractionQualityView.Stat> getTrendRows() {
    return report.getTrend() == null ? new ArrayList<>() : report.getTrend();
  }

  /** A percentage for display, or "n/a" when nothing could be measured. */
  public String pct(final Double value) {
    return value == null ? "n/a" : String.format(Locale.ROOT, "%.1f%%", value);
  }

  /** The height of a bar in percent, at least 2 so an empty bar is still visible. */
  public int barHeight(final Double value) {
    return value == null ? 2 : Math.max(2, (int) Math.round(value));
  }

  /** Average read time for display. */
  public String getAverageSeconds() {
    final Double seconds = report.getOperations().getAvgSeconds();
    return seconds == null ? "n/a" : String.format(Locale.ROOT, "%.0f s", seconds);
  }

  /** Colour class of an accuracy value: good from 95, fair from 85, otherwise poor. */
  public String tone(final Double value) {
    if (value == null) {
      return "q-none";
    }
    if (value >= 95.0) {
      return "q-good";
    }
    return value >= 85.0 ? "q-fair" : "q-poor";
  }

  private static String format(final Date date) {
    return date == null ? "" : new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(date);
  }
}
