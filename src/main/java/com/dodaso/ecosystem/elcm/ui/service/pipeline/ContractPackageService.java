package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import com.dodaso.ecosystem.baseline.common.constant.ServiceDiscoveryEnum;
import com.dodaso.ecosystem.baseline.common.proxy.RESTServiceClient;
import com.dodaso.ecosystem.elcm.ui.constant.ContractPackageControllerAPIEnum;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * Business logic + data access for contract packages (FC-1 Pipeline), elcm-ui side.
 *
 * REPLACED 2026-10-07: this used to return a static list of mock rows. It now calls
 * elcm-service's ContractPackageController. Reads degrade to an empty list when a call fails (a
 * failed call must not break the dashboard, same philosophy as StageDocumentService.findStaged()).
 * Writes never throw: each returns a PackageOutcome so the bean can show a message. elcm-service
 * makes the real decisions (one workspace per package, no document in two packages, draft
 * packages only); the UI checks only what it can cheaply.
 */
@Service
@Slf4j
public class ContractPackageService {

    @Autowired
    private RESTServiceClient restServiceClient;

    private static final String SERVICE = ServiceDiscoveryEnum.elcm_service.getServiceDiscoveryName();
    private static final String BASE = ContractPackageControllerAPIEnum.getContractPackages.getEndPoint();

    public List<ContractPackageRow> findPackages() {
        try {
            final List<ContractPackageRow> rows = restServiceClient.get(SERVICE, BASE,
                new ParameterizedTypeReference<List<ContractPackageRow>>() {
                });
            return rows != null ? rows : List.of();
        } catch (final Exception e) {
            log.error("Failed to load contract packages from elcm-service", e);
            return List.of();
        }
    }

    public List<PackageDocumentRow> findPackageDocuments(final Long packageId) {
        try {
            final List<PackageDocumentRow> rows = restServiceClient.get(SERVICE, BASE + "/" + packageId + "/documents",
                new ParameterizedTypeReference<List<PackageDocumentRow>>() {
                });
            return rows != null ? rows : List.of();
        } catch (final Exception e) {
            log.error("Failed to load the documents of package {} from elcm-service", packageId, e);
            return List.of();
        }
    }

    public List<DocumentRoleOptionRow> findDocumentRoles() {
        try {
            final List<DocumentRoleOptionRow> rows = restServiceClient.get(SERVICE,
                ContractPackageControllerAPIEnum.getDocumentRoles.getEndPoint(),
                new ParameterizedTypeReference<List<DocumentRoleOptionRow>>() {
                });
            return rows != null ? rows : List.of();
        } catch (final Exception e) {
            log.error("Failed to load document roles from elcm-service", e);
            return List.of();
        }
    }

    /**
     * Creates a package from the given documents. roleByDocument maps a staged document id to a
     * role code; a missing or blank role means "not defined yet". assigneeLoginId is optional.
     */
    public PackageOutcome createPackage(final List<Long> stagedDocumentIds, final Map<Long, String> roleByDocument,
                                        final String assigneeLoginId) {
        final Map<String, Object> body = new HashMap<>();
        body.put("documents", selections(stagedDocumentIds, roleByDocument));
        if (assigneeLoginId != null && !assigneeLoginId.isBlank()) {
            body.put("assigneeId", assigneeLoginId.trim());
        }
        return post(BASE, body, "create a package");
    }

    /** Adds the given documents to an existing draft package. */
    public PackageOutcome addDocuments(final Long packageId, final List<Long> stagedDocumentIds,
                                       final Map<Long, String> roleByDocument) {
        final Map<String, Object> body = new HashMap<>();
        body.put("documents", selections(stagedDocumentIds, roleByDocument));
        return post(BASE + "/" + packageId + "/documents", body, "add documents to package " + packageId);
    }

    /** Submit for extraction. 400 means not ready (no documents, no assignee, or a missing role). */
    public PackageOutcome submit(final Long packageId) {
        return post(BASE + "/" + packageId + "/submit", new HashMap<String, Object>(), "submit package " + packageId);
    }

    /** Unsubmit while the submission is still pending. */
    public PackageOutcome unsubmit(final Long packageId) {
        return post(BASE + "/" + packageId + "/unsubmit", new HashMap<String, Object>(), "unsubmit package " + packageId);
    }

    /** Changes the assignee of a draft package (IAMS login id). */
    public PackageOutcome reassign(final Long packageId, final String assigneeLoginId) {
        final Map<String, Object> body = new HashMap<>();
        body.put("assigneeId", assigneeLoginId);
        return post(BASE + "/" + packageId + "/assignee", body, "reassign package " + packageId);
    }

    /** Takes one document out of a draft package; it returns to the Stage Documents list. */
    public PackageOutcome removeDocument(final Long packageId, final Long stagedDocumentId) {
        try {
            restServiceClient.delete(SERVICE, BASE + "/" + packageId + "/documents/" + stagedDocumentId,
                new ParameterizedTypeReference<Void>() {
                });
            return PackageOutcome.OK;
        } catch (final HttpStatusCodeException e) {
            return outcomeOf(e, "remove a document from package " + packageId);
        } catch (final Exception e) {
            log.error("Failed to remove document {} from package {}", stagedDocumentId, packageId, e);
            return PackageOutcome.FAILED;
        }
    }

    private PackageOutcome post(final String endpoint, final Object body, final String what) {
        try {
            restServiceClient.post(SERVICE, endpoint, body, new ParameterizedTypeReference<Void>() {
            });
            return PackageOutcome.OK;
        } catch (final HttpStatusCodeException e) {
            return outcomeOf(e, what);
        } catch (final Exception e) {
            log.error("Failed to {}", what, e);
            return PackageOutcome.FAILED;
        }
    }

    private PackageOutcome outcomeOf(final HttpStatusCodeException e, final String what) {
        final int status = e.getStatusCode().value();
        log.warn("elcm-service refused to {}: HTTP {}", what, status);
        return switch (status) {
            case 404 -> PackageOutcome.NOT_FOUND;
            case 409 -> PackageOutcome.CONFLICT;
            case 422 -> PackageOutcome.WORKSPACE_MISMATCH;
            case 400 -> PackageOutcome.INVALID;
            default -> PackageOutcome.FAILED;
        };
    }

    private static List<Map<String, Object>> selections(final List<Long> ids, final Map<Long, String> roleByDocument) {
        final List<Map<String, Object>> list = new ArrayList<>();
        for (final Long id : ids) {
            final Map<String, Object> one = new HashMap<>();
            one.put("stagedDocumentId", id);
            final String role = roleByDocument != null ? roleByDocument.get(id) : null;
            if (role != null && !role.isBlank()) {
                one.put("roleCode", role);
            }
            list.add(one);
        }
        return list;
    }
}
