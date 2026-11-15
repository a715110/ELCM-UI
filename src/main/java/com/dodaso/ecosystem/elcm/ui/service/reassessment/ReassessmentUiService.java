package com.dodaso.ecosystem.elcm.ui.service.reassessment;

import com.dodaso.ecosystem.auth.constant.UserControllerAPIEnum;
import com.dodaso.ecosystem.auth.container.UserDirectoryDTOContainer;
import com.dodaso.ecosystem.baseline.common.constant.ServiceDiscoveryEnum;
import com.dodaso.ecosystem.baseline.common.proxy.RESTServiceClient;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.AssignableUserOptionRow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * elcm-ui side of lease reassessment (ADDED 2026-11-11). Reads give an empty list or null when the
 * call fails, never an exception. Writes give a {@link ReassessmentResult} carrying either the new
 * case page or a plain message (the reason elcm-service gave, else a default for the status).
 */
@Service
@Slf4j
public class ReassessmentUiService {

  private static final String SERVICE = ServiceDiscoveryEnum.elcm_service.getServiceDiscoveryName();
  private static final String BASE = "/api/v1/reassessment";

  @Autowired
  private RESTServiceClient restServiceClient;

  @Autowired
  private ObjectMapper objectMapper;

  public List<ReassessmentRows.CaseRow> list(final String scope, final Long recordId) {
    final StringBuilder url = new StringBuilder(BASE + "/cases?scope="
        + URLEncoder.encode(scope == null ? "mine" : scope, StandardCharsets.UTF_8));
    if (recordId != null) {
      url.append("&recordId=").append(recordId);
    }
    try {
      final List<ReassessmentRows.CaseRow> rows = restServiceClient.get(SERVICE, url.toString(),
          new ParameterizedTypeReference<List<ReassessmentRows.CaseRow>>() {
          });
      return rows != null ? rows : List.of();
    } catch (final Exception e) {
      log.error("Failed to load the reassessments", e);
      return null;
    }
  }

  public ReassessmentRows.CaseDetail detail(final Long id) {
    try {
      return restServiceClient.get(SERVICE, BASE + "/cases/" + id,
          new ParameterizedTypeReference<ReassessmentRows.CaseDetail>() {
          });
    } catch (final Exception e) {
      log.error("Failed to load reassessment {}", id, e);
      return null;
    }
  }

  public List<ReassessmentRows.Option> triggerTypes() {
    return options("/trigger-types");
  }

  public List<ReassessmentRows.Option> outcomes() {
    return options("/outcomes");
  }

  public List<ReassessmentRows.Option> surveyTemplates() {
    return options("/survey-templates");
  }

  /** Everyone in the IAMS directory, for the respondent picker. Empty on failure. */
  public List<AssignableUserOptionRow> people() {
    try {
      final UserDirectoryDTOContainer container = restServiceClient.get(
          ServiceDiscoveryEnum.iams_service.getServiceDiscoveryName(),
          UserControllerAPIEnum.userControllerAPIEnum_getUserDirectories.getEndPoint(),
          new ParameterizedTypeReference<UserDirectoryDTOContainer>() {
          });
      if (container == null || container.getUserDirectoryDTOList() == null) {
        return List.of();
      }
      return container.getUserDirectoryDTOList().stream()
          .filter(d -> d.getLoginId() != null && !d.getLoginId().isBlank())
          .filter(d -> d.getDisplayName() != null && !d.getDisplayName().isBlank())
          .map(d -> new AssignableUserOptionRow(d.getLoginId(), d.getDisplayName(), d.getTeamName()))
          .toList();
    } catch (final Exception e) {
      log.error("Failed to load the user directory", e);
      return List.of();
    }
  }

  public ReassessmentResult open(final Long recordId, final String triggerCode,
      final String description, final java.time.LocalDate eventDate,
      final java.time.LocalDate dueDate) {
    final Map<String, Object> body = new HashMap<>();
    body.put("recordId", recordId);
    body.put("triggerCode", triggerCode);
    body.put("description", description);
    body.put("eventDate", eventDate == null ? null : eventDate.toString());
    body.put("dueDate", dueDate == null ? null : dueDate.toString());
    return write("POST", BASE + "/cases", body);
  }

