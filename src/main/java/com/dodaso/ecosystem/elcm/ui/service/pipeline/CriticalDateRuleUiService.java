package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import com.dodaso.ecosystem.baseline.common.constant.ServiceDiscoveryEnum;
import com.dodaso.ecosystem.baseline.common.proxy.RESTServiceClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * ADDED 2026-11-21. elcm-ui side of the Critical Date Rules screen. A failed read gives null. A
 * write gives a {@link CriticalDateRuleResult} with the screen as it stands afterwards, or the
 * reason elcm-service gave (or a default for the status).
 */
@Service
@Slf4j
public class CriticalDateRuleUiService {

  private static final String SERVICE =
      ServiceDiscoveryEnum.elcm_service.getServiceDiscoveryName();
  private static final String BASE = "/api/v1/pipeline/critical-dates/rules";
  private static final String FAILED = "The action could not be completed. Please try again.";

  @Autowired
  private RESTServiceClient restServiceClient;

  @Autowired
  private ObjectMapper objectMapper;

  public CriticalDateRuleRows.AdminView view() {
    try {
      return restServiceClient.get(SERVICE, BASE,
          new ParameterizedTypeReference<CriticalDateRuleRows.AdminView>() {
          });
    } catch (final Exception e) {
      log.error("Failed to load the critical date rules", e);
      return null;
    }
  }

  public CriticalDateRuleResult create(final String code, final String label,
      final String fieldKey, final String noticeFieldKey, final Integer leadDays,
      final String priority, final boolean enabled) {
    return write("POST", BASE,
        body(code, label, fieldKey, noticeFieldKey, leadDays, priority, enabled));
  }

  public CriticalDateRuleResult update(final Long id, final String label, final String fieldKey,
      final String noticeFieldKey, final Integer leadDays, final String priority,
      final boolean enabled) {
    return write("PUT", BASE + "/" + id,
        body(null, label, fieldKey, noticeFieldKey, leadDays, priority, enabled));
  }

  public CriticalDateRuleResult setEnabled(final Long id, final boolean enabled) {
    final Map<String, Object> body = new HashMap<>();
    body.put("enabled", enabled);
    return write("POST", BASE + "/" + id + "/enabled", body);
  }

  public CriticalDateRuleResult delete(final Long id) {
    return write("POST", BASE + "/" + id + "/delete", new HashMap<String, Object>());
  }

  public CriticalDateRuleResult checkNow() {
    try {
      final CriticalDateRuleRows.CheckResult check = restServiceClient.post(SERVICE,
          BASE + "/check", new HashMap<String, Object>(),
          new ParameterizedTypeReference<CriticalDateRuleRows.CheckResult>() {
          });
      return CriticalDateRuleResult.okCheck(check);
    } catch (final HttpStatusCodeException e) {
      return CriticalDateRuleResult.failed(messageOf(e));
    } catch (final Exception e) {
      log.error("Failed to run the critical date check", e);
      return CriticalDateRuleResult.failed(FAILED);
    }
  }

  private static Map<String, Object> body(final String code, final String label,
      final String fieldKey, final String noticeFieldKey, final Integer leadDays,
      final String priority, final boolean enabled) {
    final Map<String, Object> body = new HashMap<>();
    body.put("code", code);
    body.put("label", label);
    body.put("fieldKey", fieldKey);
    body.put("noticeFieldKey", noticeFieldKey);
    body.put("leadDays", leadDays);
    body.put("priority", priority);
    body.put("enabled", enabled);
    return body;
  }

  private CriticalDateRuleResult write(final String method, final String endpoint,
      final Object body) {
    try {
      final ParameterizedTypeReference<CriticalDateRuleRows.AdminView> type =
          new ParameterizedTypeReference<CriticalDateRuleRows.AdminView>() {
          };
      final CriticalDateRuleRows.AdminView view = "PUT".equals(method)
          ? restServiceClient.put(SERVICE, endpoint, body, type)
          : restServiceClient.post(SERVICE, endpoint, body, type);
      return CriticalDateRuleResult.ok(view);
    } catch (final HttpStatusCodeException e) {
      log.warn("elcm-service refused {} {}: HTTP {}", method, endpoint, e.getStatusCode().value());
      return CriticalDateRuleResult.failed(messageOf(e));
    } catch (final Exception e) {
      log.error("Failed {} {}", method, endpoint, e);
      return CriticalDateRuleResult.failed(FAILED);
    }
  }

  private String messageOf(final HttpStatusCodeException e) {
    try {
      final JsonNode node = objectMapper.readTree(e.getResponseBodyAsString());
      final String text = node.path("message").asText("");
      if (!text.isBlank()) {
        return text;
      }
    } catch (final Exception ignored) {
      // Fall through to the default for the status.
    }
    final int status = e.getStatusCode().value();
    if (status == 403) {
      return "You are not allowed to change the critical date rules.";
    }
    if (status == 404) {
      return "That rule no longer exists.";
    }
    return FAILED;
  }
}
