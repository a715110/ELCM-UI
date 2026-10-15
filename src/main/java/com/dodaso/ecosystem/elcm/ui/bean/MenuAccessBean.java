package com.dodaso.ecosystem.elcm.ui.bean;

import com.dodaso.ecosystem.baseline.common.security.AuthenticationUtil;
import com.dodaso.ecosystem.elcm.ui.service.access.RoleAccessLookupService;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * Decides which menu items the signed-in user sees (ADDED 2026-10-14). The menu used to show every
 * item to everyone; now My Submissions is shown to the roles that submit, and the Work Queue to the
 * roles that review or approve. Dashboard and Contract Records stay open to every role.
 *
 * <p>UI CHROME ONLY, like the Demo Role switcher: hiding an item is not a security control.
 * elcm-service still enforces every permission, and a hidden page still opens if someone types its
 * address. The check fails open: when no role can be read (IAMS down, user without roles) every
 * item is shown, so a failed lookup never locks someone out of the menu. A failed lookup is not
 * cached; it is tried again after a short pause.
 *
 * <p>While an admin previews another role with the Demo Role switcher, the menu follows the
 * previewed role.
 *
 * <p>Role lists are properties (comma separated IAMS role names, matched without regard to case)
 * with these defaults: elcm.menu.my-submissions-roles = Document Submitter, Business Submitter,
 * Preparer; elcm.menu.work-queue-roles = Reviewer, Approver. Admin roles (elcm.menu.admin-roles,
 * default System Admin, Super Admin) see everything. The Administration group (Extraction
 * Templates, ADDED 2026-10-14) is shown to the admin roles only.
 */
@Named
@SessionScoped
@Slf4j
public class MenuAccessBean implements Serializable {

  private static final String MY_SUBMISSIONS = "mySubmissions";
  private static final String WORK_QUEUE = "workQueue";
  private static final String TEMPLATES = "templates";
  private static final long RETRY_AFTER_MILLIS = 60_000L;

  @Value("${elcm.menu.admin-roles:System Admin,Super Admin}")
  private String adminRoles;

  @Value("${elcm.menu.my-submissions-roles:Document Submitter,Business Submitter,Preparer}")
  private String mySubmissionsRoles;

  @Value("${elcm.menu.work-queue-roles:Reviewer,Approver}")
  private String workQueueRoles;

  @Autowired private AuthenticationUtil authenticationUtil;

  @Autowired private RoleAccessLookupService roleAccessLookupService;

  @Autowired private DemoRoleBean demoRoleBean;

  /** Roles of the signed-in user (lower case); null until a lookup has succeeded. */
  private List<String> ownRoles;

  private long lastEmptyCheckMillis;

  /** True when the menu item with this id should be shown. Unknown ids are always shown. */
  public boolean canSee(final String item) {
    final List<String> roles = effectiveRoles();
    if (roles.isEmpty()) {
      return true;
    }
    if (roles.stream().anyMatch(split(adminRoles)::contains)) {
      return true;
    }
    return switch (item) {
      case MY_SUBMISSIONS -> hasAny(roles, mySubmissionsRoles);
      case WORK_QUEUE -> hasAny(roles, workQueueRoles);
      case TEMPLATES -> false;
      default -> true;
    };
  }

  private List<String> effectiveRoles() {
    if (demoRoleBean.isOverrideActive()) {
      return List.of(demoRoleBean.getSelectedRole().trim().toLowerCase(Locale.ROOT));
    }
    if (ownRoles != null) {
      return ownRoles;
    }
    if (System.currentTimeMillis() - lastEmptyCheckMillis < RETRY_AFTER_MILLIS) {
      return List.of();
    }
    final List<String> found =
        roleAccessLookupService.findCurrentUserRoleNames(authenticationUtil.getUsername());
    if (found.isEmpty()) {
      lastEmptyCheckMillis = System.currentTimeMillis();
      return List.of();
    }
    ownRoles = found.stream().map(r -> r.trim().toLowerCase(Locale.ROOT)).toList();
    return ownRoles;
  }

  private static boolean hasAny(final List<String> roles, final String configured) {
    final Set<String> wanted = Set.copyOf(split(configured));
    return roles.stream().anyMatch(wanted::contains);
  }

  private static List<String> split(final String csv) {
    if (csv == null || csv.isBlank()) {
      return List.of();
    }
    return Arrays.stream(csv.split(","))
        .map(s -> s.trim().toLowerCase(Locale.ROOT))
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toList());
  }
}
