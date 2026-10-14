package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import com.dodaso.ecosystem.baseline.common.constant.ServiceDiscoveryEnum;
import com.dodaso.ecosystem.baseline.common.proxy.RESTServiceClient;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

/**
 * elcm-ui side of the Work Queue screen (ADDED 2026-10-14). Reads only. A failed read gives null,
 * never an exception, so the screen shows a message instead of failing.
 */
@Service
@Slf4j
public class WorkQueueUiService {

  private static final String SERVICE = ServiceDiscoveryEnum.elcm_service.getServiceDiscoveryName();
  private static final String BASE = "/api/v1/pipeline/work-queue";

  @Autowired private RESTServiceClient restServiceClient;

  public List<WorkQueueRow> queue() {
    try {
      final List<WorkQueueRow> rows =
          restServiceClient.get(
              SERVICE, BASE, new ParameterizedTypeReference<List<WorkQueueRow>>() {});
      return rows != null ? rows : List.of();
    } catch (final Exception e) {
      log.error("Failed to load the work queue", e);
      return null;
    }
  }
}
