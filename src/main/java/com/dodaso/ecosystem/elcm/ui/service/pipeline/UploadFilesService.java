package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import com.dodaso.ecosystem.baseline.common.constant.ServiceDiscoveryEnum;
import com.dodaso.ecosystem.baseline.common.container.RESTReqContainer;
import com.dodaso.ecosystem.baseline.common.proxy.RESTServiceClient;
import com.dodaso.ecosystem.common.dto.FileUploadDTO;
import com.dodaso.ecosystem.elcm.container.StagedDocumentDTOContainer;
import com.dodaso.ecosystem.elcm.dto.LkpRoutingIntentDTO;
import com.dodaso.ecosystem.elcm.dto.StagedDocumentDTO;
import com.dodaso.ecosystem.elcm.dto.WorkspaceDTO;
import com.dodaso.ecosystem.elcm.ui.constant.DestinationChoiceEnum;

/**
 * Business logic + data access for the Upload Files dialog (FC-1 Pipeline).
 * See StageDocumentService's class-level note -- same situation, same
 * reason: ALL data here is hardcoded placeholder data, no persistence
 * layer wired up yet.
 *
 * findAssignedWorkspace() and findAssignableUsers() back the two dropdowns
 * in the dialog. submitToPipeline(...) is the eventual "Add to Pipeline"
 * action -- deliberately not touching StageDocumentService yet (see
 * UploadFilesBean's class Javadoc for why: this doesn't take real files
 * as input yet, so there's nothing real to hand off to that service).
 */
@Service
public class UploadFilesService {

    private final RESTServiceClient restServiceClient;

    public UploadFilesService(RESTServiceClient restServiceClient) {
        this.restServiceClient = restServiceClient;
    }

    public WorkspaceOptionRow findAssignedWorkspace(final String loginId) {
        // TODO: this should come from the user's onboarding-assigned
        // workspace via IAMS/UserHelper, not be hardcoded to Retail for
        // everyone -- placeholder until that lookup exists.
        return new WorkspaceOptionRow("RETAIL", "Retail", "Retail Portfolio");
    }

    public List<String> findAssignableUsers() {
        // TODO: real preparer/reviewer list, likely from IAMS. Placeholder
        // names only, matching the ones already used elsewhere in the
        // Stage Documents mock data (StageDocumentService).
        return List.of("L. Nguyen", "M. Okonkwo");
    }

    /**
     * "Add to Pipeline" action -- creates one elcm.staged_document row per
     * already-uploaded file. No-ops (returns immediately) if persistedFiles
     * is empty, matching UploadFilesBean's own "only call this if there
     * were files" guard, so this is always safe to call.
     *
     * @param destinationChoice the dialog's New/Existing/Unsure selection;
     *                          mapped to lkp_routing_intent.code by name().
     * @param workspaceCode     the dialog's (possibly overridden) workspace.
     * @param comments          free-text instructions, may be null.
     * @param assignToUser      may be null/"Unassigned".
     * @param loginId           current user, stored as staged_document.uploaded_by
     *                          (no security context on elcm-service's side yet
     *                          to derive this independently).
     * @param persistedFiles    common-service's response from the earlier
     *                          upload call -- one FileUploadDTO per file,
     *                          each with a real, persisted id.
     */
    public void submitToPipeline(final DestinationChoiceEnum destinationChoice,
            final String workspaceCode,
            final String comments,
            final String assignToUser,
            final String loginId,
            final List<FileUploadDTO> persistedFiles) throws Exception {

        if (persistedFiles == null || persistedFiles.isEmpty()) {
            return;
        }

        final WorkspaceDTO workspaceDTO = new WorkspaceDTO();
        workspaceDTO.setCode(workspaceCode);

        final LkpRoutingIntentDTO routingIntentDTO = new LkpRoutingIntentDTO();
        routingIntentDTO.setCode(destinationChoice.name());

        final List<StagedDocumentDTO> stagedDocumentDTOs = persistedFiles.stream()
            .map(fileUploadDTO -> {
                final StagedDocumentDTO dto = new StagedDocumentDTO();
                dto.setFileUploadId(fileUploadDTO.getId());
                dto.setWorkspaceDTO(workspaceDTO);
                dto.setRoutingIntentDTO(routingIntentDTO);
                dto.setComments(comments);
                dto.setAssigneeId(assignToUser);
                dto.setUploadedBy(loginId);
                return dto;
            })
            .collect(Collectors.toList());

        final StagedDocumentDTOContainer requestContainer = new StagedDocumentDTOContainer();
        requestContainer.setStagedDocumentDTOList(stagedDocumentDTOs);

        // Same restServiceClient/RESTReqContainer(serviceDiscoveryName,
        // endpoint, requestBody, responseType, httpMethod) shape used by
        // uploadToCommonService() and every other cross-service call.
        final RESTReqContainer<StagedDocumentDTOContainer> restReqContainer = new RESTReqContainer<>(
            ServiceDiscoveryEnum.elcm_service.getServiceDiscoveryName(),
            "/api/v1/pipeline/staged-documents",
            requestContainer,
            new ParameterizedTypeReference<>() {
            },
            HttpMethod.POST);

        final StagedDocumentDTOContainer response = restServiceClient.callRESTService(restReqContainer);
        if (response == null) {
            // Same reasoning as uploadToCommonService()'s null-response
            // check: don't let this look like "zero rows created" when it
            // actually means the call itself failed silently.
            throw new IllegalStateException("elcm-service returned no response when creating staged documents.");
        }
    }

    public List<WorkspaceOptionRow> findAllWorkspaces() throws Exception {
        List<WorkspaceDTO> workspaceDTOs = restServiceClient.get(
                ServiceDiscoveryEnum.elcm_service.getServiceDiscoveryName(),
                "/api/v1/pipeline/workspace/allWorkspaces",
                new ParameterizedTypeReference<List<WorkspaceDTO>>() {
                });

        List<WorkspaceOptionRow> workspaceOptionRows = workspaceDTOs.stream()
                .map(WorkspaceOptionRow::fromDto)
                .collect(Collectors.toList());

        return workspaceOptionRows;
    }
}
