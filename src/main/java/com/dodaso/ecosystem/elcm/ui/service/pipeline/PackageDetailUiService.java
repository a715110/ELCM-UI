package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import com.dodaso.ecosystem.baseline.common.constant.ServiceDiscoveryEnum;
import com.dodaso.ecosystem.baseline.common.proxy.RESTServiceClient;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * ADDED 2026-10-25. elcm-ui side of the Package Details screen. Reads only, from elcm-service
 * (/api/v1/pipeline/package-detail). A failed read never throws: the answer says whether the
 * package was not found (404), the person may not see it (403) or the read failed, so the screen
 * can say so. Who is asking is decided by the service from the signed-in session.
 */
@Service
@Slf4j
public class PackageDetailUiService {

  private static final String SERVICE = ServiceDiscoveryEnum.elcm_service.getServiceDiscoveryName();
  private static final String BASE = "/api/v1/pipeline/package-detail/";
  private static final Duration TIMEOUT = Duration.ofSeconds(15);

  @Autowired private RESTServiceClient restServiceClient;

  /** How a read ended. */
  public enum Outcome {
    OK, NOT_FOUND, FORBIDDEN, FAILED
  }

  /** The package (only when OK) and how the read ended. */
  public record Result(PackageDetailRow detail, Outcome outcome) {
  }

  public Result detail(final long packageId) {
    try {
      final PackageDetailRow row = restServiceClient.callRESTService(SERVICE, BASE + packageId,
          null, new ParameterizedTypeReference<PackageDetailRow>() {
          }, HttpMethod.GET, null, TIMEOUT);
      return row == null ? new Result(null, Outcome.FAILED) : new Result(row, Outcome.OK);
    } catch (final Exception e) {
      final int status = statusOf(e);
      if (status == 404) {
        return new Result(null, Outcome.NOT_FOUND);
      }
      if (status == 403) {
        return new Result(null, Outcome.FORBIDDEN);
      }
      log.warn("Failed to load package {}: {}", packageId, e.toString());
      return new Result(null, Outcome.FAILED);
    }
  }

  /** One page of the activity log (pages start at 0); null when it could not be read. */
  public ActivityPageRow activity(final long packageId, final int page, final int size) {
    try {
      return restServiceClient.callRESTService(SERVICE,
          BASE + packageId + "/activity?page=" + page + "&size=" + size, null,
          new ParameterizedTypeReference<ActivityPageRow>() {
          }, HttpMethod.GET, null, TIMEOUT);
    } catch (final Exception e) {
      log.warn("Failed to load the activity of package {}: {}", packageId, e.toString());
      return null;
    }
  }

  private static int statusOf(final Throwable error) {
    Throwable cause = error;
    for (int depth = 0; cause != null && depth < 6; depth++) {
      if (cause instanceof HttpStatusCodeException status) {
        return status.getStatusCode().value();
      }
      cause = cause.getCause();
    }
    return 0;
  }
}
