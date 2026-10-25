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
 * ADDED 2026-10-25 -- elcm-ui side of the Verify address button (New Record panel of the Upload
 * Files dialog). The service does the check; nothing is saved. A failed call gives an UNAVAILABLE
 * answer, never an exception, so the dialog shows a message and the user can carry on.
 */
@Service
@Slf4j
public class AddressVerifyUiService {

  private static final String SERVICE = ServiceDiscoveryEnum.elcm_service.getServiceDiscoveryName();
  private static final String ENDPOINT = "/api/v1/pipeline/address/verify";

  @Autowired private RESTServiceClient restServiceClient;

  /** How long the call waits; a little longer than the service's own wait for the provider. */
  @Value("${elcm.address-verification.timeout-seconds:12}")
  private long timeoutSeconds;

  public AddressVerifyRow verify(final AddressVerifyRequestRow request) {
    try {
      final AddressVerifyRow row = restServiceClient.callRESTService(SERVICE, ENDPOINT, request,
          new ParameterizedTypeReference<AddressVerifyRow>() {
          }, HttpMethod.POST, null, Duration.ofSeconds(Math.max(1L, timeoutSeconds)));
      if (row != null) {
        return row;
      }
    } catch (final Exception e) {
      log.warn("The address check call failed: {}", e.toString());
    }
    return AddressVerifyRow.of("UNAVAILABLE",
        "The address check is not available right now. You can continue without checking.");
  }
}
