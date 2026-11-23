package com.dodaso.ecosystem.elcm.ui.config;

import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * ADDED 2026-11-23. Decides whether a state changing browser request (POST, PUT, PATCH, DELETE)
 * was sent from one of our own pages. CSRF protection of the Spring Security chain is switched
 * off in this app and the session cookie is SameSite=None, so this check is what stops another
 * web site from submitting a form to us with the user's session cookie.
 *
 * <p>Pure logic, no Spring types, so it can be tested on its own. Rules, in order:
 *
 * <ol>
 *   <li>GET, HEAD, OPTIONS and TRACE are never checked.
 *   <li>Sec-Fetch-Site "same-origin" (set by the browser, a page script cannot forge it): allow.
 *   <li>Origin header present: allow only when it is one of the allowed origins. The literal
 *       "null" (sandboxed frame, some redirects) is rejected.
 *   <li>Else Referer header present: allow only when its origin is allowed.
 *   <li>Else Sec-Fetch-Site "cross-site" or "same-site": reject.
 *   <li>Else no browser headers at all (a server to server call such as the SSO back channel
 *       logout): allow. Those calls carry their own proof (a signed token).
 * </ol>
 */
public final class OriginPolicy {

  /** Result of a check. */
  public record Decision(boolean allowed, String reason) {}

  private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

  private final Set<String> allowedOrigins;

  /**
   * Creates the policy.
   *
   * @param urls base URLs or origins that are ours, for example "https://host/ecws"; only scheme,
   *     host and port are used. Blank and unparsable entries are ignored.
   */
  public OriginPolicy(Collection<String> urls) {
    Set<String> origins = new LinkedHashSet<>();
    for (String url : urls) {
      String origin = originOf(url);
      if (origin != null) {
        origins.add(origin);
      }
    }
    this.allowedOrigins = Set.copyOf(origins);
  }

  /** The allowed origins, for the start-up log line. */
  public Set<String> allowedOrigins() {
    return allowedOrigins;
  }

  /**
   * Checks one request.
   *
   * @param method HTTP method
   * @param origin Origin header or null
   * @param referer Referer header or null
   * @param secFetchSite Sec-Fetch-Site header or null
   */
  public Decision check(String method, String origin, String referer, String secFetchSite) {
    if (method != null && SAFE_METHODS.contains(method.toUpperCase(Locale.ROOT))) {
      return new Decision(true, "safe method");
    }
    String site = secFetchSite == null ? "" : secFetchSite.trim().toLowerCase(Locale.ROOT);
    if (site.equals("same-origin")) {
      return new Decision(true, "same origin");
    }
    if (origin != null && !origin.isBlank()) {
      if (origin.trim().equalsIgnoreCase("null")) {
        return new Decision(false, "opaque origin");
      }
      String normalized = originOf(origin);
      return normalized != null && allowedOrigins.contains(normalized)
          ? new Decision(true, "origin allowed")
          : new Decision(false, "origin not allowed");
    }
    if (referer != null && !referer.isBlank()) {
      String normalized = originOf(referer);
      return normalized != null && allowedOrigins.contains(normalized)
          ? new Decision(true, "referer allowed")
          : new Decision(false, "referer not allowed");
    }
    if (site.equals("cross-site") || site.equals("same-site")) {
      return new Decision(false, "browser reports " + site + " without origin");
    }
    return new Decision(true, "no browser headers");
  }

  /**
   * Reduces a URL to "scheme://host[:port]" in lower case, without the default port. Returns null
   * when the text is blank or not a URL with a scheme and host.
   */
  public static String originOf(String url) {
    if (url == null || url.isBlank()) {
      return null;
    }
    try {
      URI uri = new URI(url.trim());
      String scheme = uri.getScheme();
      String host = uri.getHost();
      if (scheme == null || host == null) {
        return null;
      }
      scheme = scheme.toLowerCase(Locale.ROOT);
      host = host.toLowerCase(Locale.ROOT);
      int port = uri.getPort();
      boolean defaultPort =
          port == -1
              || (scheme.equals("https") && port == 443)
              || (scheme.equals("http") && port == 80);
      return defaultPort ? scheme + "://" + host : scheme + "://" + host + ":" + port;
    } catch (java.net.URISyntaxException e) {
      return null;
    }
  }
}
