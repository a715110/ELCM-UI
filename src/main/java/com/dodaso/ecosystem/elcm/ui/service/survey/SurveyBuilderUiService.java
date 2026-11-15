package com.dodaso.ecosystem.elcm.ui.service.survey;

import com.dodaso.ecosystem.baseline.common.constant.ServiceDiscoveryEnum;
import com.dodaso.ecosystem.baseline.common.proxy.RESTServiceClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * ADDED 2026-11-13. elcm-ui side of the survey builder. Reads give null when the call fails.
 * Writes give a {@link SurveyBuilderResult} with either the new state or the reason elcm-service
 * gave (or a default for the status).
 */
@Service
@Slf4j
public class SurveyBuilderUiService {

  private static final String SERVICE = ServiceDiscoveryEnum.elcm_service.getServiceDiscoveryName();
  private static final String BASE = "/api/v1/reassessment/survey-builder";

  @Autowired
  private RESTServiceClient restServiceClient;

  @Autowired
  private ObjectMapper objectMapper;

  public List<SurveyRows.TemplateRow> list() {
    try {
      return restServiceClient.get(SERVICE, BASE + "/templates",
          new ParameterizedTypeReference<List<SurveyRows.TemplateRow>>() {
          });
    } catch (final Exception e) {
      log.error("Failed to load the surveys", e);
      return null;
    }
  }

  public SurveyRows.TemplateDetail detail(final Long id) {
    try {
      return restServiceClient.get(SERVICE, BASE + "/templates/" + id,
          new ParameterizedTypeReference<SurveyRows.TemplateDetail>() {
          });
    } catch (final Exception e) {
      log.error("Failed to load survey {}", id, e);
      return null;
    }
  }

  public SurveyBuilderResult create(final String name, final String description) {
    final Map<String, Object> body = new HashMap<>();
    body.put("name", name);
    body.put("description", description);
    return detailWrite("POST", BASE + "/templates", body);
  }

  public SurveyBuilderResult update(final Long id, final String name, final String description,
      final boolean published) {
    final Map<String, Object> body = new HashMap<>();
    body.put("name", name);
    body.put("description", description);
    body.put("published", published);
    return detailWrite("PUT", BASE + "/templates/" + id, body);
  }

  public SurveyBuilderResult copy(final Long id, final String name) {
    final Map<String, Object> body = new HashMap<>();
    body.put("name", name);
    return detailWrite("POST", BASE + "/templates/" + id + "/copy", body);
  }

  public SurveyBuilderResult delete(final Long id) {
    try {
      final List<SurveyRows.TemplateRow> rows = restServiceClient.post(SERVICE,
          BASE + "/templates/" + id + "/delete", new HashMap<String, Object>(),
          new ParameterizedTypeReference<List<SurveyRows.TemplateRow>>() {
          });
      return SurveyBuilderResult.okRows(rows);
    } catch (final HttpStatusCodeException e) {
      return SurveyBuilderResult.failed(messageOf(e));
    } catch (final Exception e) {
      log.error("Failed to delete survey {}", id, e);
      return SurveyBuilderResult.failed("The action could not be completed. Please try again.");
    }
  }

  public SurveyBuilderResult addQuestion(final Long templateId, final String text,
      final String type, final String choices, final boolean required, final boolean active) {
    return detailWrite("POST", BASE + "/templates/" + templateId + "/questions",
        questionBody(text, type, choices, required, active));
  }

  public SurveyBuilderResult updateQuestion(final Long questionId, final String text,
      final String type, final String choices, final boolean required, final boolean active) {
    return detailWrite("PUT", BASE + "/questions/" + questionId,
        questionBody(text, type, choices, required, active));
  }

  public SurveyBuilderResult moveQuestion(final Long questionId, final String direction) {
    final Map<String, Object> body = new HashMap<>();
    body.put("direction", direction);
    return detailWrite("POST", BASE + "/questions/" + questionId + "/move", body);
  }

  public SurveyBuilderResult deleteQuestion(final Long questionId) {
    return detailWrite("POST", BASE + "/questions/" + questionId + "/delete",
        new HashMap<String, Object>());
  }

  private static Map<String, Object> questionBody(final String text, final String type,
      final String choices, final boolean required, final boolean active) {
    final Map<String, Object> body = new HashMap<>();
    body.put("text", text);
    body.put("type", type);
    body.put("choices", choices);
    body.put("required", required);
    body.put("active", active);
    return body;
  }

  private SurveyBuilderResult detailWrite(final String method, final String endpoint,
      final Object body) {
    try {
      final ParameterizedTypeReference<SurveyRows.TemplateDetail> type =
          new ParameterizedTypeReference<SurveyRows.TemplateDetail>() {
          };
      final SurveyRows.TemplateDetail detail = "PUT".equals(method)
          ? restServiceClient.put(SERVICE, endpoint, body, type)
          : restServiceClient.post(SERVICE, endpoint, body, type);
      return SurveyBuilderResult.ok(detail);
    } catch (final HttpStatusCodeException e) {
      log.warn("elcm-service refused {} {}: HTTP {}", method, endpoint, e.getStatusCode().value());
      return SurveyBuilderResult.failed(messageOf(e));
    } catch (final Exception e) {
      log.error("Failed {} {}", method, endpoint, e);
      return SurveyBuilderResult.failed("The action could not be completed. Please try again.");
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
      return "You are not allowed to change surveys.";
    }
    if (status == 404) {
      return "That survey or question no longer exists.";
    }
    return "The action could not be completed. Please try again.";
  }
}
