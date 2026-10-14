package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import com.dodaso.ecosystem.baseline.common.constant.ServiceDiscoveryEnum;
import com.dodaso.ecosystem.baseline.common.proxy.RESTServiceClient;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

/**
 * elcm-ui side of the My Submissions screen (ADDED 2026-10-14). Reads only. A failed read gives
 * null, never an exception, so the screen shows a message instead of failing.
 */
@Service
@Slf4j
public class MySubmissionsUiService {

  private static final String SERVICE = ServiceDiscoveryEnum.elcm_service.getServiceDiscoveryName();
  private static final String BASE = "/api/v1/pipeline/my";

  @Autowired private RESTServiceClient restServiceClient;

  public List<MySubmissionRow> submissions() {
    try {
      final List<MySubmissionRow> rows =
          restServiceClient.get(
              SERVICE,
              BASE + "/submissions",
              new ParameterizedTypeReference<List<MySubmissionRow>>() {});
      return rows != null ? rows : List.of();
    } catch (final Exception e) {
      log.error("Failed to load my submissions", e);
      return null;
    }
  }

  public List<MyCommittedDocumentRow> committed() {
    try {
      final List<MyCommittedDocumentRow> rows =
          restServiceClient.get(
              SERVICE,
              BASE + "/committed-documents",
              new ParameterizedTypeReference<List<MyCommittedDocumentRow>>() {});
      return rows != null ? rows : List.of();
    } catch (final Exception e) {
      log.error("Failed to load my committed documents", e);
      return null;
    }
  }
}
