package com.dodaso.ecosystem.elcm.ui.bean;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

/**
 * Backing bean for the right-edge "Task Rail" (WEB-INF/taskrail.xhtml). the
 * hover-to-expand icon panel included from template.xhtml on every page,
 * giving one-click access to ECWS collaboration-task functions from anywhere
 * in ELCM (mirrors the collapsed-icon-rail pattern used by Smartsheet and
 * similar enterprise apps).
 *
 * ECWS is a separate application from ELCM (separate WAR/context-path, see
 * the nginx /ecws vs /elcm path routing set up earlier), so rail items don't
 * navigate via JSF outcomes. they resolve to absolute ECWS URLs
 * (ecwsBaseUrl + route) and open in a new browser tab (see
 * target="_blank" in taskrail.xhtml). ecwsBaseUrl comes from the
 * `ecws.base-url` property (application-local.yml locally; each
 * adev/bdev/dit/sit/uat/prod profile has a CHANGE_ME placeholder for its
 * real ECWS host. see the TODO added to each).
 *
 * Only routes that have actually been confirmed against a real ECWS page
 * are wired up (route != null); everything else renders disabled in the UI
 * rather than as a dead/guessed link. see TaskRailItem.isAvailable() and
 * taskrail.xhtml. As more ECWS routes are confirmed, add them to init()
 * below.
 *
 * ALL badge counts here are hardcoded placeholder data, not from a real
 * service. there is no persistence layer wired up yet (see DashboardBean's
 * MINIMUM-JARS note). Replace with a real query against CollaborationTaskDTO
 * / EventLogDTO (see BaseBean) once the ECWS task service is available from
 * this UI.
 *
 * Scope: SessionScoped rather than ViewScoped/RequestScoped because
 * template.xhtml (and therefore this include) renders on every view in the
 * app, and the rail's expanded/collapsed state and badge counts should
 * persist across page navigation within a session rather than reset on
 * every view change.
 */
@Named
@SessionScoped
@Getter
@Setter
@Slf4j
public class TaskRailBean implements Serializable {

  /** e.g. "https://localhost/ecws" locally.see ecws.base-url in
   * application-*.yml. No trailing slash. */
  @Value("${ecws.base-url}")
  private String ecwsBaseUrl;

  private List<TaskRailItem> railItems;

  /** Whether the rail should render pinned open instead of hover-collapsed
   * (a user preference toggle raised via the pin icon in taskrail.xhtml). */
  private boolean pinned = false;

  @PostConstruct
  private void init() {
    railItems = new ArrayList<>();

    // CONFIRMED: dashboard.xhtml is served at /ecws/dashboard (rewrite-clean
    // URL, no .xhtml suffix). My Tasks points here for now per instruction;
    // swap for a real /ecws/tasks/... route once that view exists.
    railItems.add(new TaskRailItem(
        "myTasks", "My Tasks", "pi pi-check-square",
        "Tasks assigned to you across all contract packages",
        "/dashboard", 4));

    // NOT YET CONFIRMED -- route left null so the item renders disabled
    // (see TaskRailItem.isAvailable()) instead of linking to a guessed,
    // possibly-nonexistent ECWS path. Fill in route once confirmed, e.g.:
    //   railItems.add(new TaskRailItem("taskInbox", "Task Inbox",
    //       "pi pi-inbox", "...", "/tasks/inbox", 7));
    railItems.add(new TaskRailItem(
        "taskInbox", "Task Inbox", "pi pi-inbox",
        "All incoming collaboration tasks awaiting action",
        null, 7));
    railItems.add(new TaskRailItem(
        "createTask", "Create Task", "pi pi-plus-circle",
        "Start a new ad-hoc collaboration task", null, 0));
    railItems.add(new TaskRailItem(
        "approvals", "Approvals Queue", "pi pi-thumbs-up",
        "Items routed to you for review/approval", null, 2));
    railItems.add(new TaskRailItem(
        "reassign", "Reassign Task", "pi pi-user-plus",
        "Reassign a task to another user or role", null, 0));
    railItems.add(new TaskRailItem(
        "activityLog", "Activity Log", "pi pi-sitemap",
        "Recent task activity and status changes", null, 0));
    railItems.add(new TaskRailItem(
        "notifications", "Notifications", "pi pi-bell",
        "Task-related notifications", null, 3));
  }

  public int getTotalBadgeCount() {
    return railItems.stream().mapToInt(TaskRailItem::getBadgeCount).sum();
  }

  public void togglePinned() {
    pinned = !pinned;
  }

  @Getter
  @Setter
  @AllArgsConstructor
  @NoArgsConstructor
  public static class TaskRailItem implements Serializable {

    private String id;
    private String label;
    private String icon;        // PrimeIcons class, e.g. "pi pi-inbox"
    private String description; // shown under the label in the expanded rail
    private String route;       // path segment under ECWS, e.g. "/dashboard"; null = not yet confirmed
    private int badgeCount;     // 0 = no badge rendered

    public boolean isAvailable() {
      return route != null;
    }
  }

  /** Absolute ECWS URL for the given item, or null if it has no confirmed
   * route yet (taskrail.xhtml renders those disabled rather than linking). */
  public String getEcwsUrl(final TaskRailItem item) {
    if (item == null || item.getRoute() == null) {
      return null;
    }
    return ecwsBaseUrl + item.getRoute();
  }
}
