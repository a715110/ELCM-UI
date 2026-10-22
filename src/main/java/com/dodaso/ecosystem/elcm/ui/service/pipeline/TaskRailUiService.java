package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import com.dodaso.ecosystem.baseline.common.constant.ServiceDiscoveryEnum;
import com.dodaso.ecosystem.baseline.common.proxy.RESTServiceClient;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

/**
 * elcm-ui side of the Task Rail (ADDED 2026-10-18). Reads only. A failed read gives null, never an
 * exception, so the panel shows a message instead of failing the page. Who is asking is decided by
 * the service from the signed-in session, not by anything sent from here.
 */
@Service
@Slf4j
public class TaskRailUiService {

  private static final String SERVICE = ServiceDiscoveryEnum.elcm_service.getServiceDiscoveryName();
  private static final String BASE = "/api/v1/pipeline/task-rail";

  @Autowired private RESTServiceClient restServiceClient;

  /** REVISED 2026-10-22: how long a read waits for the service, so a slow service shows the
   * message instead of holding the page (the badge is read while a page is drawn). */
  @Value("${elcm.task-rail.timeout-seconds:8}")
  private long timeoutSeconds;

  private <T> T read(final String endpoint, final ParameterizedTypeReference<T> type)
      throws Exception {
    return restServiceClient.callRESTService(SERVICE, endpoint, null, type, HttpMethod.GET, null,
        Duration.ofSeconds(Math.max(1L, timeoutSeconds)));
  }

  /** Open Review and Approval tasks; mine keeps only the viewer's own. */
  public TaskRailPageRow open(final boolean mine) {
    try {
      return read(BASE + "/open?mine=" + mine,
          new ParameterizedTypeReference<TaskRailPageRow>() {
          });
    } catch (final Exception e) {
      log.error("Failed to load the open tasks for the task rail", e);
      return null;
    }
  }

  /** One page of closed tasks (last 30 days); before is the cursor of the previous page or null. */
  public TaskRailPageRow closed(final boolean mine, final Long before) {
    try {
      final String query = "?mine=" + mine + (before == null ? "" : "&before=" + before);
      return read(BASE + "/closed" + query,
          new ParameterizedTypeReference<TaskRailPageRow>() {
          });
    } catch (final Exception e) {
      log.error("Failed to load the closed tasks for the task rail", e);
      return null;
    }
  }

  /** The badge number, or -1 when it could not be read (the caller keeps its last value). */
  public int assignedToMe() {
    try {
      final TaskRailCountRow row = read(BASE + "/count",
          new ParameterizedTypeReference<TaskRailCountRow>() {
          });
      return row == null ? -1 : row.getAssignedToMe();
    } catch (final Exception e) {
      log.warn("Failed to read the task rail badge count", e);
      return -1;
    }
  }
}
