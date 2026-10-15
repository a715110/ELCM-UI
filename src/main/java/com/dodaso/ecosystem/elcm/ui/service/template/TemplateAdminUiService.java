package com.dodaso.ecosystem.elcm.ui.service.template;

import com.dodaso.ecosystem.baseline.common.constant.ServiceDiscoveryEnum;
import com.dodaso.ecosystem.baseline.common.proxy.RESTServiceClient;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.PackageOutcome;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * elcm-ui side of the Extraction Templates screen (ADDED 2026-10-14). Reads give null (or an empty
 * list) when the call fails; writes give a Result whose outcome says what happened, never an
 * exception.
 */
@Service
@Slf4j
public class TemplateAdminUiService {

  private static final String SERVICE = ServiceDiscoveryEnum.elcm_service.getServiceDiscoveryName();
  private static final String BASE = "/api/v1/pipeline/extraction-templates";

  @Autowired private RESTServiceClient restServiceClient;

  /** Every version of every template; null when the call failed. */
  public List<TemplateRows.Summary> list() {
    try {
      return restServiceClient.get(
          SERVICE, BASE, new ParameterizedTypeReference<List<TemplateRows.Summary>>() {});
    } catch (final Exception e) {
      log.error("Failed to load the extraction templates", e);
      return null;
    }
  }

  public TemplateRows.Options options() {
    try {
      return restServiceClient.get(
          SERVICE, BASE + "/options", new ParameterizedTypeReference<TemplateRows.Options>() {});
    } catch (final Exception e) {
      log.error("Failed to load the template options", e);
      return null;
    }
  }

  public TemplateRows.Detail detail(final Long id) {
    try {
      return restServiceClient.get(
          SERVICE, BASE + "/" + id, new ParameterizedTypeReference<TemplateRows.Detail>() {});
    } catch (final Exception e) {
      log.error("Failed to load extraction template version {}", id, e);
      return null;
    }
  }

  public List<TemplateRows.Event> events(final Long id) {
    try {
      final List<TemplateRows.Event> rows =
          restServiceClient.get(
              SERVICE,
              BASE + "/" + id + "/events",
              new ParameterizedTypeReference<List<TemplateRows.Event>>() {});
      return rows != null ? rows : List.of();
    } catch (final Exception e) {
      log.error("Failed to load the change log of extraction template version {}", id, e);
      return List.of();
    }
  }

  public TemplateRows.Diff compare(final Long fromId, final Long toId) {
    try {
      return restServiceClient.get(
          SERVICE,
          BASE + "/" + fromId + "/compare/" + toId,
          new ParameterizedTypeReference<TemplateRows.Diff>() {});
    } catch (final Exception e) {
      log.error("Failed to compare extraction template versions {} and {}", fromId, toId, e);
      return null;
    }
  }

  public TemplateRows.Result createTemplate(
      final String name, final Long contractTypeId, final String reason) {
    final Map<String, Object> body = new HashMap<>();
    body.put("name", name);
    body.put("contractTypeId", contractTypeId);
    body.put("reason", reason);
    return post(BASE, body, "create the template " + name);
  }

  /** A draft copied from the given version. The detail of the new draft is in the result. */
  public TemplateRows.Result createDraft(final Long basedOnId) {
    try {
      final TemplateRows.Detail detail =
          restServiceClient.post(
              SERVICE,
              BASE + "/" + basedOnId + "/draft",
              new HashMap<String, Object>(),
              new ParameterizedTypeReference<TemplateRows.Detail>() {});
      final TemplateRows.Result r = new TemplateRows.Result();
      r.setDetail(detail);
      return r;
    } catch (final HttpStatusCodeException e) {
      return refused(e, "create a draft from version " + basedOnId);
    } catch (final Exception e) {
      log.error("Failed to create a draft from extraction template version {}", basedOnId, e);
      return TemplateRows.Result.of(PackageOutcome.FAILED);
    }
  }

  public TemplateRows.Result save(final Long id, final List<TemplateRows.Field> fields) {
    final Map<String, Object> body = new HashMap<>();
    body.put("fields", fields);
    try {
      return restServiceClient.put(
          SERVICE, BASE + "/" + id, body, new ParameterizedTypeReference<TemplateRows.Result>() {});
    } catch (final HttpStatusCodeException e) {
      return refused(e, "save draft " + id);
    } catch (final Exception e) {
      log.error("Failed to save extraction template draft {}", id, e);
      return TemplateRows.Result.of(PackageOutcome.FAILED);
    }
  }

  public TemplateRows.Result publish(final Long id, final String reason) {
    final Map<String, Object> body = new HashMap<>();
    body.put("reason", reason);
    return post(BASE + "/" + id + "/publish", body, "publish draft " + id);
  }

  public PackageOutcome discard(final Long id, final String reason) {
    final Map<String, Object> body = new HashMap<>();
    body.put("reason", reason);
    try {
      restServiceClient.post(
          SERVICE, BASE + "/" + id + "/discard", body, new ParameterizedTypeReference<Void>() {});
      return PackageOutcome.OK;
    } catch (final HttpStatusCodeException e) {
      return refused(e, "discard draft " + id).getOutcome();
    } catch (final Exception e) {
      log.error("Failed to discard extraction template draft {}", id, e);
      return PackageOutcome.FAILED;
    }
  }

  private TemplateRows.Result post(
      final String endpoint, final Map<String, Object> body, final String what) {
    try {
      final TemplateRows.Result r =
          restServiceClient.post(
              SERVICE, endpoint, body, new ParameterizedTypeReference<TemplateRows.Result>() {});
      return r != null ? r : TemplateRows.Result.of(PackageOutcome.FAILED);
    } catch (final HttpStatusCodeException e) {
      return refused(e, what);
    } catch (final Exception e) {
      log.error("Failed to {}", what, e);
      return TemplateRows.Result.of(PackageOutcome.FAILED);
    }
  }

  private static TemplateRows.Result refused(final HttpStatusCodeException e, final String what) {
    log.warn("elcm-service refused to {}: HTTP {}", what, e.getStatusCode().value());
    return TemplateRows.Result.of(
        switch (e.getStatusCode().value()) {
          case 404 -> PackageOutcome.NOT_FOUND;
          case 409 -> PackageOutcome.CONFLICT;
          case 400 -> PackageOutcome.INVALID;
          case 403 -> PackageOutcome.FORBIDDEN;
          default -> PackageOutcome.FAILED;
        });
  }
}
