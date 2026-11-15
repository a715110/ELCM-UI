package com.dodaso.ecosystem.elcm.ui.bean;

import com.dodaso.ecosystem.elcm.ui.service.perf.PerfIncidentUiService;
import com.dodaso.ecosystem.elcm.ui.service.perf.PerfRows;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * ADDED 2026-11-12. Performance Incidents page: filters, a page of incidents, the detail with the
 * stack, and the calls that used the most CPU in the last 24 hours. Incidents come from every app
 * that runs the perf monitor with the store switched on (ELCM, ECWS, EARS).
 */
@Named
@ViewScoped
@Getter
@RequiredArgsConstructor
public class PerfIncidentsBean implements Serializable {

  private static final int PAGE_SIZE = 25;

  private final PerfIncidentUiService service;

  @Setter
  private String serviceFilter;
  @Setter
  private String reasonFilter;
  @Setter
  private String callFilter;
  @Setter
  private LocalDate fromDate;
  @Setter
  private LocalDate toDate;
  @Setter
  private Long minMs;

  private List<PerfRows.Incident> items = new ArrayList<>();
  private List<PerfRows.TopCall> topCalls = new ArrayList<>();
  private PerfRows.Incident selected;
  private long total;
  private int page;
  private boolean loadFailed;

  @PostConstruct
  void init() {
    search();
  }

  /** Runs the filter from the first page and refreshes the summary. */
  public void search() {
    page = 0;
    load();
    final List<PerfRows.TopCall> top = service.top(24, 10);
    topCalls = top == null ? new ArrayList<>() : top;
  }

  public void next() {
    if (hasNext()) {
      page++;
      load();
    }
  }

  public void previous() {
    if (page > 0) {
      page--;
      load();
    }
  }

  public boolean hasNext() {
    return (long) (page + 1) * PAGE_SIZE < total;
  }

  public boolean hasPrevious() {
    return page > 0;
  }

  /** Text like "1 to 25 of 140". */
  public String getRangeText() {
    if (total == 0) {
      return "No incidents";
    }
    return (page * PAGE_SIZE + 1) + " to " + Math.min(total, (long) (page + 1) * PAGE_SIZE)
        + " of " + total;
  }

  /** Loads one incident with its stack for the detail dialog. */
  public void select(final Long id) {
    selected = service.detail(id);
  }

  private void load() {
    final LocalDateTime from = fromDate == null ? null : fromDate.atStartOfDay();
    final LocalDateTime to = toDate == null ? null : toDate.plusDays(1).atStartOfDay();
    final PerfRows.IncidentPage result = service.list(serviceFilter, reasonFilter, callFilter, from,
        to, minMs, page, PAGE_SIZE);
    loadFailed = result == null;
    items = result == null ? new ArrayList<>() : result.getItems();
    total = result == null ? 0 : result.getTotal();
  }
}
