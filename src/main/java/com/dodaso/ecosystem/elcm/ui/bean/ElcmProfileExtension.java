package com.dodaso.ecosystem.elcm.ui.bean;

import com.dodaso.ecosystem.baseline.common.constant.ServiceDiscoveryEnum;
import com.dodaso.ecosystem.baseline.common.profile.ProfileExtension;
import com.dodaso.ecosystem.baseline.common.profile.ProfileLink;
import com.dodaso.ecosystem.baseline.common.profile.ProfileOption;
import com.dodaso.ecosystem.baseline.common.profile.ProfileWorkGroup;
import com.dodaso.ecosystem.baseline.common.proxy.RESTServiceClient;
import com.dodaso.ecosystem.elcm.ui.primefaces.RainPreferences;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.TaskRailPageRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.TaskRailRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.TaskRailUiService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * ADDED 2026-10-18. What ELCM adds to the shared Profile page (baseline-common): the look of the
 * screens, the two task panel preferences, the list of what the user's roles allow (asked of the
 * ELCM service, which owns the role to permission mapping) and "My work", built from the same Task
 * Rail data the right-hand panel shows. The session scoped beans are reached through providers
 * because this bean lives for the whole application.
 */
@Component
@Slf4j
public class ElcmProfileExtension implements ProfileExtension {

  static final String KEY_SCOPE = "taskPanel.scope";
  static final String KEY_PINNED = "taskPanel.pinned";
  static final String SCOPE_MINE = "MINE";
  static final String SCOPE_ALL = "ALL";
  static final int WORK_MAX = 10;
  private static final String SERVICE = ServiceDiscoveryEnum.elcm_service.getServiceDiscoveryName();

  @Autowired
  private ObjectProvider<RainPreferences> rainPreferences;

  @Autowired
  private ObjectProvider<TaskRailBean> taskRailBean;

  @Autowired
  private TaskRailUiService taskRailUiService;

  @Autowired
  private RESTServiceClient restServiceClient;

  @Override
  public String getAppCode() {
    return "ELCM";
  }

  @Override
  public String getAppName() {
    return "ELCM";
  }

  @Override
  public void applyTheme(final String mode) {
    if (mode != null) {
      rainPreferences.getObject().setDarkMode(mode);
    }
  }

  @Override
  public String currentTheme() {
    return rainPreferences.getObject().getDarkMode();
  }

  @Override
  public List<ProfileOption> options() {
    final List<ProfileOption> list = new ArrayList<>();
    list.add(new ProfileOption(KEY_SCOPE, "Task panel shows",
        "What the right-hand task panel lists when you open it.", SCOPE_MINE)
        .choice(SCOPE_MINE, "Only my tasks").choice(SCOPE_ALL, "Everything I may see"));
    list.add(new ProfileOption(KEY_PINNED, "Task panel stays open",
        "Keep the right-hand task panel open instead of opening it when you point at it.",
        "false").choice("false", "No").choice("true", "Yes"));
    return list;
  }

  @Override
  public void onSaved(final Map<String, String> values) {
    final TaskRailBean rail = taskRailBean.getObject();
    final String pinned = values.get(KEY_PINNED);
    if (pinned != null) {
      rail.setPinned("true".equals(pinned));
    }
    final String scope = values.get(KEY_SCOPE);
    if (SCOPE_ALL.equals(scope)) {
      rail.showAll();
    } else if (SCOPE_MINE.equals(scope)) {
      rail.showMine();
    }
  }

  @Override
  public List<String> abilities() {
    try {
      final List<String> lines = restServiceClient.get(SERVICE, "/api/v1/profile/abilities",
          new ParameterizedTypeReference<List<String>>() {
          });
      return lines == null ? List.of() : lines;
    } catch (final Exception e) {
      log.error("Could not read what the signed-in user can do in ELCM", e);
      return List.of();
    }
  }

  @Override
  public List<ProfileWorkGroup> work() {
    final TaskRailPageRow open = taskRailUiService.open(true);
    final TaskRailPageRow closed = taskRailUiService.closed(true, null);
    final List<TaskRailRow> openRows = open == null ? List.of() : open.getItems();
    final List<TaskRailRow> closedRows = closed == null ? List.of() : closed.getItems();

    final List<ProfileLink> assigned = new ArrayList<>();
    final List<ProfileLink> submitted = new ArrayList<>();
    final List<ProfileLink> reviewed = new ArrayList<>();
    final List<TaskRailRow> all = new ArrayList<>(openRows);
    all.addAll(closedRows);
    for (final TaskRailRow row : all) {
      final String relation = row.getRelation() == null ? "" : row.getRelation();
      switch (relation) {
        case "ASSIGNED" -> add(assigned, row, row.getEventLabel() + ", " + dueOrState(row));
        case "SUBMITTER" -> add(submitted, row, row.getEventLabel() + ", " + dueOrState(row));
        case "REVIEWER" -> add(reviewed, row, row.getEventLabel() + ", " + dueOrState(row));
        default -> {
          // not the user's own line
        }
      }
    }
    final List<ProfileWorkGroup> groups = new ArrayList<>();
    groups.add(new ProfileWorkGroup("Open tasks assigned to me", "Nothing is waiting for you.",
        assigned));
    groups.add(new ProfileWorkGroup("Packages I submitted (last 30 days)",
        "No packages submitted by you in this period.", submitted));
    groups.add(new ProfileWorkGroup("Packages I reviewed (last 30 days)",
        "No packages reviewed by you in this period.", reviewed));
    return groups;
  }

  private static void add(final List<ProfileLink> into, final TaskRailRow row,
      final String detail) {
    if (into.size() >= WORK_MAX || row.getPackageId() == null) {
      return;
    }
    for (final ProfileLink existing : into) {
      if (existing.getLabel().equals(row.getPackageCode())) {
        return;
      }
    }
    into.add(new ProfileLink(row.getPackageCode(), detail, packageUrl(row.getPackageId())));
  }

  /** The due badge for an open task, or how the task ended for a closed one. */
  private static String dueOrState(final TaskRailRow row) {
    if (row.getClosedAt() != null) {
      return row.getOutcome() == null ? "closed" : row.getOutcome();
    }
    final String due = row.getDueText();
    return due == null || due.isBlank() ? "open" : due;
  }

  /** Same page the task panel opens: the dashboard on this package, under the context path. */
  static String packageUrl(final Long packageId) {
    return UriComponentsBuilder.fromPath(contextPath() + "/dashboard")
        .queryParam("review", packageId)
        .queryParam("Source", "Profile")
        .build()
        .toUriString();
  }

  private static String contextPath() {
    final RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes instanceof ServletRequestAttributes servlet) {
      return servlet.getRequest().getContextPath();
    }
    return "";
  }
}
