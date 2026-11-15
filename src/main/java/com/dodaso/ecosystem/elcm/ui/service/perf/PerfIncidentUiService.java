package com.dodaso.ecosystem.elcm.ui.service.perf;

import com.dodaso.ecosystem.baseline.common.constant.ServiceDiscoveryEnum;
import com.dodaso.ecosystem.baseline.common.proxy.RESTServiceClient;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

/**
 * ADDED 2026-11-12. Reads perf incidents from common-service for the admin page. Every read gives
 * null when the call fails, never an exception, so the page can say so.
 */
@Service
@Slf4j
public class PerfIncidentUiService {

  private static final String SERVICE = ServiceDiscoveryEnum.common_service.getServiceDiscoveryName();
  private static final String BASE = "/api/v1/perf";

  @Autowired
  private RESTServiceClient restServiceClient;

  /** Filter fields; blank ones are left out of the call. */
  public PerfRows.IncidentPage list(final String service, final String reason, final String call,
      final LocalDateTime from, final LocalDateTime to, final Long minMs, final int page,
      final int size) {
    final StringBuilder url = new StringBuilder(BASE + "/incidents?page=" + page + "&size=" + size);
    add(url, "service", service);
    add(url, "reason", reason);
    add(url, "call", call);
    add(url, "from", from == null ? null : from.toString());
    add(url, "to", to == null ? null : to.toString());
    add(url, "minMs", minMs == null ? null : String.valueOf(minMs));
    try {
      return restServiceClient.get(SERVICE, url.toString(),
          new ParameterizedTypeReference<PerfRows.IncidentPage>() {
          });
    } catch (final Exception e) {
      log.error("Failed to load the performance incidents", e);
      return null;
    }
  }

  public PerfRows.Incident detail(final Long id) {
    try {
      return restServiceClient.get(SERVICE, BASE + "/incidents/" + id,
          new ParameterizedTypeReference<PerfRows.Incident>() {
          });
    } catch (final Exception e) {
      log.error("Failed to load performance incident {}", id, e);
      return null;
    }
  }

  public List<PerfRows.TopCall> top(final int hours, final int limit) {
    try {
      return restServiceClient.get(SERVICE, BASE + "/top?hours=" + hours + "&limit=" + limit,
          new ParameterizedTypeReference<List<PerfRows.TopCall>>() {
          });
    } catch (final Exception e) {
      log.error("Failed to load the top calls", e);
      return null;
    }
  }

  private static void add(final StringBuilder url, final String name, final String value) {
    if (value != null && !value.isBlank()) {
      url.append('&').append(name).append('=')
          .append(URLEncoder.encode(value.trim(), StandardCharsets.UTF_8));
    }
  }
}
