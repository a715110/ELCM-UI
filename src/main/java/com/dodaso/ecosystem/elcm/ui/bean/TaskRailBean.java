package com.dodaso.ecosystem.elcm.ui.bean;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.TaskRailPageRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.TaskRailRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.TaskRailUiService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import com.dodaso.ecosystem.baseline.common.profile.UserPreferences;
import com.dodaso.ecosystem.baseline.common.security.AuthenticationUtil;
import com.dodaso.ecosystem.baseline.common.urlparam.UrlCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * Backing bean for the right-edge "Task Rail" (WEB-INF/taskrail.xhtml) -- the
 * hover-to-expand icon panel included from template.xhtml on every page,
 * giving one-click access to ECWS collaboration-task functions from anywhere
 * in ELCM (mirrors the collapsed-icon-rail pattern used by Smartsheet and
 * similar enterprise apps).
 *
 * ECWS is a separate application from ELCM (separate WAR/context-path, see
 * the nginx /ecws vs /elcm path routing set up earlier), so rail items don't
 * navigate via JSF outcomes -- they resolve to absolute ECWS URLs
 * (ecwsBaseUrl + route) and open in a new browser tab (see
 * target="_blank" in taskrail.xhtml). ecwsBaseUrl comes from the
 * `ecws.base-url` property (application-local.yml locally; each
 * adev/bdev/dit/sit/uat/prod profile has a CHANGE_ME placeholder for its
 * real ECWS host -- see the TODO added to each).
 *
 * Only routes that have actually been confirmed against a real ECWS page
 * are wired up (route != null); everything else renders disabled in the UI
 * rather than as a dead/guessed link -- see TaskRailItem.isAvailable() and
 * taskrail.xhtml. As more ECWS routes are confirmed, add them to init()
 * below.
 *
 * REVISED 2026-10-18: the rail now shows the Review and Approval tasks the
 * signed-in user may see, in two tabs: Open (the default) and Closed. The data
 * comes from the ELCM service (TaskRailUiService, /api/v1/pipeline/task-rail),
 * which applies the access rules, so this bean only displays what it is given.
 * Nothing is read until it is needed: the badge number is read when a page is
 * drawn, the Open list when the user first hovers or taps the rail, and the
 * Closed list only when the user opens the Closed tab. Every list is kept for
 * REFRESH_MILLIS (30 seconds) before a hover reads it again, so there is no
 * polling and no database read per page. The old placeholder badge numbers
 * (4, 7, 2, 3) are gone; the other items keep their "Coming soon" place in the
 * footer.
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

  /** e.g. "https://localhost/ecws" locally -- see ecws.base-url in
   * application-*.yml. No trailing slash. */
  @Value("${ecws.base-url}")
  private String ecwsBaseUrl;

  // Same bean UserHelper.getUserDTOContainer() already uses to get the
  // logged-in user's ID without an extra IAMS REST round-trip -- reuse it
  // here rather than calling userHelper.getActiveUserProfile() (that method
  // makes a REST call, which is unnecessary just to build a URL param).
  @Autowired
  private AuthenticationUtil authenticationUtil;

  /** The current user's login ID, passed to ECWS as a query param so it
   * knows who's asking without a separate login/lookup on that side. */
  private String loginId;

  private List<TaskRailItem> railItems;

  /** Whether the rail should render pinned open instead of hover-collapsed
   * (a user preference toggle raised via the pin icon in taskrail.xhtml). */
  private boolean pinned = false;

  // ---- ADDED 2026-10-18: Review and Approval tasks ----

  /** How long a list or the badge is kept before the next hover reads it again. */
  static final long REFRESH_MILLIS = 30_000L;

  static final String TAB_OPEN = "OPEN";
  static final String TAB_CLOSED = "CLOSED";

  @Autowired
  private TaskRailUiService taskRailUiService;

  /** ADDED 2026-10-22: encrypts the query of links to ECWS (same secret in both apps). */
  @Autowired
  private UrlCrypt urlCrypt;

  /** The tab being shown: Open (the default) or Closed. */
  private String activeTab = TAB_OPEN;

  /** True for "Mine" (the default), false for "All" (everything the user may see). */
  private boolean mineOnly = true;

  private List<TaskRailRow> openItems = new ArrayList<>();
  private boolean openLoaded;
  private boolean openFailed;
  private long openLoadedAt;

  private List<TaskRailRow> closedItems = new ArrayList<>();
  private boolean closedLoaded;
  private boolean closedFailed;
  private long closedLoadedAt;
  /** Cursor for "Load more"; null when the Closed list has no more. */
  private Long closedNext;

  /** Open tasks assigned to the user, shown on the handle. */
  private int badge;
  private long badgeLoadedAt;

  /** ADDED 2026-10-18: the user's saved choices from the Profile page. */
  @Autowired
  private UserPreferences userPreferences;

  @PostConstruct
  private void init() {
    loginId = authenticationUtil.getUsername();

    // ADDED 2026-10-18: start with what the user chose on the Profile page. Nothing saved, or
    // IAMS not reachable, leaves the standard start (Mine, hover to open).
    mineOnly = !"ALL".equals(userPreferences.get("taskPanel.scope", "MINE"));
    pinned = "true".equals(userPreferences.get("taskPanel.pinned", "false"));

    railItems = new ArrayList<>();

    // REVISED 2026-10-18: My Tasks now opens the ECWS task list,
    // https://<ecws>/collaborationTaskList?Source=Menu&Action=List (it used
    // to open /dashboard). The route carries its own query string;
    // getEcwsUrl() keeps it and appends loginId.
    railItems.add(new TaskRailItem(
        "myTasks", "My Tasks", "pi pi-check-square",
        "Tasks assigned to you across all contract packages",
        "/collaborationTaskList?Source=Menu&Action=List", 0));

    // NOT YET CONFIRMED -- route left null so the item renders disabled
    // (see TaskRailItem.isAvailable()) instead of linking to a guessed,
    // possibly-nonexistent ECWS path. Fill in route once confirmed, e.g.:
    //   railItems.add(new TaskRailItem("taskInbox", "Task Inbox",
    //       "pi pi-inbox", "...", "/tasks/inbox", 7));
    railItems.add(new TaskRailItem(
        "taskInbox", "Task Inbox", "pi pi-inbox",
        "All incoming collaboration tasks awaiting action",
        null, 0));
    railItems.add(new TaskRailItem(
        "createTask", "Create Task", "pi pi-plus-circle",
        "Start a new ad-hoc collaboration task", null, 0));
    railItems.add(new TaskRailItem(
        "approvals", "Approvals Queue", "pi pi-thumbs-up",
        "Items routed to you for review/approval", null, 0));
    railItems.add(new TaskRailItem(
        "reassign", "Reassign Task", "pi pi-user-plus",
        "Reassign a task to another user or role", null, 0));
    railItems.add(new TaskRailItem(
        "activityLog", "Activity Log", "pi pi-sitemap",
        "Recent task activity and status changes", null, 0));
    railItems.add(new TaskRailItem(
        "notifications", "Notifications", "pi pi-bell",
        "Task-related notifications", null, 0));
  }

  /**
   * REVISED 2026-10-18: the number on the handle is now real: open Review and Approval tasks
   * assigned to the user. It is read at most every 30 seconds; when the read fails the last number
   * stays. (It used to add up placeholder numbers.)
   */
  public int getTotalBadgeCount() {
    if (now() - badgeLoadedAt >= REFRESH_MILLIS) {
      refreshBadge();
    }
    return badge;
  }

  // ---- ADDED 2026-10-18: tabs, lists and actions ----

  /**
   * Called when the user hovers or taps the rail (taskrail.xhtml remoteCommand, throttled in
   * task-rail.js). Reads the shown list again only when it is older than 30 seconds.
   */
  public void loadIfStale() {
    if (TAB_CLOSED.equals(activeTab)) {
      if (!closedLoaded || now() - closedLoadedAt >= REFRESH_MILLIS) {
        loadClosedFirstPage();
      }
    } else if (!openLoaded || now() - openLoadedAt >= REFRESH_MILLIS) {
      loadOpen();
    }
    if (now() - badgeLoadedAt >= REFRESH_MILLIS) {
      refreshBadge();
    }
  }

  public void showOpen() {
    activeTab = TAB_OPEN;
    if (!openLoaded || now() - openLoadedAt >= REFRESH_MILLIS) {
      loadOpen();
    }
  }

  /** The Closed list is read here, the first time the tab is opened, and not before. */
  public void showClosed() {
    activeTab = TAB_CLOSED;
    if (!closedLoaded || now() - closedLoadedAt >= REFRESH_MILLIS) {
      loadClosedFirstPage();
    }
  }

  public void showMine() {
    setScope(true);
  }

  public void showAll() {
    setScope(false);
  }

  /** Reads the shown tab again now (the refresh icon). */
  public void refresh() {
    if (TAB_CLOSED.equals(activeTab)) {
      loadClosedFirstPage();
    } else {
      loadOpen();
    }
    refreshBadge();
  }

  /** Adds the next page of the Closed list below the current lines. */
  public void loadMoreClosed() {
    if (closedNext == null) {
      return;
    }
    final TaskRailPageRow page = taskRailUiService.closed(mineOnly, closedNext);
    if (page == null) {
      closedFailed = true;
      return;
    }
    closedFailed = false;
    closedItems.addAll(page.getItems());
    closedNext = page.getNextBefore();
  }

  public boolean isOpenTab() {
    return TAB_OPEN.equals(activeTab);
  }

  public boolean isClosedTab() {
    return TAB_CLOSED.equals(activeTab);
  }

  /** The lines of the tab being shown. */
  public List<TaskRailRow> getItems() {
    return isClosedTab() ? closedItems : openItems;
  }

  /** True when the shown tab was read and came back empty. */
  public boolean isEmptyShown() {
    return isClosedTab()
        ? closedLoaded && !closedFailed && closedItems.isEmpty()
        : openLoaded && !openFailed && openItems.isEmpty();
  }

  /** True when the shown tab has not been read yet (the first hover is still loading it). */
  public boolean isLoadingShown() {
    return isClosedTab()
        ? !closedLoaded && !closedFailed
        : !openLoaded && !openFailed;
  }

  /** True when reading the shown tab failed. */
  public boolean isFailedShown() {
    return isClosedTab() ? closedFailed : openFailed;
  }

  public boolean isHasMoreClosed() {
    return closedNext != null;
  }

  /** The Open tab title with its count once the list has been read, such as "Open (3)". */
  public String getOpenTitle() {
    return openLoaded && !openFailed ? "Open (" + openItems.size() + ")" : "Open";
  }

  /**
   * ADDED 2026-10-18: link to the task's detail page in ECWS,
   * {ecws.base-url}/collaborationTask?taskId=N&Action=Update, or null while the task has not been
   * sent to ECWS yet (taskrail.xhtml then shows a greyed icon).
   */
  public String getEcwsTaskUrl(final TaskRailRow row) {
    if (row == null || row.getEcwsTaskId() == null) {
      return null;
    }
    // REVISED 2026-10-22: the query is encrypted for ECWS when dodaso.url.peer-encrypt is on.
    return ecwsBaseUrl + "/collaborationTask?"
        + urlCrypt.peer("taskId=" + UrlCrypt.encode(row.getEcwsTaskId()) + "&Action=Update");
  }

  private void setScope(final boolean mine) {
    if (mineOnly == mine) {
      return;
    }
    mineOnly = mine;
    // Both lists depend on the scope. Closed stays unread until its tab is opened again.
    closedLoaded = false;
    closedItems = new ArrayList<>();
    closedNext = null;
    closedFailed = false;
    openLoaded = false;
    if (isClosedTab()) {
      loadClosedFirstPage();
    } else {
      loadOpen();
    }
  }

  private void loadOpen() {
    final TaskRailPageRow page = taskRailUiService.open(mineOnly);
    openLoadedAt = now();
    if (page == null) {
      openFailed = true;
      return;
    }
    openFailed = false;
    openLoaded = true;
    openItems = new ArrayList<>(page.getItems());
  }

  private void loadClosedFirstPage() {
    final TaskRailPageRow page = taskRailUiService.closed(mineOnly, null);
    closedLoadedAt = now();
    if (page == null) {
      closedFailed = true;
      return;
    }
    closedFailed = false;
    closedLoaded = true;
    closedItems = new ArrayList<>(page.getItems());
    closedNext = page.getNextBefore();
  }

  private void refreshBadge() {
    final int count = taskRailUiService.assignedToMe();
    badgeLoadedAt = now();
    if (count >= 0) {
      badge = count;
    }
  }

  private static long now() {
    return System.currentTimeMillis();
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
   * route yet (taskrail.xhtml renders those disabled rather than linking).
   * Appends loginId as a query param so ECWS knows which user is arriving
   * without a separate lookup. UriComponentsBuilder handles encoding and
   * makes it a one-line addition to pass further params later (e.g.
   * sourceApp=elcm, a specific record ID for a deep link, etc.) --
   * just chain another .queryParam(...) call below. */
  public String getEcwsUrl(final TaskRailItem item) {
    if (item == null || item.getRoute() == null) {
      return null;
    }
    // REVISED 2026-10-22: the route may carry its own query; loginId is added, then the whole
    // query is encrypted for ECWS when dodaso.url.peer-encrypt is on.
    final String route = item.getRoute();
    final int mark = route.indexOf('?');
    final String path = mark < 0 ? route : route.substring(0, mark);
    String query = mark < 0 ? "" : route.substring(mark + 1);
    if (loginId != null) {
      query += (query.isEmpty() ? "" : "&") + "loginId=" + UrlCrypt.encode(loginId);
    }
    return ecwsBaseUrl + path + (query.isEmpty() ? "" : "?" + urlCrypt.peer(query));
  }
}
