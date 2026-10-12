package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import com.dodaso.ecosystem.baseline.common.constant.ServiceDiscoveryEnum;
import com.dodaso.ecosystem.baseline.common.proxy.RESTServiceClient;
import com.dodaso.ecosystem.elcm.ui.constant.ContractPackageControllerAPIEnum;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * elcm-ui side of extraction: progress and results of a submitted package, and Retry. The
 * extraction itself runs in the background in elcm-service; these calls are quick reads, so the
 * dashboard polls them. A failed read returns null (the dialog says it could not load) rather
 * than throwing.
 */
@Service
@Slf4j
public class ExtractionUiService {

    @Autowired
    private RESTServiceClient restServiceClient;

    private static final String SERVICE = ServiceDiscoveryEnum.elcm_service.getServiceDiscoveryName();
    private static final String BASE = ContractPackageControllerAPIEnum.getContractPackages.getEndPoint();

    public ExtractionStatusRow status(final Long packageId) {
        try {
            return restServiceClient.get(SERVICE, BASE + "/" + packageId + "/extraction",
                new ParameterizedTypeReference<ExtractionStatusRow>() {
                });
        } catch (final Exception e) {
            log.error("Failed to load the extraction status of package {}", packageId, e);
            return null;
        }
    }

    public ExtractionResultsRow results(final Long packageId) {
        try {
            return restServiceClient.get(SERVICE, BASE + "/" + packageId + "/extraction/results",
                new ParameterizedTypeReference<ExtractionResultsRow>() {
                });
        } catch (final Exception e) {
            log.error("Failed to load the extraction results of package {}", packageId, e);
            return null;
        }
    }

    /** Failed extraction back in the queue. CONFLICT means it is not failed (anymore). */
    public PackageOutcome retry(final Long packageId) {
        try {
            restServiceClient.post(SERVICE, BASE + "/" + packageId + "/extraction/retry", new HashMap<String, Object>(),
                new ParameterizedTypeReference<Void>() {
                });
            return PackageOutcome.OK;
        } catch (final HttpStatusCodeException e) {
            log.warn("elcm-service refused to retry extraction of package {}: HTTP {}", packageId,
                e.getStatusCode().value());
            return switch (e.getStatusCode().value()) {
                case 404 -> PackageOutcome.NOT_FOUND;
                case 409 -> PackageOutcome.CONFLICT;
                default -> PackageOutcome.FAILED;
            };
        } catch (final Exception e) {
            log.error("Failed to retry extraction of package {}", packageId, e);
            return PackageOutcome.FAILED;
        }
    }
}
