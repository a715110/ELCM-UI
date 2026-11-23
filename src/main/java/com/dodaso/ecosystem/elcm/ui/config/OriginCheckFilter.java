package com.dodaso.ecosystem.elcm.ui.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * ADDED 2026-11-23. Applies {@link OriginPolicy} to every request. Mode comes from
 * dodaso.security.origin-check: enforce (default) answers 403 to a request from another site,
 * report only writes a warning and lets it through (use it to roll out), off does nothing.
 * Nothing sensitive is logged: method, path and the reason only.
 */
@Slf4j
public class OriginCheckFilter extends OncePerRequestFilter {

  /** How a rejected request is treated. */
  public enum Mode {
    ENFORCE,
    REPORT,
    OFF;

    /** Reads the property value; anything unknown means ENFORCE (fail closed). */
    public static Mode parse(String value) {
      if (value == null) {
        return ENFORCE;
      }
      switch (value.trim().toLowerCase(Locale.ROOT)) {
        case "off":
          return OFF;
        case "report":
          return REPORT;
        default:
          return ENFORCE;
      }
    }
  }

  private final OriginPolicy policy;
  private final Mode mode;

  public OriginCheckFilter(OriginPolicy policy, Mode mode) {
    this.policy = policy;
    this.mode = mode;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (mode == Mode.OFF) {
      chain.doFilter(request, response);
      return;
    }
    OriginPolicy.Decision decision =
        policy.check(
            request.getMethod(),
            request.getHeader("Origin"),
            request.getHeader("Referer"),
            request.getHeader("Sec-Fetch-Site"));
    if (decision.allowed()) {
      chain.doFilter(request, response);
      return;
    }
    log.warn(
        "Cross-site request {} {} ({}), mode {}",
        request.getMethod(),
        request.getRequestURI(),
        decision.reason(),
        mode);
    if (mode == Mode.ENFORCE) {
      // Write the answer directly: sendError would go through the /error page, where an
      // unauthenticated caller is turned into a 401.
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      response.setContentType("text/plain;charset=UTF-8");
      response.getWriter().write("Cross-site request blocked");
      return;
    }
    chain.doFilter(request, response);
  }
}
