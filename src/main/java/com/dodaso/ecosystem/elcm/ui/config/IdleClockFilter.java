package com.dodaso.ecosystem.elcm.ui.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Shared idle clock for ELCM and ECWS.
 *
 * <p>One cookie, {@link #COOKIE_NAME} (Path=/, so both apps receive it), holds the time of the
 * user's last real action in EITHER app, signed with a secret both apps share. The idle limit
 * is judged from it, not from this app's own server session: working in ECWS therefore keeps
 * ELCM alive, and Continue in one app extends the other. The servlet session timeout is only a
 * long backstop (server.servlet.session.timeout).
 *
 * <p>Per request:
 * <ul>
 *   <li>Static resources, the logout endpoints and actuator are ignored.</li>
 *   <li>For an authenticated session, a missing, forged or older-than-limit clock ends the
 *       session. A script call gets 401, a JSF AJAX call a redirect instruction, any other
 *       request a redirect to {@code /logout}, which also ends the SSO session (otherwise the SSO
 *       session would sign the user straight back in).</li>
 *   <li>Otherwise the idle time is exposed to the controllers (request attribute
 *       {@link #IDLE_MILLIS_ATTR}) and, unless the request is the passive status poll, the clock
 *       is refreshed (at most every {@value #REFRESH_MILLIS} ms, to avoid a Set-Cookie on every
 *       request).</li>
 * </ul>
 * The status poll does not refresh the clock: it is not user activity.
 */
@Slf4j
public class IdleClockFilter extends OncePerRequestFilter {

  /** Cookie shared by both apps. Value: {@code <epoch millis>.<HMAC-SHA256, base64url>}. */
  public static final String COOKIE_NAME = "DODASO_LAST_ACTIVITY";

  /** Request attribute: idle milliseconds seen by this request (Long), when the user is signed in. */
  public static final String IDLE_MILLIS_ATTR = IdleClockFilter.class.getName() + ".IDLE_MILLIS";

  /** Request attribute: the idle limit in milliseconds (Long). */
  public static final String LIMIT_MILLIS_ATTR = IdleClockFilter.class.getName() + ".LIMIT_MILLIS";

  static final long REFRESH_MILLIS = 15_000L;
  private static final long COOKIE_MAX_AGE_SECONDS = 24 * 60 * 60;

  private static final String[] IGNORED_PREFIXES = {
      "/javax.faces.resource/", "/jakarta.faces.resource/", "/resources/", "/css/", "/js/",
      "/images/", "/actuator/", "/logout", "/api/logout", "/error", "/favicon"};
  private static final String STATUS_PATH = "/api/session-status";
  private static final String EXTEND_PATH = "/api/extend-session";

  private final byte[] secret;
  private final long limitMillis;

  public IdleClockFilter(String secret, long limitMillis) {
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
    this.limitMillis = limitMillis;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    final String path = request.getRequestURI().substring(request.getContextPath().length());
    if (isIgnored(path)) {
      chain.doFilter(request, response);
      return;
    }

    final long now = System.currentTimeMillis();
    final Long last = readClock(request);
    final HttpSession session = request.getSession(false);

    if (session != null && isAuthenticated(session)) {
      final long idle = last == null ? Long.MAX_VALUE : Math.max(0L, now - last);
      if (idle > limitMillis) {
        log.info("Idle for {} s (limit {} s): ending session {}",
            last == null ? -1 : idle / 1000, limitMillis / 1000, abbreviate(session.getId()));
        endIdleSession(request, response, session, path);
        return;
      }
      request.setAttribute(IDLE_MILLIS_ATTR, idle);
      request.setAttribute(LIMIT_MILLIS_ATTR, limitMillis);
    }

    // Refresh before the chain runs: a redirect or error would already have committed the headers.
    if (!STATUS_PATH.equals(path) && (last == null || now - last >= REFRESH_MILLIS)) {
      writeClock(response, now);
      if (request.getAttribute(IDLE_MILLIS_ATTR) != null) {
        request.setAttribute(IDLE_MILLIS_ATTR, 0L);
      }
    }
    chain.doFilter(request, response);
  }

  private void endIdleSession(HttpServletRequest request, HttpServletResponse response,
      HttpSession session, String path) throws IOException {
    SecurityContextHolder.clearContext();
    try {
      session.invalidate();
    } catch (IllegalStateException alreadyInvalid) {
      // Ended by a concurrent request.
    }
    // Lets the login page say why the user was signed out.
    response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("loginReason", "timeout")
        .path("/").maxAge(300).secure(true).sameSite("None").build().toString());
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate");

    final String logoutUrl = request.getContextPath() + "/logout";
    if (STATUS_PATH.equals(path) || EXTEND_PATH.equals(path)) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    } else if ("partial/ajax".equals(request.getHeader("Faces-Request"))) {
      response.setContentType("text/xml;charset=UTF-8");
      response.getWriter().write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
          + "<partial-response><redirect url=\"" + logoutUrl + "\"/></partial-response>");
    } else {
      response.sendRedirect(logoutUrl);
    }
  }

  private static boolean isIgnored(String path) {
    for (String prefix : IGNORED_PREFIXES) {
      if (path.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isAuthenticated(HttpSession session) {
    final Object attribute = session.getAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
    if (!(attribute instanceof SecurityContext)) {
      return false;
    }
    final Authentication authentication = ((SecurityContext) attribute).getAuthentication();
    return authentication != null && authentication.isAuthenticated()
        && !(authentication instanceof AnonymousAuthenticationToken);
  }

  /** The signed time of the last action, or null when the cookie is absent or not authentic. */
  private Long readClock(HttpServletRequest request) {
    final Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (COOKIE_NAME.equals(cookie.getName())) {
        return verify(cookie.getValue());
      }
    }
    return null;
  }

  Long verify(String value) {
    if (value == null) {
      return null;
    }
    final int dot = value.indexOf('.');
    if (dot <= 0) {
      return null;
    }
    try {
      final long millis = Long.parseLong(value.substring(0, dot));
      final byte[] expected = sign(Long.toString(millis)).getBytes(StandardCharsets.UTF_8);
      final byte[] actual = value.substring(dot + 1).getBytes(StandardCharsets.UTF_8);
      return MessageDigest.isEqual(expected, actual) ? millis : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  String sign(String payload) {
    try {
      final Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return Base64.getUrlEncoder().withoutPadding()
          .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HmacSHA256 unavailable", e);
    }
  }

  private void writeClock(HttpServletResponse response, long now) {
    final String payload = Long.toString(now);
    final ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, payload + "." + sign(payload))
        .path("/")
        .maxAge(COOKIE_MAX_AGE_SECONDS)
        .httpOnly(true)
        .secure(true)
        .sameSite("None")
        .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  private static String abbreviate(String id) {
    return id == null || id.length() < 8 ? "?" : id.substring(0, 8) + "...";
  }
}
