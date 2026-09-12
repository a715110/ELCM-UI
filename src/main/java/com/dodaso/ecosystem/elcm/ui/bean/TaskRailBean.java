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

/**
 * Backing bean for the right-edge "Task Rail" (WEB-INF/taskrail.xhtml) -- the
 * hover-to-expand icon panel included from template.xhtml on every page,
 * giving one-click access to ECWS collaboration-task functions from anywhere
 * in ELCM (mirrors the collapsed-icon-rail pattern used by Smartsheet and
 * similar enterprise apps).
 *
 * ALL data here is hardcoded placeholder data, not from a real service --
 * there is no persistence layer wired up yet (see DashboardBean's
 * MINIMUM-JARS note). badgeCount values are static placeholders; replace
 * getRailItems() with a real query against CollaborationTaskDTO /
 * EventLogDTO (see BaseBean) once the ECWS task service is available from
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

  private List<TaskRailItem> railItems;

  /** Whether the rail should render pinned open instead of hover-collapsed
   * (a user preference toggle raised via the pin icon in taskrail.xhtml). */
  private boolean pinned = false;

  @PostConstruct
  private void init() {
    railItems = new ArrayList<>();

    // TODO: replace outcome values with real ECWS routes/rewrite rules once
    //  the task-inbox / approvals views exist; these are placeholders
    //  matching the ACTION_SHORTCUTS convention already used in quick-menu.js.
    railItems.add(new TaskRailItem(
        "myTasks", "My Tasks", "pi pi-check-square",
        "Tasks assigned to you across all contract packages",
        "/tasks/my", 4));
    railItems.add(new TaskRailItem(
        "taskInbox", "Task Inbox", "pi pi-inbox",
        "All incoming collaboration tasks awaiting action",
        "/tasks/inbox", 7));
    railItems.add(new TaskRailItem(
        "createTask", "Create Task", "pi pi-plus-circle",
        "Start a new ad-hoc collaboration task", "/tasks/new", 0));
    railItems.add(new TaskRailItem(
        "approvals", "Approvals Queue", "pi pi-thumbs-up",
        "Items routed to you for review/approval", "/tasks/approvals", 2));
    railItems.add(new TaskRailItem(
        "reassign", "Reassign Task", "pi pi-user-plus",
        "Reassign a task to another user or role", "/tasks/reassign", 0));
    railItems.add(new TaskRailItem(
        "activityLog", "Activity Log", "pi pi-sitemap",
        "Recent task activity and status changes", "/tasks/activity", 0));
    railItems.add(new TaskRailItem(
        "notifications", "Notifications", "pi pi-bell",
        "Task-related notifications", "/tasks/notifications", 3));
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
    private String outcome;     // navigation target (h:link outcome / URL)
    private int badgeCount;     // 0 = no badge rendered
  }
}
