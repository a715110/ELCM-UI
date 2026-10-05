package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import com.dodaso.ecosystem.auth.constant.UserControllerAPIEnum;
import com.dodaso.ecosystem.auth.container.UserDirectoryDTOContainer;
import com.dodaso.ecosystem.baseline.common.constant.ServiceDiscoveryEnum;
import com.dodaso.ecosystem.baseline.common.container.RESTReqContainer;
import com.dodaso.ecosystem.baseline.common.proxy.RESTServiceClient;
import com.dodaso.ecosystem.common.dto.FileUploadDTO;
import com.dodaso.ecosystem.elcm.container.StagedDocumentDTOContainer;
import com.dodaso.ecosystem.elcm.dto.ContractRecordDTO;
import com.dodaso.ecosystem.elcm.dto.LkpContractTypeDTO;
import com.dodaso.ecosystem.elcm.dto.LkpRoutingIntentDTO;
import com.dodaso.ecosystem.elcm.dto.StagedDocumentDTO;
import com.dodaso.ecosystem.elcm.dto.WorkspaceDTO;
import com.dodaso.ecosystem.elcm.ui.constant.ContractRecordControllerAPIEnum;
import com.dodaso.ecosystem.elcm.ui.constant.DestinationChoiceEnum;
import lombok.extern.slf4j.Slf4j;

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
 *
 * REVISED 2026-10-04: findAssignableUsers() now calls IAMS's directory
 * endpoint (ADEV-IAMS-SERVICE UserController.getUserDirectories(), added
 * for Option 1 -- see the IAMS RBAC/Directory ERD) instead of returning a
 * hardcoded placeholder list.
 *
 * REVISED 2026-10-04 (later same day): findAssignableUsers() now returns
 * AssignableUserOptionRow (loginId + displayName + teamName), not a bare
 * List<String> of display names -- backs the "Assign To" field's new
 * autocomplete, which shows loginId alongside displayName so the user can
 * tell identically-named people apart before picking.
 *
 * REVISED 2026-10-04 (assigneeId -> loginId): the value the autocomplete
 * submits, and submitToPipeline() sends as StagedDocumentDTO.assigneeId, is
 * now the picked person's loginId, not their display name -- so identically-
 * named people are distinct assignments, not merely distinct picks. See
 * AssignableUserOptionRow and elcm-service's AssigneeDirectoryLookupService
 * (which resolves it back to a display name on read, with a fallback for
 * rows written before this change).
 */
@Service
@Slf4j
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

  /**
   * Populates the "Assign To" field's autocomplete from IAMS's directory,
   * replacing the former hardcoded ("L. Nguyen", "M. Okonkwo") placeholder.
   *
   * Called once per dialog reset (UploadFilesBean.resetState()), not per
   * keystroke -- with IAMS's directory currently at ~14 synthetic entries,
   * loading the full roster once and filtering in memory
   * (UploadFilesBean.completeAssignableUsers()) is simpler and cheaper
   * than a REST round trip on every character typed. Revisit if the
   * directory ever grows large enough for that to matter -- same caveat
   * AssigneeDirectoryLookupService's Javadoc already flags on the
   * elcm-service side.
   *
   * Returns AssignableUserOptionRow (loginId + displayName + teamName),
   * not a bare display-name string -- see that class's own Javadoc for
   * what this does and does not fix about telling identically-named
   * people apart.
   *
   * Fails open to an empty list (not an exception) on any IAMS error, so
   * a directory outage degrades the field to "nothing to pick" rather
   * than breaking the whole Upload Files dialog.
   */
  public List<AssignableUserOptionRow> findAssignableUsers() {
    try {
      UserDirectoryDTOContainer container = restServiceClient.get(
          ServiceDiscoveryEnum.iams_service.getServiceDiscoveryName(),
          UserControllerAPIEnum.userControllerAPIEnum_getUserDirectories.getEndPoint(),
          new ParameterizedTypeReference<UserDirectoryDTOContainer>() {
          });

      if (container == null || container.getUserDirectoryDTOList() == null) {
        return List.of();
      }

      // loginId is what actually gets stored as the assignment now (see
      // AssignableUserOptionRow), so an entry without one can't be assigned
      // to -- drop it rather than offer an option that would submit null.
      return container.getUserDirectoryDTOList().stream()
          .filter(dto -> dto.getLoginId() != null && !dto.getLoginId().isBlank())
          .filter(dto -> dto.getDisplayName() != null && !dto.getDisplayName().isBlank())
          .map(dto -> new AssignableUserOptionRow(dto.getLoginId(), dto.getDisplayName(), dto.getTeamName()))
          .collect(Collectors.toList());
    } catch (Exception e) {
      log.error("Failed to load assignable users from IAMS directory", e);
      return List.of();
    }
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
   * @param assignToUser               the assignee's IAMS loginId (what the
   *                                    Assign To autocomplete submits), or
   *                                    null/blank for Unassigned.
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

  /**
   * ADDED 2026-10-02 -- backs the Existing Record autocomplete's new
   * "show me what I just picked" detail panel. Reuses the exact same
   * elcm-service endpoint (GET /api/v1/pipeline/contract-record/{id}) the
   * Stage Documents dashboard's file-preview feature already calls via
   * DocumentViewerService.getRecordDetail() -- same ContractRecordDTO
   * shape (counterparty/contractType/status/workspace/property/address),
   * same "call once the id is known" contract. Kept as its own method
   * here (rather than having UploadFilesBean reach into
   * DocumentViewerService) so this service's callers don't take on a
   * dependency belonging to a different feature.
   */
  public ContractRecordDTO getRecordDetail(final Long recordId) throws Exception {
    return restServiceClient.get(
        ServiceDiscoveryEnum.elcm_service.getServiceDiscoveryName(),
        ContractRecordControllerAPIEnum.contractRecordBase.getEndPoint() + "/" + recordId,
        new ParameterizedTypeReference<ContractRecordDTO>() {
        });
  }
}