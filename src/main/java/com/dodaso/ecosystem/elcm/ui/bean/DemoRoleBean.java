package com.dodaso.ecosystem.elcm.ui.bean;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.dodaso.ecosystem.baseline.common.security.AuthenticationUtil;
import com.dodaso.ecosystem.elcm.ui.service.access.FieldAccessRow;
import com.dodaso.ecosystem.elcm.ui.service.access.RoleAccessLookupService;
import com.dodaso.ecosystem.elcm.ui.service.access.RoleAccessRow;

import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Named;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Backs the "Demo Role" switcher in the top bar. Lets a demo user preview the
 * screens as another IAMS role would see them: pages and fields are hidden,
 * made read only, or left editable according to the page_access and
 * field_access rows IAMS holds for that role.
 *
 * UI CHROME ONLY. This changes what is rendered, not what data is returned or
 * what the services allow. The underlying data is not filtered and nothing
 * here is a security control; real enforcement is the deferred Option 2.
 *
 * Who sees the switcher: users holding one of the admin roles
 * (elcm.demo-role.admin-roles, default "System Admin,Super Admin"), or
 * everyone when elcm.demo-role.visible-to-all=true (for client demos). The
 * check fails closed: if IAMS cannot be reached the switcher is hidden. A
 * failed check is not cached, so it is retried on the next render.
 *
 * Levels, for pages and fields: H hidden, R read only, E editable. With no
 * role selected, or no rule for a page, everything is editable. A field with
 * no rule of its own inherits its page's level.
 *
 * Scope: SessionScoped because the top bar renders on every view and the
 * selected role has to survive navigation. Selecting a role reloads the page
 * (see topbar.xhtml) so every section re-evaluates these methods.
 */
@Named
@SessionScoped
@Getter
@Setter
@Slf4j
public class DemoRoleBean implements Serializable {

  private static final String DEFAULT_ROLES =
      "Document Submitter,Preparer,Reviewer,Approver,Accountant,Controller,"
          + "Business Submitter,Auditor,System Admin,Super Admin";

  private static final long RETRY_AFTER_MILLIS = 60_000L;

  private static final char EDIT = 'E';
  private static final char READ = 'R';
  private static final char HIDE = 'H';

  /** Show the switcher to every user, not just admins. Meant for client demos. */
  @Value("${elcm.demo-role.visible-to-all:false}")
  private boolean visibleToAll;

  /** Comma-separated IAMS role names allowed to use the switcher. */
  @Value("${elcm.demo-role.admin-roles:System Admin,Super Admin}")
  private String adminRoles;

  /** Comma-separated role names offered in the dropdown. */
  @Value("${elcm.demo-role.roles:" + DEFAULT_ROLES + "}")
  private String roleOptionsConfig;

  @Autowired
  private AuthenticationUtil authenticationUtil;

  @Autowired
  private RoleAccessLookupService roleAccessLookupService;

  /** The role being previewed; null or blank means "my own role" (no override). */
  private String selectedRole;

  /** Cached admin check; null until it has succeeded once. No accessors: the
   * public isAvailable() below is the only property EL should see. */
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private Boolean available;

  /** When the last admin check came back with no roles; used to avoid asking IAMS on every render. */
  private long lastEmptyCheckMillis;

  /** Rules for selectedRole, keyed by page name. Empty when no override. */
  private Map<String, RoleAccessRow> rulesByPage = new HashMap<>();

  /** True when the switcher should render for the current user. */
  public boolean isAvailable() {
    if (visibleToAll) {
      return true;
    }
    if (available != null) {
      return available;
    }
    if (System.currentTimeMillis() - lastEmptyCheckMillis < RETRY_AFTER_MILLIS) {
      return false;
    }
    final List<String> userRoles =
        roleAccessLookupService.findCurrentUserRoleNames(authenticationUtil.getUsername());
    if (userRoles.isEmpty()) {
      // Could not confirm any role (IAMS down, or user has none): hide the
      // switcher, and ask again only after a short pause, because this method
      // runs several times per page render.
      lastEmptyCheckMillis = System.currentTimeMillis();
      return false;
    }
    final List<String> admins = split(adminRoles);
    available = userRoles.stream().anyMatch(admins::contains);
    return available;
  }

  /** Role names for the dropdown. */
  public List<String> getRoleOptions() {
    return split(roleOptionsConfig);
  }

  /** True while a role other than the user's own is being previewed. */
  public boolean isOverrideActive() {
    // selectedRole is checked first: it is only ever set by an admin through the
    // switcher, so for everyone else this returns without calling isAvailable().
    return selectedRole != null && !selectedRole.isBlank() && isAvailable();
  }

  /** Dropdown change listener: loads the chosen role's rules, or clears them. */
  public void onRoleChange() {
    if (!isAvailable() || selectedRole == null || selectedRole.isBlank()) {
      selectedRole = null;
      rulesByPage = new HashMap<>();
      return;
    }
    final Map<String, RoleAccessRow> loaded = new HashMap<>();
    for (RoleAccessRow row : roleAccessLookupService.findAccessRules(selectedRole)) {
      if (row.getPageName() != null) {
        loaded.put(row.getPageName(), row);
      }
    }
    if (loaded.isEmpty()) {
      log.warn("No page access rules found for role '{}'; nothing will be restricted", selectedRole);
    }
    rulesByPage = loaded;
  }

  /** False when the page is hidden for the previewed role. */
  public boolean canSee(final String page) {
    return pageLevel(page) != HIDE;
  }

  /** True when the page is read only for the previewed role. */
  public boolean readOnly(final String page) {
    return pageLevel(page) == READ;
  }

  /** False when the field, or the whole page, is hidden for the previewed role. */
  public boolean canSeeField(final String page, final String field) {
    return fieldLevel(page, field) != HIDE;
  }

  /** True when the field is read only, from its own rule or inherited from its page. */
  public boolean fieldReadOnly(final String page, final String field) {
    return fieldLevel(page, field) == READ;
  }

  private char pageLevel(final String page) {
    if (!isOverrideActive()) {
      return EDIT;
    }
    final RoleAccessRow row = rulesByPage.get(page);
    return row == null ? EDIT : toLevel(row.getAccessLevel(), EDIT);
  }

  private char fieldLevel(final String page, final String field) {
    final char pageLevel = pageLevel(page);
    if (pageLevel == HIDE) {
      return HIDE;
    }
    final RoleAccessRow row = rulesByPage.get(page);
    if (row != null && row.getFieldAccesseDtoList() != null) {
      for (FieldAccessRow f : row.getFieldAccesseDtoList()) {
        if (field != null && field.equals(f.getFieldName())) {
          return toLevel(f.getAccessLevel(), pageLevel);
        }
      }
    }
    return pageLevel;
  }

  private static char toLevel(final String code, final char fallback) {
    if (code == null || code.isBlank()) {
      return fallback;
    }
    final char c = code.trim().toUpperCase(Locale.ROOT).charAt(0);
    return (c == EDIT || c == READ || c == HIDE) ? c : fallback;
  }

  private static List<String> split(final String csv) {
    if (csv == null || csv.isBlank()) {
      return List.of();
    }
    return Arrays.stream(csv.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toList());
  }
}