  public ReassessmentResult sendSurvey(final Long id, final String templateCode,
      final String respondent, final String businessUnit, final java.time.LocalDate dueDate) {
    final Map<String, Object> body = new HashMap<>();
    body.put("templateCode", templateCode);
    body.put("respondent", respondent);
    body.put("businessUnit", businessUnit);
    body.put("dueDate", dueDate == null ? null : dueDate.toString());
    return write("POST", BASE + "/cases/" + id + "/survey", body);
  }

  public ReassessmentResult answer(final Long id, final Map<String, String> answers,
      final boolean submit) {
    final Map<String, Object> body = new HashMap<>();
    body.put("answers", answers);
    body.put("submit", submit);
    return write("POST", BASE + "/cases/" + id + "/survey/answers", body);
  }

  public ReassessmentResult conclude(final Long id, final String outcomeCode, final String summary,
      final boolean submit) {
    final Map<String, Object> body = new HashMap<>();
    body.put("outcomeCode", outcomeCode);
    body.put("summary", summary);
    body.put("submit", submit);
    return write("PUT", BASE + "/cases/" + id + "/conclusion", body);
  }

  public ReassessmentResult decide(final Long id, final String decision, final String comment) {
    final Map<String, Object> body = new HashMap<>();
    body.put("decision", decision);
    body.put("comment", comment);
    return write("POST", BASE + "/cases/" + id + "/conclusion/decision", body);
  }

  public ReassessmentResult linkReplacement(final Long id, final Long replacementRecordId,
      final java.time.LocalDate effectiveDate, final String notes) {
    final Map<String, Object> body = new HashMap<>();
    body.put("replacementRecordId", replacementRecordId);
    body.put("effectiveDate", effectiveDate == null ? null : effectiveDate.toString());
    body.put("notes", notes);
    return write("POST", BASE + "/cases/" + id + "/replacement", body);
  }

  public ReassessmentResult close(final Long id, final String comment) {
    final Map<String, Object> body = new HashMap<>();
    body.put("comment", comment);
    return write("POST", BASE + "/cases/" + id + "/close", body);
  }

  private List<ReassessmentRows.Option> options(final String path) {
    try {
      final List<ReassessmentRows.Option> rows = restServiceClient.get(SERVICE, BASE + path,
          new ParameterizedTypeReference<List<ReassessmentRows.Option>>() {
          });
      return rows != null ? rows : List.of();
    } catch (final Exception e) {
      log.error("Failed to load reassessment options {}", path, e);
      return List.of();
    }
  }

  private ReassessmentResult write(final String method, final String endpoint, final Object body) {
    try {
      final ParameterizedTypeReference<ReassessmentRows.CaseDetail> type =
          new ParameterizedTypeReference<ReassessmentRows.CaseDetail>() {
          };
      final ReassessmentRows.CaseDetail detail = "PUT".equals(method)
          ? restServiceClient.put(SERVICE, endpoint, body, type)
          : restServiceClient.post(SERVICE, endpoint, body, type);
      return ReassessmentResult.ok(detail);
    } catch (final HttpStatusCodeException e) {
      log.warn("elcm-service refused {} {}: HTTP {}", method, endpoint, e.getStatusCode().value());
      return ReassessmentResult.failed(messageOf(e));
    } catch (final Exception e) {
      log.error("Failed {} {}", method, endpoint, e);
      return ReassessmentResult.failed("The action could not be completed. Please try again.");
    }
  }

  /** The reason elcm-service gave, when its error body carries one; else a default per status. */
  private String messageOf(final HttpStatusCodeException e) {
    try {
      final JsonNode node = objectMapper.readTree(e.getResponseBodyAsString());
      final String text = node.path("message").asText("");
      if (!text.isBlank()) {
        return text;
      }
    } catch (final Exception ignored) {
      // fall through to the default text
    }
    return switch (e.getStatusCode().value()) {
      case 403 -> "You do not have permission for this action.";
      case 404 -> "The reassessment no longer exists.";
      case 409 -> "The reassessment is not in a state that allows this.";
      case 412 -> "A role rule refused this action.";
      case 422, 400 -> "Some of the information is not valid.";
      case 503 -> "Lease reassessment is not switched on.";
      default -> "The action could not be completed. Please try again.";
    };
  }
}
