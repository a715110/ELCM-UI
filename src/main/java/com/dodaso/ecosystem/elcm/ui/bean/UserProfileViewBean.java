package com.dodaso.ecosystem.elcm.ui.bean;

import com.dodaso.ecosystem.baseline.common.profile.ProfileIamsClient;
import com.dodaso.ecosystem.baseline.common.profile.ProfileIdentity;
import com.dodaso.ecosystem.baseline.common.profile.ProfileInitials;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * ADDED 2026-10-24 (ported from ECWS).
 * The read-only Profile dialog opened by View Profile in the small popup that
 * appears when a user name is clicked (dm-multichannel.js, template.xhtml). It shows the same kind
 * of details as the avatar menu: round initials on the person's own color, name, login, roles, and
 * also team and workspaces. The details come from the IAMS user directory, the same source as the
 * avatar menu and the Profile page. Nothing here changes anything.
 *
 * <p>When the directory has no entry (or cannot be read), the dialog still shows the name that was
 * clicked, with a notice.
 */
@Named("userProfileViewBean")
@ViewScoped
@Getter
@Slf4j
public class UserProfileViewBean implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  @Autowired
  private transient ProfileIamsClient client;

  private String loginId = "";
  private String clickedName = "";
  private ProfileIdentity identity;
  private boolean notFound;

  /** Reads the person named by the loginId and displayName request parameters. */
  public void load() {
    final Map<String, String> params =
        FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();
    loginId = trim(params.get("loginId"));
    clickedName = trim(params.get("displayName"));
    identity = null;
    notFound = false;
    if (loginId.isEmpty()) {
      notFound = true;
      return;
    }
    identity = client.identity(loginId);
    notFound = identity == null;
  }

  public String getDisplayName() {
    if (identity != null && identity.getDisplayName() != null
        && !identity.getDisplayName().isBlank()) {
      return identity.getDisplayName();
    }
    return !clickedName.isEmpty() ? clickedName : loginId;
  }

  public String getInitials() {
    return ProfileInitials.of(getDisplayName(), loginId);
  }

  /** Style class that colors the circle, for example dp-avatar-c3 (same as the avatar menu). */
  public String getColorClass() {
    return "dp-avatar-c" + ProfileInitials.colorOf(loginId);
  }

  public String getRolesText() {
    return identity == null || identity.getRoleNames() == null || identity.getRoleNames().isEmpty()
        ? "No role assigned" : String.join(", ", identity.getRoleNames());
  }

  public String getTeamText() {
    return identity == null || identity.getTeamName() == null || identity.getTeamName().isBlank()
        ? "None" : identity.getTeamName();
  }

  public String getWorkspacesText() {
    return identity == null || identity.getWorkspaceCodes() == null
        || identity.getWorkspaceCodes().isEmpty() ? "None"
        : String.join(", ", identity.getWorkspaceCodes());
  }

  private static String trim(final String value) {
    return value == null ? "" : value.trim();
  }
}
