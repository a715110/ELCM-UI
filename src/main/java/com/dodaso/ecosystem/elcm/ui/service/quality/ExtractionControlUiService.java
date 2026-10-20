package com.dodaso.ecosystem.elcm.ui.service.quality;

import com.dodaso.ecosystem.baseline.common.constant.ServiceDiscoveryEnum;
import com.dodaso.ecosystem.baseline.common.proxy.RESTServiceClient;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.PackageOutcome;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * ADDED 2026-10-18 -- elcm-ui side of Pause and Resume of the extraction agent. A failed read gives
 * null; a write gives an outcome the screen turns into words.
 */
@Service
@Slf4j
public class ExtractionControlUiService {

  private static final String SERVICE = ServiceDiscoveryEnum.elcm_service.getServiceDiscoveryName();
  private static final String BASE = "/api/v1/extraction/control";

  @Autowired private RESTServiceClient restServiceClient;

  /** Result of a Pause or Resume: the outcome, and the new state when it worked. */
  public record Result(PackageOutcome outcome, ExtractionControlView view) {}

  /** The current state, or null when it could not be read. */
  public ExtractionControlView state() {
    try {
      return restServiceClient.get(
          SERVICE, BASE, new ParameterizedTypeReference<ExtractionControlView>() {});
    } catch (final Exception e) {
      log.error("Failed to read the extraction control state", e);
      return null;
    }
  }

  /** Pauses the agent with a reason. */
  public Result pause(final String reason) {
    final Map<String, Object> body = new HashMap<>();
    body.put("reason", reason);
    return send(BASE + "/pause", body, "pause extraction");
  }

  /** Lets the agent work again. */
  public Result resume() {
    return send(BASE + "/resume", new HashMap<String, Object>(), "resume extraction");
  }

  private Result send(final String endpoint, final Object body, final String what) {
    try {
      final ExtractionControlView view =
          restServiceClient.post(
              SERVICE, endpoint, body, new ParameterizedTypeReference<ExtractionControlView>() {});
      return new Result(PackageOutcome.OK, view);
    } catch (final HttpStatusCodeException e) {
      final HttpStatusCode code = e.getStatusCode();
      log.warn("elcm-service refused to {}: HTTP {}", what, code.value());
      return new Result(
          switch (code.value()) {
            case 400 -> PackageOutcome.INVALID;
            case 403 -> PackageOutcome.FORBIDDEN;
            default -> PackageOutcome.FAILED;
          },
          null);
    } catch (final Exception e) {
      log.error("Failed to {}", what, e);
      return new Result(PackageOutcome.FAILED, null);
    }
  }
}
