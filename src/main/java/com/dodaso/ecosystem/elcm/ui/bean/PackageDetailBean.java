package com.dodaso.ecosystem.elcm.ui.bean;

import com.dodaso.ecosystem.elcm.ui.service.pipeline.ActivityEntryRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.ActivityPageRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.PackageDetailRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.PackageDetailUiService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * ADDED 2026-10-25. Backing bean of the Package Details screen (packagedetails.xhtml): one
 * contract package with its documents, submissions and activity log. Read only; every action
 * stays on the Dashboard and in the review dialog, which this page links to. The package is named
 * by the "package" request parameter (the id). elcm-service decides who may see it: a package that
 * does not exist, or that the user may not see, gives a message instead of the details.
 */
@Named
@ViewScoped
@Getter
@RequiredArgsConstructor
public class PackageDetailBean implements Serializable {

  static final int ACTIVITY_PAGE_SIZE = 25;

  private final PackageDetailUiService uiService;

  private Long packageId;
  private PackageDetailRow detail;
  private PackageDetailUiService.Outcome outcome;

  private List<ActivityEntryRow> activity = new ArrayList<>();
  private int activityPage;
  private long activityTotal;
  private int activityPages;
  private boolean activityFailed;

  @PostConstruct
  void init() {
    final String raw = FacesContext.getCurrentInstance().getExternalContext()
        .getRequestParameterMap().get("package");
    try {
      packageId = raw == null || raw.isBlank() ? null : Long.valueOf(raw.trim());
    } catch (final NumberFormatException e) {
      packageId = null;
    }
    if (packageId == null) {
      outcome = PackageDetailUiService.Outcome.NOT_FOUND;
      return;
    }
    refresh();
  }

  /** Reads the package and the first page of its activity again. */
  public void refresh() {
    if (packageId == null) {
      return;
    }
    final PackageDetailUiService.Result result = uiService.detail(packageId);
    outcome = result.outcome();
    detail = result.detail();
    activityPage = 0;
    if (detail != null) {
      loadActivity();
    }
  }

  public void newer() {
    if (activityPage > 0) {
      activityPage--;
      loadActivity();
    }
  }

  public void older() {
    if (activityPage + 1 < activityPages) {
      activityPage++;
      loadActivity();
    }
  }

  public boolean isFound() {
    return detail != null;
  }

  public boolean isNotFound() {
    return outcome == PackageDetailUiService.Outcome.NOT_FOUND;
  }

  public boolean isForbidden() {
    return outcome == PackageDetailUiService.Outcome.FORBIDDEN;
  }

  public boolean isFailed() {
    return outcome == PackageDetailUiService.Outcome.FAILED;
  }

  public boolean isHasNewer() {
    return activityPage > 0;
  }

  public boolean isHasOlder() {
    return activityPage + 1 < activityPages;
  }

  /** "1 to 25 of 60". */
  public String getActivityRangeText() {
    if (activityTotal == 0 || activity.isEmpty()) {
      return "";
    }
    final long from = (long) activityPage * ACTIVITY_PAGE_SIZE + 1;
    return from + " to " + (from + activity.size() - 1) + " of " + activityTotal;
  }

  /** The tab title with the number of documents, such as "Documents (3)". */
  public String getDocumentsTitle() {
    return "Documents (" + (detail == null ? 0 : detail.getDocuments().size()) + ")";
  }

  public String getSubmissionsTitle() {
    return "Submissions (" + (detail == null ? 0 : detail.getSubmissions().size()) + ")";
  }

  private void loadActivity() {
    final ActivityPageRow page = uiService.activity(packageId, activityPage, ACTIVITY_PAGE_SIZE);
    if (page == null) {
      activityFailed = true;
      activity = new ArrayList<>();
      activityTotal = 0;
      activityPages = 0;
      return;
    }
    activityFailed = false;
    activity = page.getEntries() != null ? page.getEntries() : new ArrayList<>();
    activityTotal = page.getTotal();
    activityPages = page.getTotalPages();
  }
}
