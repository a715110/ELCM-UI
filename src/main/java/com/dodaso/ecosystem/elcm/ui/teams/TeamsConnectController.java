package com.dodaso.ecosystem.elcm.ui.teams;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.HtmlUtils;

/**
 * ADDED 2026-10-24 (ported from ECWS).
 * "Connect Microsoft account" for the Teams direct message. This is a small
 * authorization code flow with PKCE against Microsoft Entra that runs next to the normal SSO
 * sign-in and does not replace it: the SSO principal is untouched, only the Graph tokens are kept
 * in the session (TeamsTokenStore).
 */
@Controller
@Slf4j
public class TeamsConnectController {

  private static final String STATE_KEY = "dodaso.teams.state";
  private static final String VERIFIER_KEY = "dodaso.teams.verifier";
  private static final SecureRandom RANDOM = new SecureRandom();

  @Autowired
  private TeamsTokenStore tokenStore;

  @Value("${messaging.teams.redirect-uri}")
  private String redirectUri;

  @GetMapping("/teams/connect")
  public void connect(final HttpSession session, final HttpServletResponse response)
      throws Exception {
    final String state = random(24);
    final String verifier = random(48);
    session.setAttribute(STATE_KEY, state);
    session.setAttribute(VERIFIER_KEY, verifier);
    final String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
        MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
    final String url = tokenStore.authorizeUrl()
        + "?client_id=" + TeamsTokenStore.enc(tokenStore.clientId())
        + "&response_type=code"
        + "&redirect_uri=" + TeamsTokenStore.enc(redirectUri)
        + "&response_mode=query"
        + "&scope=" + TeamsTokenStore.enc(tokenStore.scopes())
        + "&state=" + state
        + "&code_challenge=" + challenge
        + "&code_challenge_method=S256"
        + "&prompt=select_account";
    response.sendRedirect(url);
  }

  @GetMapping("/teams/callback")
  public ResponseEntity<String> callback(
      @RequestParam(required = false) final String code,
      @RequestParam(required = false) final String state,
      @RequestParam(required = false) final String error,
      @RequestParam(name = "error_description", required = false) final String errorDescription,
      final HttpSession session) {
    final String expectedState = (String) session.getAttribute(STATE_KEY);
    final String verifier = (String) session.getAttribute(VERIFIER_KEY);
    session.removeAttribute(STATE_KEY);
    session.removeAttribute(VERIFIER_KEY);

    if (error != null) {
      log.warn("Teams connect refused: {} {}", error, errorDescription);
      return page(false, "Microsoft said: " + (errorDescription != null ? errorDescription : error));
    }
    if (code == null || state == null || expectedState == null || !expectedState.equals(state)
        || verifier == null) {
      return page(false, "The sign-in could not be verified. Close this window and try again.");
    }
    try {
      final JsonNode tokens = tokenStore.exchangeCode(code, verifier, redirectUri);
      if (tokens == null) {
        return page(false, "Microsoft did not return a token. See the server log.");
      }
      tokenStore.save(session, tokens);
      return page(true, "Your Microsoft account is connected.");
    } catch (final Exception e) {
      log.error("Teams connect failed", e);
      return page(false, "Connect failed: " + e.getMessage());
    }
  }

  private static ResponseEntity<String> page(final boolean ok, final String text) {
    final String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
        + "<title>Microsoft Teams</title></head>"
        + "<body style=\"font-family:Segoe UI,Arial,sans-serif;text-align:center;margin-top:80px\">"
        + "<h2 style=\"color:" + (ok ? "#16a34a" : "#dc2626") + "\">"
        + (ok ? "Connected to Microsoft Teams" : "Not connected") + "</h2>"
        + "<p>" + HtmlUtils.htmlEscape(text) + "</p>"
        + (ok ? "<p>Close this window and press Send again.</p>" : "")
        + "<script>" + (ok ? "setTimeout(function(){window.close();},2500);" : "") + "</script>"
        + "</body></html>";
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
  }

  private static String random(final int bytes) {
    final byte[] buffer = new byte[bytes];
    RANDOM.nextBytes(buffer);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
  }
}
