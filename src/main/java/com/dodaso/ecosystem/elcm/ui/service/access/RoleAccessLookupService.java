package com.dodaso.ecosystem.elcm.ui.service.access;

import java.util.Collection;
import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.stereotype.Service;

import com.dodaso.ecosystem.auth.constant.UserControllerAPIEnum;
import com.dodaso.ecosystem.auth.container.UserDirectoryDTOContainer;
import com.dodaso.ecosystem.baseline.common.constant.ServiceDiscoveryEnum;
import com.dodaso.ecosystem.baseline.common.proxy.RESTServiceClient;
import com.dodaso.ecosystem.elcm.ui.constant.RoleControllerAPIEnum;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads role data from IAMS for the Demo Role switcher: which roles the
 * logged-in user holds (to decide whether they may see the switcher), and
 * which pages and fields a given role may see or edit (what the switcher
 * applies to the screen).
 *
 * Both calls fail open to an empty list. A caller that gets an empty role list
 * treats the user as a non-admin, and a caller that gets an empty rule list
 * applies no restrictions, so an IAMS outage degrades to "switcher hidden" or
 * "nothing restricted" and never breaks a page.
 *
 * Query values are passed raw, not URL-encoded: the REST client's URI handling
 * encodes them, and encoding twice would turn "Super Admin" into
 * "Super%2520Admin". This matches how UserHelper builds its loginId query.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RoleAccessLookupService {

  private final RESTServiceClient restServiceClient;

  /** Claim the SSO server puts in the ID and access tokens (IAMS role names as stored). */
  static final String ROLES_CLAIM = "roles";

  /**
   * Role names of the signed-in user. Reads the "roles" claim from the login
   * session first, which needs no network call; if the claim is missing or empty
   * (an older SSO build, or IAMS was unreachable when the user signed in) it
   * falls back to asking IAMS. The claim is a snapshot from sign-in, so a role
   * change shows after the next login.
   */
  public List<String> findCurrentUserRoleNames(final String loginId) {
    final List<String> fromToken = rolesFromToken(SecurityContextHolder.getContext().getAuthentication());
    return fromToken.isEmpty() ? findRoleNames(loginId) : fromToken;
  }

  /** Roles claim of the login principal as strings; empty if absent or not a token login. */
  static List<String> rolesFromToken(final Authentication authentication) {
    if (authentication == null
        || !(authentication.getPrincipal() instanceof OAuth2AuthenticatedPrincipal principal)) {
      return List.of();
    }
    final Object claim = principal.getAttribute(ROLES_CLAIM);
    if (!(claim instanceof Collection<?> values)) {
      return List.of();
    }
    return values.stream().filter(v -> v != null && !v.toString().isBlank())
        .map(Object::toString).toList();
  }

  /** IAMS role names held by the user, e.g. ["Super Admin"]; empty on failure. */
  public List<String> findRoleNames(final String loginId) {
    if (loginId == null || loginId.isBlank()) {
      return List.of();
    }
    try {
      final UserDirectoryDTOContainer container = restServiceClient.get(
          ServiceDiscoveryEnum.iams_service.getServiceDiscoveryName(),
          UserControllerAPIEnum.userControllerAPIEnum_getUserDirectoryByLoginId.getEndPoint()
              + "?loginId=" + loginId,
          new ParameterizedTypeReference<UserDirectoryDTOContainer>() {
          });
      if (container == null || container.getUserDirectoryDTO() == null
          || container.getUserDirectoryDTO().getRoleNames() == null) {
        return List.of();
      }
      return container.getUserDirectoryDTO().getRoleNames();
    } catch (Exception e) {
      log.error("Could not load IAMS roles for {}", loginId, e);
      return List.of();
    }
  }

  /** Page and field rules for a role name; empty on failure or unknown role. */
  public List<RoleAccessRow> findAccessRules(final String roleName) {
    if (roleName == null || roleName.isBlank()) {
      return List.of();
    }
    try {
      final List<RoleAccessRow> rows = restServiceClient.get(
          ServiceDiscoveryEnum.iams_service.getServiceDiscoveryName(),
          RoleControllerAPIEnum.getPageAccessByRoleName.getEndPoint() + "?roleName=" + roleName,
          new ParameterizedTypeReference<List<RoleAccessRow>>() {
          });
      return rows == null ? List.of() : rows;
    } catch (Exception e) {
      log.error("Could not load page access rules for role {}", roleName, e);
      return List.of();
    }
  }
}
