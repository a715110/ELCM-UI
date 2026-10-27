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
 * Decides whether the signed-in user may open Configure Messaging (ADDED 2026-10-26): System Admin
 * and Super Admin only (property elcm.menu.admin-roles, the same list the menu uses).
 *
 * <p>Unlike {@link MenuAccessBean} this fails CLOSED: when no role can be read the answer is no,
 * because the page shows connection secrets. A failed lookup is not cached and is tried again
 * after a short pause. UI chrome only: common-service does not check the role yet.
 */
@Named
@SessionScoped
@Slf4j
public class MessagingAccessBean implements Serializable {

  private static final long RETRY_AFTER_MILLIS = 60_000L;

  @Value("${elcm.menu.admin-roles:System Admin,Super Admin}")
  private String adminRoles;

  @Autowired private AuthenticationUtil authenticationUtil;

  @Autowired private RoleAccessLookupService roleAccessLookupService;

  private boolean admin;

  private long lastFailMillis;

  /** True when the user holds one of the admin roles. */
  public boolean isAdmin() {
    if (admin) {
      return true;
    }
    if (System.currentTimeMillis() - lastFailMillis < RETRY_AFTER_MILLIS) {
      return false;
    }
    final List<String> roles =
        roleAccessLookupService.findCurrentUserRoleNames(authenticationUtil.getUsername());
    final Set<String> wanted = Set.copyOf(split(adminRoles));
    admin =
        roles.stream().map(r -> r.trim().toLowerCase(Locale.ROOT)).anyMatch(wanted::contains);
    if (!admin) {
      lastFailMillis = System.currentTimeMillis();
    }
    return admin;
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
