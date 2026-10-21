package com.dodaso.ecosystem.elcm.ui.bean;

import com.dodaso.ecosystem.elcm.ui.helper.CsvBuilder;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.ActivityEntryRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.ActivityPageRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.ContractPackageRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.ContractPackageService;
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
import lombok.extern.slf4j.Slf4j;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

/**
 * ADDED 2026-10-20. Backing bean for the Activity Log dialog of dashboard.xhtml: who did what to a
 * package and when, newest first, a page at a time, with a CSV of the whole log. The lines are read
 * from elcm-service, which reads them from EARS. A failed read shows a notice instead of breaking the
 * page. Times are UTC, like the rest of ELCM.
 */
@Named
@ViewScoped
@Getter
@Slf4j
@RequiredArgsConstructor
public class ActivityLogBean implements Serializable {

  static final int PAGE_SIZE = 25;
  private static final int CSV_PAGE_SIZE = 200;
  private static final int CSV_MAX_PAGES = 50;

  private final ContractPackageService contractPackageService;

  private ContractPackageRow activityPackage;
  private List<ActivityEntryRow> entries = new ArrayList<>();
  private int page;
  private long total;
  private int totalPages;
  private boolean loadFailed;

  /** Opens the log of one package at its newest page. */
  public void open(final ContractPackageRow row) {
    activityPackage = row;
    page = 0;
    load();
  }

  public void refresh() {
    load();
  }

  public void newer() {
    if (page > 0) {
      page--;
      load();
    }
  }

  public void older() {
    if (page + 1 < totalPages) {
      page++;
      load();
    }
  }

  public boolean isHasNewer() {
    return page > 0;
  }

  public boolean isHasOlder() {
    return page + 1 < totalPages;
  }

  public String getPackageCode() {
    return activityPackage == null ? "" : activityPackage.getPackageCode();
  }

  /** "1 to 25 of 60". */
  public String getRangeText() {
    if (total == 0 || entries.isEmpty()) {
      return "";
    }
    final long from = (long) page * PAGE_SIZE + 1;
    return from + " to " + (from + entries.size() - 1) + " of " + total;
  }

  private void load() {
    if (activityPackage == null) {
      return;
    }
    final ActivityPageRow result = contractPackageService.findActivity(activityPackage.getId(), page, PAGE_SIZE);
    if (result == null) {
      loadFailed = true;
      entries = new ArrayList<>();
      total = 0;
      totalPages = 0;
      return;
    }
    loadFailed = false;
    entries = result.getEntries() != null ? result.getEntries() : new ArrayList<>();
    total = result.getTotal();
    totalPages = result.getTotalPages();
  }

  /** CSV of the whole log of the open package, newest first. Times are UTC. */
  public StreamedContent getActivityCsv() {
    final CsvBuilder csv = new CsvBuilder().row("Package", "When (UTC)", "Who", "Login", "Event", "Details");
    final String code = getPackageCode();
    if (activityPackage != null) {
      for (int p = 0; p < CSV_MAX_PAGES; p++) {
        final ActivityPageRow chunk = contractPackageService.findActivity(activityPackage.getId(), p, CSV_PAGE_SIZE);
        if (chunk == null || chunk.getEntries() == null || chunk.getEntries().isEmpty()) {
          break;
        }
        for (final ActivityEntryRow a : chunk.getEntries()) {
          csv.row(code, a.getOccurredAtText(), a.getWho(), a.getActor(), a.getEvent(), a.getMessage());
        }
        if (p + 1 >= chunk.getTotalPages()) {
          break;
        }
      }
    }
    final byte[] bytes = csv.toBytes();
    return DefaultStreamedContent.builder()
        .name("activity-" + safeName(code) + "-" + LocalDate.now(ZoneOffset.UTC) + ".csv")
        .contentType("text/csv; charset=UTF-8")
        .stream(() -> new ByteArrayInputStream(bytes))
        .build();
  }

  private static String safeName(final String code) {
    return code == null || code.isBlank() ? "package" : code.replaceAll("[^A-Za-z0-9._-]", "_");
  }
}
