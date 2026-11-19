package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import com.dodaso.ecosystem.baseline.common.constant.ServiceDiscoveryEnum;
import com.dodaso.ecosystem.baseline.common.proxy.RESTServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

/**
 * ADDED 2026-11-16 -- elcm-ui side of the Critical Dates screen. Reads only. A failed read gives
 * null, never an exception, so the screen shows a message instead of failing.
 */
@Service
@Slf4j
public class CriticalDateUiService {

  private static final String SERVICE =
      ServiceDiscoveryEnum.elcm_service.getServiceDiscoveryName();
  private static final String PATH = "/api/v1/pipeline/critical-dates";

  @Autowired
  private RESTServiceClient restServiceClient;

  public CriticalDateView overview() {
    try {
      return restServiceClient.get(SERVICE, PATH,
          new ParameterizedTypeReference<CriticalDateView>() {
          });
    } catch (final Exception e) {
      log.error("Failed to load the critical dates", e);
      return null;
    }
  }
}
