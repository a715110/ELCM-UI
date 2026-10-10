package com.dodaso.ecosystem.elcm.ui.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * "Logged out at" marker shared by ELCM and ECWS.
 *
 * <p>Both apps sit behind the same host, so a cookie set on Path=/ is sent to both. On every
 * logout the app writes {@link #COOKIE_NAME} with the current time ({@link #write}); this filter,
 * which runs before the security context is loaded, ends any HTTP session that was created at or
 * before that time. A login after the logout creates a newer session, so it is not affected.
 *
 * <p>This is what ends the other app's session when the user logs out here while no tab of the
 * other app is open (nothing else would run in that app until its own idle timeout). The value
 * is not signed: the worst a forged marker can do is log the forger out.
 */
@Slf4j
public class LogoutMarkerFilter extends OncePerRequestFilter {

  /** Cookie shared by both apps. Value: epoch milliseconds of the logout. */
  public static final String COOKIE_NAME = "DODASO_LOGOUT_AT";

  /** Longer than any session can live (idle timeout is 30 minutes). */
  private static final long MAX_AGE_SECONDS = 24 * 60 * 60;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    final HttpSession session = request.getSession(false);
    if (session != null) {
      final long loggedOutAt = markerOf(request);
      if (loggedOutAt > 0 && session.getCreationTime() <= loggedOutAt) {
        log.info("Session {} predates the logout marker, ending it", abbreviate(session.getId()));
        SecurityContextHolder.clearContext();
        try {
          session.invalidate();
        } catch (IllegalStateException alreadyInvalid) {
          // Ended by a concurrent request: nothing left to do.
        }
      }
    }
    chain.doFilter(request, response);
  }

  /** Marker time in epoch milliseconds, or 0 when the cookie is absent or unreadable. */
  static long markerOf(HttpServletRequest request) {
    final Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return 0L;
    }
    for (Cookie cookie : cookies) {
      if (COOKIE_NAME.equals(cookie.getName())) {
        try {
          return Long.parseLong(cookie.getValue());
        } catch (NumberFormatException e) {
          return 0L;
        }
      }
    }
    return 0L;
  }

  /** Adds the marker (now) to the response. Same attributes as the session cookie, Path=/. */
  public static void write(HttpServletResponse response) {
    final ResponseCookie marker = ResponseCookie
        .from(COOKIE_NAME, Long.toString(System.currentTimeMillis()))
        .path("/")
        .maxAge(MAX_AGE_SECONDS)
        .httpOnly(true)
        .secure(true)
        .sameSite("None")
        .build();
    response.addHeader(HttpHeaders.SET_COOKIE, marker.toString());
  }

  private static String abbreviate(String id) {
    return id == null || id.length() < 8 ? "?" : id.substring(0, 8) + "...";
  }
}
