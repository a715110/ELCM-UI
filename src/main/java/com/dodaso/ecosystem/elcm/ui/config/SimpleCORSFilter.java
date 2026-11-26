package com.dodaso.ecosystem.elcm.ui.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * REVISED 2026-11-24. Was "Access-Control-Allow-Origin: *" together with "Allow-Credentials: true"
 * on every response. The browser reaches this app only through nginx on one origin, so no
 * cross-origin script call is needed. CORS headers are now sent only to an origin listed in
 * dodaso.security.allowed-origins (empty by default), and then that exact origin is echoed.
 * The framing and cache headers are unchanged.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SimpleCORSFilter implements Filter {

  private final Set<String> allowedOrigins;

  public SimpleCORSFilter(
      @Value("${dodaso.security.allowed-origins:}") final String allowedOriginsCsv) {
    this.allowedOrigins = Arrays.stream(allowedOriginsCsv.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(s -> s.endsWith("/") ? s.substring(0, s.length() - 1) : s)
        .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public void init(final FilterConfig arg0) throws ServletException {
  }

  @Override
  public void doFilter(final ServletRequest req, final ServletResponse resp,
      final FilterChain chain) throws IOException, ServletException {

    final HttpServletRequest request = (HttpServletRequest) req;
    final HttpServletResponse response = (HttpServletResponse) resp;
    final String origin = request.getHeader("Origin");
    // Always: the answer depends on the Origin header, so caches must not mix origins
    response.addHeader("Vary", "Origin");
    if (origin != null && allowedOrigins.contains(origin)) {
      response.setHeader("Access-Control-Allow-Origin", origin);
      response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE, PUT");
      response.setHeader("Access-Control-Max-Age", "3600");
      response.setHeader("Access-Control-Allow-Headers",
          "origin, content-type, accept, Authorization");
      response.setHeader("Access-Control-Allow-Credentials", "true");
    }
    response.setHeader("Content-Type", "application/json");
    response.setHeader("X-Frame-Options", "SAMEORIGIN");
    response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
    response.setHeader("Pragma", "no-cache");
    response.setHeader("Expires", "0");
    chain.doFilter(req, resp);
  }

  @Override
  public void destroy() {
  }
}
