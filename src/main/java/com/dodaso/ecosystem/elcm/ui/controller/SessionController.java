package com.dodaso.ecosystem.elcm.ui.controller;

import com.dodaso.ecosystem.elcm.ui.config.IdleClockFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two calls the browser's session timeout script (resources/ext/js/session-timeout-manager.js)
 * makes. Both need an authenticated user; SecurityConfig answers 401 (not a login redirect) when
 * there is none, which the script treats as an expired session.
 *
 * GET /api/session-status: the session is alive. Polled about once a minute. The poll is NOT user
 * activity: the idle time comes from the clock ELCM and ECWS share (IdleClockFilter), which
 * answers 401 itself once the limit has passed. The body carries idleSeconds and limitSeconds so
 * the script can line its warning and countdown up with that clock (for example after activity
 * in the other app, or after the computer slept).
 *
 * POST /api/extend-session: the user is active or pressed Continue. The filter refreshes the
 * shared clock for this request, which extends both apps. 409 when a logout is under way (the
 * script sets the short-lived noExtend cookie), so an in-flight extension cannot revive a session
 * that is being closed.
 */
@RestController
public class SessionController {

  @GetMapping("/api/session-status")
  public ResponseEntity<Map<String, Object>> status(HttpServletRequest request) {
    final HttpSession session = request.getSession(false);
    if (session == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    final Map<String, Object> body = new LinkedHashMap<>();
    body.put("active", true);
    body.put("maxInactiveInterval", session.getMaxInactiveInterval());
    addIdleClock(request, body);
    return ResponseEntity.ok(body);
  }

  @PostMapping("/api/extend-session")
  public ResponseEntity<Map<String, Object>> extend(HttpServletRequest request) {
    final HttpSession session = request.getSession(false);
    if (session == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    if (logoutInProgress(request)) {
      return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
    final Map<String, Object> body = new LinkedHashMap<>();
    body.put("success", true);
    body.put("maxInactiveInterval", session.getMaxInactiveInterval());
    body.put("extendedAt", Instant.now().toString());
    addIdleClock(request, body);
    return ResponseEntity.ok(body);
  }

  /** Idle time and limit of the shared clock, as seen by the filter for this request. */
  private static void addIdleClock(HttpServletRequest request, Map<String, Object> body) {
    final Object idle = request.getAttribute(IdleClockFilter.IDLE_MILLIS_ATTR);
    final Object limit = request.getAttribute(IdleClockFilter.LIMIT_MILLIS_ATTR);
    if (idle instanceof Long && limit instanceof Long) {
      body.put("idleSeconds", (Long) idle / 1000);
      body.put("limitSeconds", (Long) limit / 1000);
    }
  }

  private static boolean logoutInProgress(HttpServletRequest request) {
    final Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return false;
    }
    for (final Cookie cookie : cookies) {
      if ("noExtend".equals(cookie.getName()) && "1".equals(cookie.getValue())) {
        return true;
      }
    }
    return false;
  }
}
