package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import com.dodaso.ecosystem.baseline.common.constant.ServiceDiscoveryEnum;
import com.dodaso.ecosystem.baseline.common.proxy.RESTServiceClient;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

/**
 * elcm-ui side of the Contract Records screen (ADDED 2026-10-12): the records with their number of
 * applied terms, and the terms of one record. Reads only. A failed read gives an empty list or
 * null, never an exception, so the screen shows a message instead of failing.
 */
@Service
@Slf4j
public class ContractRecordUiService {

    private static final String SERVICE = ServiceDiscoveryEnum.elcm_service.getServiceDiscoveryName();
    private static final String BASE = "/api/v1/pipeline/contract-records";

    @Autowired
    private RESTServiceClient restServiceClient;

    public List<ContractRecordRow> list(final String query) {
        try {
            final String q = query == null ? "" : query.trim();
            final List<ContractRecordRow> rows = restServiceClient.get(SERVICE,
                BASE + "?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8),
                new ParameterizedTypeReference<List<ContractRecordRow>>() {
                });
            return rows != null ? rows : List.of();
        } catch (final Exception e) {
            log.error("Failed to load the contract records", e);
            return null;
        }
    }

    public ContractRecordTermsRow terms(final Long recordId, final boolean history) {
        try {
            return restServiceClient.get(SERVICE, BASE + "/" + recordId + "/terms?history=" + history,
                new ParameterizedTypeReference<ContractRecordTermsRow>() {
                });
        } catch (final Exception e) {
            log.error("Failed to load the terms of contract record {}", recordId, e);
            return null;
        }
    }
}
