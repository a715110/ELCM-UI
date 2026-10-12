package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import com.dodaso.ecosystem.baseline.common.constant.ServiceDiscoveryEnum;
import com.dodaso.ecosystem.baseline.common.proxy.RESTServiceClient;
import com.dodaso.ecosystem.elcm.ui.constant.ContractPackageControllerAPIEnum;
import java.util.HashMap;
import java.util.Map;
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
    /** confirmVerified answers this when the caller lacks the review permission. */
    public static final int CONFIRM_FORBIDDEN = -2;

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

    /** The history of one field; null when it could not be loaded. */
    public FieldHistoryRow history(final Long packageId, final Long resultId) {
        try {
            return restServiceClient.get(SERVICE, BASE + "/" + packageId + "/extraction/fields/" + resultId + "/history",
                new ParameterizedTypeReference<FieldHistoryRow>() {
                });
        } catch (final Exception e) {
            log.error("Failed to load the history of field {} of package {}", resultId, packageId, e);
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

    /**
     * Reviewer: save one field (corrected value and confirmed flag). INVALID is a value that does
     * not fit the field type (400), CONFLICT means the package can no longer be reviewed (409).
     */
    public PackageOutcome saveField(final Long packageId, final ExtractionResultsRow.FieldRow field) {
        final Map<String, Object> body = new HashMap<>();
        body.put("correctedValue", field.getCorrectedValue());
        body.put("verified", field.isVerified());
        return post(BASE + "/" + packageId + "/extraction/fields/" + field.getResultId(), body,
            "save field " + field.getResultId() + " of package " + packageId);
    }

    /** Reviewer: confirm every field the extractor verified. Returns how many, -1 when the call failed, CONFIRM_FORBIDDEN without permission. */
    public int confirmVerified(final Long packageId) {
        try {
            final Map<String, Integer> answer = restServiceClient.post(SERVICE,
                BASE + "/" + packageId + "/extraction/confirm-verified", new HashMap<String, Object>(),
                new ParameterizedTypeReference<Map<String, Integer>>() {
                });
            return answer != null && answer.get("confirmed") != null ? answer.get("confirmed") : 0;
        } catch (final HttpStatusCodeException e) {
            log.warn("elcm-service refused to confirm the verified fields of package {}: HTTP {}", packageId,
                e.getStatusCode().value());
            return e.getStatusCode().value() == 403 ? CONFIRM_FORBIDDEN : -1;
        } catch (final Exception e) {
            log.error("Failed to confirm the verified fields of package {}", packageId, e);
            return -1;
        }
    }

    /** Reviewer: complete the review. WORKSPACE_MISMATCH here means 422: fields still to confirm. */
    public PackageOutcome completeReview(final Long packageId) {
        return post(BASE + "/" + packageId + "/extraction/complete", new HashMap<String, Object>(),
            "complete the review of package " + packageId);
    }

    /**
     * Reviewer: send the package back to Assembly with a reason. INVALID is a missing or too long
     * reason (400), CONFLICT means the package is not in a state that can be declined (409).
     */
    public PackageOutcome decline(final Long packageId, final String reason) {
        final Map<String, Object> body = new HashMap<>();
        body.put("reason", reason);
        return post(BASE + "/" + packageId + "/extraction/decline", body,
            "decline package " + packageId);
    }

    private PackageOutcome post(final String endpoint, final Object body, final String what) {
        try {
            restServiceClient.post(SERVICE, endpoint, body, new ParameterizedTypeReference<Void>() {
            });
            return PackageOutcome.OK;
        } catch (final HttpStatusCodeException e) {
            log.warn("elcm-service refused to {}: HTTP {}", what, e.getStatusCode().value());
            return switch (e.getStatusCode().value()) {
                case 404 -> PackageOutcome.NOT_FOUND;
                case 409 -> PackageOutcome.CONFLICT;
                case 422 -> PackageOutcome.WORKSPACE_MISMATCH;
                case 400 -> PackageOutcome.INVALID;
                case 403 -> PackageOutcome.FORBIDDEN;
                default -> PackageOutcome.FAILED;
            };
        } catch (final Exception e) {
            log.error("Failed to {}", what, e);
            return PackageOutcome.FAILED;
        }
    }
}
