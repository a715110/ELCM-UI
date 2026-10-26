package com.dodaso.ecosystem.elcm.ui.bean;

import com.dodaso.ecosystem.elcm.ui.service.pipeline.PackageListPageRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.PackageListRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.PackageListUiService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * ADDED 2026-10-25. Backing bean of the Contract Packages list (contractpackages.xhtml): the
 * packages the signed-in user may see, in two tabs, Open (every status except completed) and
 * Closed (completed), 20 at a time with a "Load more" button, newest change first. "Mine" is the
 * packages the user is assigned to, created, submitted or reviewed; "All" is for administrators,
 * reviewers and approvers, and the switch is hidden for everyone else. The rule is applied by
 * elcm-service (the same read as the Package Rail in ECWS). The text filter works on the lines
 * already loaded. Each line opens the Package Details screen.
 */
@Named
@ViewScoped
@Getter
@RequiredArgsConstructor
public class PackageListBean implements Serializable {

  static final String TAB_OPEN = "OPEN";
  static final String TAB_CLOSED = "CLOSED";

  private final PackageListUiService uiService;

  @Setter private String tab = TAB_OPEN;

  /** True for "Mine" (the default), false for "All". */
  private boolean mineOnly = true;

  @Setter private String filter = "";

  private boolean canSeeAll;

  private List<PackageListRow> openItems = new ArrayList<>();
  private boolean openFailed;
  private int openTotal;
  private Integer openNext;

  private List<PackageListRow> closedItems = new ArrayList<>();
  private boolean closedLoaded;
  private boolean closedFailed;
  private int closedTotal;
  private Integer closedNext;

  /** The Mine and All switch as the page uses it: MINE or ALL. */
  public String getScope() {
    return mineOnly ? "MINE" : "ALL";
  }

  public void setScope(final String scope) {
    mineOnly = !"ALL".equals(scope);
  }

  @PostConstruct
  void init() {
    loadOpen();
  }

  /** Reads the shown tab again from the start. */
  public void refresh() {
    if (isClosedTab()) {
      loadClosed();
    } else {
      loadOpen();
    }
  }

  /** The tab changed: the Closed list is read the first time its tab is shown, and not before. */
  public void tabChanged() {
    if (isClosedTab()) {
      if (!closedLoaded) {
        loadClosed();
      }
    }
  }

  /** The Mine and All switch changed: both lists are read again, the shown one now. */
  public void scopeChanged() {
    closedLoaded = false;
    closedItems = new ArrayList<>();
    closedNext = null;
    loadOpen();
    if (isClosedTab()) {
      loadClosed();
    }
  }

  public void loadMore() {
    if (isClosedTab()) {
      if (closedNext != null) {
        final PackageListPageRow page = uiService.closed(mineOnly, closedItems.size());
        if (page == null) {
          closedFailed = true;
          return;
        }
        closedFailed = false;
        closedItems.addAll(page.getItems());
        closedNext = page.getNextOffset();
        closedTotal = totalOf(page, closedItems);
      }
    } else if (openNext != null) {
      final PackageListPageRow page = uiService.open(mineOnly, openItems.size());
      if (page == null) {
        openFailed = true;
        return;
      }
      openFailed = false;
      openItems.addAll(page.getItems());
      openNext = page.getNextOffset();
      openTotal = totalOf(page, openItems);
    }
  }

  public boolean isClosedTab() {
    return TAB_CLOSED.equals(tab);
  }

  public boolean isFailedShown() {
    return isClosedTab() ? closedFailed : openFailed;
  }

  public boolean isHasMore() {
    return isClosedTab() ? closedNext != null : openNext != null;
  }

  /** The lines of the shown tab that match the text filter. */
  public List<PackageListRow> getRows() {
    final List<PackageListRow> source = isClosedTab() ? closedItems : openItems;
    final String needle = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
    if (needle.isEmpty()) {
      return source;
    }
    final List<PackageListRow> out = new ArrayList<>();
    for (final PackageListRow row : source) {
      if (contains(row.getPackageCode(), needle) || contains(row.getWorkspace(), needle)
          || contains(row.getAssignee(), needle) || contains(row.getStatusLabel(), needle)) {
        out.add(row);
      }
    }
    return out;
  }

  /** "Showing 20 of 37", for the line under the table. */
  public String getCountText() {
    final int shown = isClosedTab() ? closedItems.size() : openItems.size();
    final int total = isClosedTab() ? closedTotal : openTotal;
    return "Showing " + shown + " of " + total;
  }

  public String getOpenTitle() {
    return openFailed ? "Open" : "Open (" + openTotal + ")";
  }

  public String getClosedTitle() {
    return closedLoaded && !closedFailed ? "Closed (" + closedTotal + ")" : "Closed";
  }

  private void loadOpen() {
    final PackageListPageRow page = uiService.open(mineOnly, 0);
    if (page == null) {
      openFailed = true;
      return;
    }
    openFailed = false;
    openItems = new ArrayList<>(page.getItems());
    openNext = page.getNextOffset();
    openTotal = totalOf(page, openItems);
    canSeeAll = page.isCanSeeAll();
    if (!canSeeAll && !mineOnly) {
      // The user may not use All: show Mine and read again.
      mineOnly = true;
      loadOpen();
    }
  }

  private void loadClosed() {
    final PackageListPageRow page = uiService.closed(mineOnly, 0);
    if (page == null) {
      closedFailed = true;
      return;
    }
    closedFailed = false;
    closedLoaded = true;
    closedItems = new ArrayList<>(page.getItems());
    closedNext = page.getNextOffset();
    closedTotal = totalOf(page, closedItems);
    canSeeAll = page.isCanSeeAll();
  }

  private static boolean contains(final String text, final String needle) {
    return text != null && text.toLowerCase(Locale.ROOT).contains(needle);
  }

  private static int totalOf(final PackageListPageRow page, final List<PackageListRow> shown) {
    return page.getTotal() == null ? shown.size() : page.getTotal();
  }
}
