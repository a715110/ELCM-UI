package com.dodaso.ecosystem.elcm.ui.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

/**
 * Runs on every logout, explicit or timeout (both end in /logout), next to Spring Security's own
 * handler that invalidates the HTTP session and clears the security context.
 *
 * Adds what that handler does not do:
 * - Removes the user's stored OAuth2 tokens. They are kept by OAuth2AuthorizedClientService, not in
 *   the HTTP session, so invalidating the session alone leaves them in memory.
 * - Expires the session cookie with the attributes it was issued with (name, path, Secure,
 *   HttpOnly, SameSite=None, all from server.servlet.session.cookie). Spring's deleteCookies()
 *   expires a cookie on the context path, while this app issues it on "/", so it would not match.
 * - Writes the shared "logged out at" marker cookie (LogoutMarkerFilter), which ends the other app's
 *   session on its next request.
 * - Marks the response as not cacheable, so the back button cannot show a cached page.
 */
@Component
@Slf4j
public class SessionCleanupLogoutHandler implements LogoutHandler {

  private final OAuth2AuthorizedClientService authorizedClientService;
  private final String cookieName;
  private final String cookiePath;

  public SessionCleanupLogoutHandler(
      OAuth2AuthorizedClientService authorizedClientService,
      @Value("${server.servlet.session.cookie.name:ELCMSESSIONID}") String cookieName,
      @Value("${server.servlet.session.cookie.path:/}") String cookiePath) {
    this.authorizedClientService = authorizedClientService;
    this.cookieName = cookieName;
    this.cookiePath = cookiePath;
  }

  @Override
  public void logout(HttpServletRequest request, HttpServletResponse response,
      Authentication authentication) {
    if (authentication instanceof OAuth2AuthenticationToken token) {
      authorizedClientService.removeAuthorizedClient(
          token.getAuthorizedClientRegistrationId(), token.getName());
      log.info("Logout: removed stored tokens of {}", token.getName());
    }

    final ResponseCookie expired = ResponseCookie.from(cookieName, "")
        .path(cookiePath)
        .maxAge(0)
        .httpOnly(true)
        .secure(true)
        .sameSite("None")
        .build();
    response.addHeader(HttpHeaders.SET_COOKIE, expired.toString());
    // Tells the other app (same host, Path=/) to end its session too, see LogoutMarkerFilter.
    LogoutMarkerFilter.write(response);
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate");
    response.setHeader(HttpHeaders.PRAGMA, "no-cache");
  }
}
