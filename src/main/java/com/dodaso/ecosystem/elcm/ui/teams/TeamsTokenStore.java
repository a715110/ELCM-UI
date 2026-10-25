package com.dodaso.ecosystem.elcm.ui.teams;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ADDED 2026-10-24 (ported from ECWS).
 * Keeps the signed-in person's Microsoft Graph tokens in their HTTP session
 * (never in the database, never in logs) and refreshes the access token when it is about to
 * expire. Used for the Teams direct message, which must be sent as the person.
 */
@Component
@Slf4j
public class TeamsTokenStore {

  static final String SESSION_KEY = "dodaso.teams.tokens";
  private static final long SKEW_SECONDS = 120;

  @Value("${messaging.teams.tenant-id:}")
  private String tenantId;

  @Value("${messaging.teams.client-id:}")
  private String clientId;

  @Value("${messaging.teams.client-secret:}")
  private String clientSecret;

  @Value("${messaging.teams.scopes:User.Read offline_access Chat.Create ChatMessage.Send}")
  private String scopes;

  private final ObjectMapper mapper = new ObjectMapper();
  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10)).build();

  /** Session held tokens. */
  static final class Tokens implements Serializable {

    private static final long serialVersionUID = 1L;
    String accessToken;
    String refreshToken;
    Instant expiresAt;
  }

  /** Stores the result of a code exchange. */
  public void save(final HttpSession session, final JsonNode tokenResponse) {
    final Tokens tokens = new Tokens();
    tokens.accessToken = tokenResponse.path("access_token").asText(null);
    tokens.refreshToken = tokenResponse.path("refresh_token").asText(null);
    tokens.expiresAt = Instant.now().plusSeconds(tokenResponse.path("expires_in").asLong(3000));
    session.setAttribute(SESSION_KEY, tokens);
  }

  /** A usable access token, refreshed if needed, or null when the person must connect. */
  public String accessToken(final HttpSession session) {
    if (session == null || !(session.getAttribute(SESSION_KEY) instanceof Tokens tokens)) {
      return null;
    }
    if (tokens.accessToken != null
        && Instant.now().plusSeconds(SKEW_SECONDS).isBefore(tokens.expiresAt)) {
      return tokens.accessToken;
    }
    if (tokens.refreshToken == null) {
      return null;
    }
    try {
      final JsonNode refreshed = post("grant_type=refresh_token"
          + "&refresh_token=" + enc(tokens.refreshToken)
          + "&scope=" + enc(scopes));
      if (refreshed == null) {
        session.removeAttribute(SESSION_KEY);
        return null;
      }
      final String keepRefresh = tokens.refreshToken;
      save(session, refreshed);
      final Tokens updated = (Tokens) session.getAttribute(SESSION_KEY);
      if (updated.refreshToken == null) {
        updated.refreshToken = keepRefresh;
      }
      return updated.accessToken;
    } catch (final Exception e) {
      log.warn("Teams token refresh failed: {}", e.getMessage());
      return null;
    }
  }

  /** Exchanges an authorization code (PKCE) for tokens; null when Microsoft refused. */
  public JsonNode exchangeCode(final String code, final String verifier,
      final String redirectUri) throws Exception {
    return post("grant_type=authorization_code"
        + "&code=" + enc(code)
        + "&redirect_uri=" + enc(redirectUri)
        + "&code_verifier=" + enc(verifier)
        + "&scope=" + enc(scopes));
  }

  private JsonNode post(final String form) throws Exception {
    if (clientSecret == null || clientSecret.isBlank()) {
      log.error("messaging.teams.client-secret is empty: set ELCM_ENTRA_CLIENT_SECRET");
      return null;
    }
    final HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token"))
        .timeout(Duration.ofSeconds(20))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(
            form + "&client_id=" + enc(clientId) + "&client_secret=" + enc(clientSecret),
            StandardCharsets.UTF_8))
        .build();
    final HttpResponse<String> response =
        http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    final JsonNode json = mapper.readTree(response.body());
    if (response.statusCode() / 100 != 2) {
      log.warn("Microsoft token endpoint HTTP {}: {} {}", response.statusCode(),
          json.path("error").asText(), json.path("error_description").asText());
      return null;
    }
    return json;
  }

  public String authorizeUrl() {
    return "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/authorize";
  }

  public String clientId() {
    return clientId;
  }

  public String scopes() {
    return scopes;
  }

  static String enc(final String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
