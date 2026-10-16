package com.dodaso.ecosystem.elcm.ui.bean;

import com.dodaso.ecosystem.elcm.ui.service.pipeline.WorkQueueRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.WorkQueueUiService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.model.SelectItem;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * Backing bean of the Work Queue screen (ADDED 2026-10-14): the submitted packages across all
 * workspaces, split by stage. Reviewers use "Ready for review", approvers use "Awaiting approval".
 * The filters (stage tab, assigned to me, workspace, search text) work on the list already loaded,
 * so changing a filter is instant; Refresh reads it again.
 *
 * <p>"Assigned to me" compares the package assignee with the signed-in login id. The queue shows
 * every package the user may see, so people with the review or approve role can also pick up work
 * that is not assigned to them.
 */
@Named
@ViewScoped
@Getter
@NoArgsConstructor(force = true) // Allows CDI proxy creation
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class WorkQueueBean extends BaseBean {

  /** Tab value that shows every stage. */
  static final String ALL = "ALL";

  /** Tab shown first. */
  static final String DEFAULT_TAB = "REVIEW";

  private static final String[][] TABS = {
    {"EXTRACTING", "Extracting"},
    {"REVIEW", "Ready for review"},
    {"APPROVAL", "Awaiting approval"},
    {"FAILED", "Failed"},
    {"APPLIED", "Applied"},
    {ALL, "All"}
  };

  private final WorkQueueUiService workQueueUiService;

  private List<WorkQueueRow> all = new ArrayList<>();
  private boolean loadFailed;

  @Setter private String tab = DEFAULT_TAB;
  @Setter private boolean mineOnly;
  @Setter private String workspace = "";
  @Setter private String query = "";

  /**
   * REVISED 2026-10-16: reads the optional request parameters "tab" and "mine" that the dashboard
   * cards send, so a click on a card opens the matching tab (and "assigned to me" when Mine only
   * is on). An unknown tab value is ignored and the default tab stays.
   */
  @PostConstruct
  void init() {
    final Map<String, String> params = getRequestParamMap();
    if (params != null) {
      final String requestedTab = params.get("tab");
      for (final String[] t : TABS) {
        if (t[0].equals(requestedTab)) {
          tab = requestedTab;
        }
      }
      mineOnly = "true".equalsIgnoreCase(params.get("mine"));
    }
    refresh();
  }

  public void refresh() {
    final List<WorkQueueRow> rows = workQueueUiService.queue();
    loadFailed = rows == null;
    all = rows == null ? new ArrayList<>() : rows;
  }

  /** The rows of the current tab after the other filters. */
  public List<WorkQueueRow> getRows() {
    final List<WorkQueueRow> out = new ArrayList<>();
    for (final WorkQueueRow r : all) {
      if (inTab(r, tab) && passesFilters(r)) {
        out.add(r);
      }
    }
    return out;
  }

  /** The tab buttons, each with the number of rows it would show under the other filters. */
  public List<SelectItem> getTabItems() {
    final List<SelectItem> items = new ArrayList<>();
    for (final String[] t : TABS) {
      int n = 0;
      for (final WorkQueueRow r : all) {
        if (inTab(r, t[0]) && passesFilters(r)) {
          n++;
        }
      }
      items.add(new SelectItem(t[0], t[1] + " (" + n + ")"));
    }
    return items;
  }

  /** The workspaces present in the queue, for the filter dropdown. */
  public List<String> getWorkspaces() {
    final TreeSet<String> set = new TreeSet<>();
    for (final WorkQueueRow r : all) {
      if (r.getWorkspace() != null) {
        set.add(r.getWorkspace());
      }
    }
    return new ArrayList<>(set);
  }

  /** True when the row is assigned to the signed-in user. */
  public boolean isMine(final WorkQueueRow row) {
    final String assignee = row.getAssigneeLoginId();
    if (assignee == null) {
      return false;
    }
    final String authenticated = userHelper.getCurrentLoginId();
    return assignee.equalsIgnoreCase(getLoginId())
        || (authenticated != null && assignee.equalsIgnoreCase(authenticated));
  }

  /** The button text on a row: what the viewer would do there. */
  public String actionLabel(final WorkQueueRow row) {
    return switch (row.getStageCode()) {
      case "REVIEW" -> "Review";
      case "APPROVAL" -> "Approve";
      case "FAILED" -> "Look at it";
      default -> "Open";
    };
  }

  private boolean inTab(final WorkQueueRow row, final String which) {
    return ALL.equals(which) || which != null && which.equals(row.getStageCode());
  }

  private boolean passesFilters(final WorkQueueRow row) {
    if (mineOnly && !isMine(row)) {
      return false;
    }
    if (workspace != null && !workspace.isBlank() && !workspace.equals(row.getWorkspace())) {
      return false;
    }
    if (query == null || query.isBlank()) {
      return true;
    }
    final String needle = query.trim().toLowerCase(Locale.ROOT);
    return contains(row.getPackageCode(), needle)
        || contains(row.getTargetRecord(), needle)
        || contains(row.getAssignee(), needle)
        || contains(row.getSubmittedBy(), needle);
  }

  private static boolean contains(final String value, final String needle) {
    return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
  }
}
