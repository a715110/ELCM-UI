package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import com.dodaso.ecosystem.baseline.common.constant.ServiceDiscoveryEnum;
import com.dodaso.ecosystem.baseline.common.proxy.RESTServiceClient;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

/**
 * ADDED 2026-10-25. elcm-ui side of the Contract Packages list. Reads only, from elcm-service
 * (/api/v1/pipeline/package-rail, the same read and the same Mine and All rule as the Package Rail
 * in ECWS). A failed read gives null, never an exception.
 */
@Service
@Slf4j
public class PackageListUiService {

  private static final String SERVICE = ServiceDiscoveryEnum.elcm_service.getServiceDiscoveryName();
  private static final String BASE = "/api/v1/pipeline/package-rail";
  private static final Duration TIMEOUT = Duration.ofSeconds(15);

  @Autowired private RESTServiceClient restServiceClient;

  public PackageListPageRow open(final boolean mine, final int offset) {
    return read("/open", mine, offset);
  }

  public PackageListPageRow closed(final boolean mine, final int offset) {
    return read("/closed", mine, offset);
  }

  private PackageListPageRow read(final String path, final boolean mine, final int offset) {
    try {
      return restServiceClient.callRESTService(SERVICE,
          BASE + path + "?mine=" + mine + "&offset=" + offset, null,
          new ParameterizedTypeReference<PackageListPageRow>() {
          }, HttpMethod.GET, null, TIMEOUT);
    } catch (final Exception e) {
      log.warn("Failed to load the package list {}: {}", path, e.toString());
      return null;
    }
  }
}
