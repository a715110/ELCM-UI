package com.dodaso.ecosystem.elcm.ui.bean;

import com.dodaso.ecosystem.elcm.ui.helper.CsvBuilder;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.CriticalDateUiService;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.CriticalDateView;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.CriticalDateView.DateRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.CriticalDateView.RuleRow;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

/**
 * Backing bean of the Critical Dates screen (ADDED 2026-11-16): the rules that decide which contract
 * dates become ECWS tasks, and the dates being watched with where each one stands. Read only.
 */
@Named
@ViewScoped
@Getter
@RequiredArgsConstructor
public class CriticalDatesBean implements Serializable {

  private final CriticalDateUiService criticalDateUiService;

  private boolean loadFailed;
  private boolean enabled;
  private boolean ecwsEnabled;
  private String cron = "";
  private String zone = "";
  private String today = "";
  private List<RuleRow> rules = new ArrayList<>();
  private List<DateRow> dates = new ArrayList<>();

  /** Status filter: empty for all, else WAITING, READY, TASK_REQUESTED, OVERDUE or PAST. */
  @Setter
  private String statusFilter = "";

  @PostConstruct
  void init() {
    reload();
  }

  public void reload() {
    final CriticalDateView view = criticalDateUiService.overview();
    loadFailed = view == null;
    if (view == null) {
      return;
    }
    enabled = view.isEnabled();
    ecwsEnabled = view.isEcwsEnabled();
    cron = view.getCron();
    zone = view.getZone();
    today = view.getToday();
    rules = view.getRules() == null ? new ArrayList<>() : view.getRules();
    dates = view.getDates() == null ? new ArrayList<>() : view.getDates();
  }

  /** The dates the filter lets through. */
  public List<DateRow> getShownDates() {
    if (statusFilter == null || statusFilter.isBlank()) {
      return dates;
    }
    final List<DateRow> shown = new ArrayList<>();
    for (final DateRow row : dates) {
      if (statusFilter.equals(row.getStatus())) {
        shown.add(row);
      }
    }
    return shown;
  }

  public long countOf(final String status) {
    return dates.stream().filter(d -> status.equals(d.getStatus())).count();
  }

  /** True when the feature will create tasks: both switches are on. */
  public boolean isRunning() {
    return enabled && ecwsEnabled;
  }

  /** Plain words for the daily check time, from the cron text (minute, hour in fields 2 and 3). */
  public String getCheckTimeText() {
    final String[] f = cron == null ? new String[0] : cron.trim().split("\\s+");
    if (f.length >= 3 && f[1].matches("\\d+") && f[2].matches("\\d+")) {
      return String.format("%02d:%02d", Integer.parseInt(f[2]), Integer.parseInt(f[1]))
          + " (" + zone + ")";
    }
    return cron + " (" + zone + ")";
  }

  /** CSV of the dates the table shows now (the filter applies). */
  public StreamedContent getDatesCsv() {
    final CsvBuilder csv = new CsvBuilder().row("Record", "Counterparty", "Rule", "Contract date",
        "Notice days", "Due date", "Task made from", "Days left", "Status");
    for (final DateRow r : getShownDates()) {
      csv.row(r.getRecordCode(), r.getCounterparty(), r.getRuleLabel(), r.getContractDate(),
          String.valueOf(r.getNoticeDays()), r.getDueDate(), r.getCreateOn(),
          String.valueOf(r.getDaysLeft()), r.getStatusText());
    }
    final byte[] bytes = csv.toBytes();
    return DefaultStreamedContent.builder()
        .name("critical-dates-" + LocalDate.now(ZoneOffset.UTC) + ".csv")
        .contentType("text/csv; charset=UTF-8")
        .stream(() -> new ByteArrayInputStream(bytes))
        .build();
  }
}
