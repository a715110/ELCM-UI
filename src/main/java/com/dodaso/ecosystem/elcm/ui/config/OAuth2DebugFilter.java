package com.dodaso.ecosystem.elcm.ui.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Date;
import java.util.Enumeration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OAuth2DebugFilter implements Filter {

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    HttpServletRequest httpRequest = (HttpServletRequest) request;
    String requestURI = httpRequest.getRequestURI();
    String queryString = httpRequest.getQueryString();

    // Log OAuth2 authorization initiation
    if (requestURI.contains("/oauth2/authorization/")) {
      log.trace("=== OAuth2 AUTHORIZATION INITIATION ===");
      log.trace("URI: {}", requestURI);
      HttpSession session = httpRequest.getSession(true);
      log.trace("Session ID: {}", session.getId());
      log.trace("Session is new: {}", session.isNew());
    }

    // Log OAuth2 callback with detailed session analysis
    if (requestURI.contains("/login/oauth2/code/")) {
      log.trace("=== OAuth2 CALLBACK ANALYSIS ===");
      log.trace("URI: {}", requestURI);
      log.trace("Query: {}", queryString);

      HttpSession session = httpRequest.getSession(false);
      if (session != null) {
        log.trace("Session ID: {}", session.getId());
        log.trace("Session creation time: {}", new Date(session.getCreationTime()));
        log.trace("Session last accessed: {}", new Date(session.getLastAccessedTime()));

        // Look for the specific authorization request attribute
        String authReqKey = "org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository.AUTHORIZATION_REQUEST";
        Object authReq = session.getAttribute(authReqKey);
        log.trace("Authorization request in session: {}", authReq != null);

        if (authReq != null) {
          log.trace("Authorization request details: {}", authReq.toString());
        } else {
          log.trace("Session attributes:");
          Enumeration<String> names = session.getAttributeNames();
          while (names.hasMoreElements()) {
            String name = names.nextElement();
            log.trace("  {} = {}", name, session.getAttribute(name));
          }
        }
      } else {
        log.trace("NO SESSION FOUND during callback!");
      }
    }

    chain.doFilter(request, response);
  }
}