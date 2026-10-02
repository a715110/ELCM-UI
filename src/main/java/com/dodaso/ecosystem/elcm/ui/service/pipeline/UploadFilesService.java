package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
import com.dodaso.ecosystem.elcm.dto.LkpContractTypeDTO;
import com.dodaso.ecosystem.elcm.dto.LkpRoutingIntentDTO;
import com.dodaso.ecosystem.elcm.dto.StagedDocumentDTO;
import com.dodaso.ecosystem.elcm.dto.WorkspaceDTO;
import com.dodaso.ecosystem.elcm.ui.constant.ContractRecordControllerAPIEnum;
import com.dodaso.ecosystem.elcm.ui.constant.DestinationChoiceEnum;

/**
 * Business logic + data access for the Upload Files dialog (FC-1 Pipeline).
 *
 * findAssignedWorkspace()/findAllWorkspaces()/findAssignableUsers() back the
 * dialog's dropdowns -- findAssignedWorkspace() is still a TODO placeholder
 * (see its own Javadoc), findAllWorkspaces() is real (calls elcm-service).
 *
 * submitToPipeline() is the "Add to Pipeline" action -- creates one
 * elcm.staged_document row per already-uploaded file, via elcm-service's
 * StageDocumentController.createStagedDocuments().
 *
 * REVISED 2026-09-29: now also carries the New Record / Existing Record
 * destination sub-panel's fields (newRecordName/newRecordCounterparty/
 * newRecordPropertyAddress/newRecordContractTypeCode/existingRecordQuery)
 * through to StagedDocumentDTO -- see that DTO's Javadoc for why these are
 * plain fields rather than a real ContractRecord/Property/Address
 * reference at this point. contractTypeDTO is only ever populated for
 * NEW_RECORD; StageDocumentService.createStagedDocuments() on the
 * elcm-service side falls back to its own default contract type when it's
 * null (Existing Record / Not Sure).
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
   * @param destinationChoice          the dialog's New/Existing/Unsure
   *                                    selection; mapped to
   *                                    lkp_routing_intent.code by name().
   * @param workspaceCode              the dialog's (possibly overridden)
   *                                    workspace.
   * @param comments                   free-text instructions, may be null.
   * @param assignToUser               may be null/"Unassigned".
   * @param loginId                    current user, stored as
   *                                    staged_document.uploaded_by (no
   *                                    security context on elcm-service's
   *                                    side yet to derive this
   *                                    independently).
   * @param persistedFiles             common-service's response from the
   *                                    earlier upload call -- one
   *                                    FileUploadDTO per file, each with a
   *                                    real, persisted id.
   * @param newRecordName              New Record's optional Record Name
   *                                    field -- null unless
   *                                    destinationChoice is NEW_RECORD.
   * @param newRecordCounterparty      New Record's required Counterparty
   *                                    field -- null unless
   *                                    destinationChoice is NEW_RECORD
   *                                    (UploadFilesBean validates it's
   *                                    non-blank in that case before this
   *                                    is ever called).
   * @param newRecordPropertyAddress   New Record's required Property
   *                                    Address field -- same null/
   *                                    validation rule as counterparty.
   * @param newRecordContractTypeCode  New Record's Contract Type
   *                                    dropdown selection (a
   *                                    lkp_contract_type.code, e.g.
   *                                    "PROPERTY_LEASE") -- null unless
   *                                    destinationChoice is NEW_RECORD.
   * @param newRecordAddressLine2      New Record's optional Address Line 2
   *                                    field -- null unless
   *                                    destinationChoice is NEW_RECORD.
   * @param newRecordCity              New Record's required City field --
   *                                    null unless destinationChoice is
   *                                    NEW_RECORD.
   * @param newRecordState             New Record's optional State field --
   *                                    null unless destinationChoice is
   *                                    NEW_RECORD.
   * @param newRecordZip               New Record's optional Zip field --
   *                                    null unless destinationChoice is
   *                                    NEW_RECORD.
   * @param existingRecordQuery        Existing Record's free-text search
   *                                    field, kept only as an audit trail
   *                                    (see StagedDocument's Javadoc) --
   *                                    null unless destinationChoice is
   *                                    EXISTING_RECORD.
   * @param existingRecordId           Existing Record's actual selection --
   *                                    the id the user picked from the
   *                                    autocomplete (see
   *                                    ContractRecordOptionRow), required
   *                                    server-side when destinationChoice
   *                                    is EXISTING_RECORD -- null
   *                                    otherwise.
   */
  public void submitToPipeline(final DestinationChoiceEnum destinationChoice,
      final String workspaceCode,
      final String comments,
      final String assignToUser,
      final String loginId,
      final List<FileUploadDTO> persistedFiles,
      final String newRecordName,
      final String newRecordCounterparty,
      final String newRecordPropertyAddress,
      final String newRecordContractTypeCode,
      final String newRecordAddressLine2,
      final String newRecordCity,
      final String newRecordState,
      final String newRecordZip,
      final String existingRecordQuery,
      final Long existingRecordId) throws Exception {

    if (persistedFiles == null || persistedFiles.isEmpty()) {
      return;
    }

    final WorkspaceDTO workspaceDTO = new WorkspaceDTO();
    workspaceDTO.setCode(workspaceCode);

    final LkpRoutingIntentDTO routingIntentDTO = new LkpRoutingIntentDTO();
    routingIntentDTO.setCode(destinationChoice.name());

    // Only meaningful for New Record -- left null for Existing
    // Record/Not Sure so elcm-service falls back to its own default
    // contract type (see StageDocumentService.createStagedDocuments()).
    final LkpContractTypeDTO contractTypeDTO;
    if (destinationChoice == DestinationChoiceEnum.NEW_RECORD && newRecordContractTypeCode != null) {
      contractTypeDTO = new LkpContractTypeDTO();
      contractTypeDTO.setCode(newRecordContractTypeCode);
    } else {
      contractTypeDTO = null;
    }

    final List<StagedDocumentDTO> stagedDocumentDTOs = persistedFiles.stream()
        .map(fileUploadDTO -> {
          final StagedDocumentDTO dto = new StagedDocumentDTO();
          dto.setFileUploadId(fileUploadDTO.getId());
          dto.setWorkspaceDTO(workspaceDTO);
          dto.setRoutingIntentDTO(routingIntentDTO);
          dto.setContractTypeDTO(contractTypeDTO);
          dto.setComments(comments);
          dto.setAssigneeId(assignToUser);
          dto.setUploadedBy(loginId);
          dto.setNewRecordName(newRecordName);
          dto.setNewRecordCounterparty(newRecordCounterparty);
          dto.setNewRecordPropertyAddress(newRecordPropertyAddress);
          dto.setNewRecordAddressLine2(newRecordAddressLine2);
          dto.setNewRecordCity(newRecordCity);
          dto.setNewRecordState(newRecordState);
          dto.setNewRecordZip(newRecordZip);
          dto.setExistingRecordQuery(existingRecordQuery);
          dto.setExistingRecordId(existingRecordId);
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

  /**
   * ADDED 2026-10-01 -- backs the dialog's Existing Record p:autoComplete
   * (UploadFilesBean.completeExistingRecords()). Same GET pattern as
   * findAllWorkspaces(), hitting elcm-service's
   * ContractRecordController.search() -- see RecordProvisioningService.
   * search()'s Javadoc on the elcm-service side for matching rules/
   * limitations (record_code only, top 20, case-insensitive contains).
   */
  public List<ContractRecordOptionRow> searchExistingRecords(final String query) throws Exception {
    if (query == null || query.isBlank()) {
      return List.of();
    }
    return restServiceClient.get(
        ServiceDiscoveryEnum.elcm_service.getServiceDiscoveryName(),
        ContractRecordControllerAPIEnum.searchContractRecords.getEndPoint()
            + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8),
        new ParameterizedTypeReference<List<ContractRecordOptionRow>>() {
        });
  }
}