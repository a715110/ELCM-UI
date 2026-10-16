package com.dodaso.ecosystem.elcm.ui.service.quality;

import com.dodaso.ecosystem.baseline.common.constant.ServiceDiscoveryEnum;
import com.dodaso.ecosystem.baseline.common.proxy.RESTServiceClient;
import com.dodaso.ecosystem.elcm.ui.constant.ExtractionQualityControllerAPIEnum;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

/**
 * elcm-ui side of the Extraction Quality screen (ADDED 2026-10-16). Reads only. A failed read gives
 * null, never an exception, so the screen shows a message instead of failing.
 */
@Service
@Slf4j
public class ExtractionQualityUiService {

  private static final String SERVICE = ServiceDiscoveryEnum.elcm_service.getServiceDiscoveryName();

  @Autowired private RESTServiceClient restServiceClient;

  /**
   * Reads the report.
   *
   * @param from first day, yyyy-mm-dd
   * @param to last day, yyyy-mm-dd
   * @param workspace workspace name, blank for all
   * @param template template name and version, blank for all
   * @param model model name, blank for all
   * @param granularity WEEK or MONTH
   */
  public ExtractionQualityView report(
      final String from,
      final String to,
      final String workspace,
      final String template,
      final String model,
      final String granularity) {
    final StringBuilder query = new StringBuilder();
    add(query, "from", from);
    add(query, "to", to);
    add(query, "workspace", workspace);
    add(query, "template", template);
    add(query, "model", model);
    add(query, "granularity", granularity);
    try {
      return restServiceClient.get(
          SERVICE,
          ExtractionQualityControllerAPIEnum.getReport.getEndPoint() + query,
          new ParameterizedTypeReference<ExtractionQualityView>() {});
    } catch (final Exception e) {
      log.error("Failed to load the extraction quality report", e);
      return null;
    }
  }

  private static void add(final StringBuilder query, final String name, final String value) {
    if (value == null || value.isBlank()) {
      return;
    }
    query
        .append(query.length() == 0 ? "?" : "&")
        .append(name)
        .append('=')
        .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
  }
}
