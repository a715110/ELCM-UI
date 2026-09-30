package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import com.dodaso.ecosystem.baseline.common.constant.ServiceDiscoveryEnum;
import com.dodaso.ecosystem.baseline.common.proxy.RESTServiceClient;
import com.dodaso.ecosystem.elcm.ui.constant.StageDocumentControllerAPIEnum;

import lombok.extern.slf4j.Slf4j;

/**
 * Business logic + data access for staged documents (FC-1 Pipeline),
 * elcm-ui side.
 *
 * REPLACED 2026-09-28: this used to return a static List.of(...) of three
 * mock StagedDocumentRow entries -- there was no persistence layer wired up
 * on the elcm-ui side yet. elcm-service's own
 * com.dodaso.ecosystem.elcm.service.pipeline.StageDocumentService already
 * reads real elcm.staged_document rows and resolves file metadata from
 * common-service (see that class's Javadoc for the two-phase
 * transactional-read / outside-transaction-batch-lookup design); this class
 * just needs to call it over the wire, the same way PipelineMetricsBean
 * calls elcm-service's own metrics endpoint and UploadFilesBean calls
 * common-service's upload endpoint -- restServiceClient.get(serviceName,
 * endpoint, ParameterizedTypeReference), driven by ServiceDiscoveryEnum +
 * an XxxControllerAPIEnum for the endpoint constant.
 *
 * No new Eureka-registration blocker here (unlike UploadFilesBean's
 * common_service issue): ServiceDiscoveryEnum.elcm_service already exists
 * and is already used by PipelineMetricsBean, and this call targets
 * elcm-service itself.
 *
 * StageDocumentsBean.init() calls findStaged(null) directly with no
 * try/catch of its own, so a REST failure is swallowed here (logged, empty
 * list returned) rather than left to surface as an uncaught exception on
 * page load -- same "a lookup hiccup shouldn't break the page" philosophy
 * UploadFilesBean.resetState() already uses for its own lookup calls.
 */
@Service
@Slf4j
public class StageDocumentService {

    @Autowired
    private RESTServiceClient restServiceClient;

    /**
     * workspace filtering isn't implemented server-side yet (see
     * elcm-service's StageDocumentService.findStaged()'s own note) -- when
     * non-null it's appended as a query parameter so this call's contract
     * doesn't need to change once server-side filtering is real.
     */
    public List<StagedDocumentRow> findStaged(final String workspace) {
        final String endpoint = workspace != null && !workspace.isBlank()
            ? StageDocumentControllerAPIEnum.getStagedDocuments.getEndPoint() + "?workspace=" + workspace
            : StageDocumentControllerAPIEnum.getStagedDocuments.getEndPoint();

        try {
            final List<StagedDocumentRow> rows = restServiceClient.get(
                ServiceDiscoveryEnum.elcm_service.getServiceDiscoveryName(),
                endpoint,
                new ParameterizedTypeReference<List<StagedDocumentRow>>() {
                });
            return rows != null ? rows : List.of();
        } catch (final Exception e) {
            // A failed call here (elcm-service down, network blip, etc.)
            // shouldn't break the whole dashboard -- degrade to an empty
            // table rather than an uncaught exception on page load, but log
            // it so the failure is visible in production logs.
            log.error("Failed to load staged documents from elcm-service", e);
            return List.of();
        }
    }
}