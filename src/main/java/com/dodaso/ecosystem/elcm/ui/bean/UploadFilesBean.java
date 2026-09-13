package com.dodaso.ecosystem.elcm.ui.bean;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;

import com.dodaso.ecosystem.baseline.common.constant.ServiceDiscoveryEnum;
import com.dodaso.ecosystem.baseline.common.container.RESTReqContainer;
import com.dodaso.ecosystem.common.container.FileUploadDTOContainer;
import com.dodaso.ecosystem.common.dto.FileItemDTO;
import com.dodaso.ecosystem.common.dto.FileUploadRequestDTO;
import com.dodaso.ecosystem.elcm.ui.constant.DestinationChoiceEnum;
import com.dodaso.ecosystem.elcm.ui.constant.FileStorageControllerAPIEnum;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.StagedFileUploadRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.UploadFilesService;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.WorkspaceOptionRow;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Backing bean for WEB-INF/uploadfilesdialog.xhtml. Split out as its own
 * bean rather than folded into StageDocumentsBean, matching the
 * bean-per-section convention used across dashboard.xhtml -- this dialog
 * is its own unit of UI state even though "Add to Pipeline" eventually
 * feeds Stage Documents.
 *
 * FILE TRANSPORT (browser -> elcm-ui): PrimeFaces <p:fileUpload
 * mode="advanced" multiple="true" auto="false" listener="#{uploadFilesBean.
 * handleFileUpload}"> in uploadfilesdialog.xhtml. auto="false" matters
 * here: files queue in the component client-side as they're picked/
 * dropped, and are only actually transmitted (each as its own ajax call,
 * invoking handleFileUpload once per file below) when the dialog's "Add
 * to Pipeline" button calls PF('ufdFileUploadWidget').upload().
 *
 * FILE TRANSPORT (elcm-ui -> common-service): addToPipeline() below posts
 * the accumulated uploadedFiles to common-service's
 * FileStorageController.upload() as a single JSON request
 * (FileUploadRequestDTO), via the same restServiceClient/RESTReqContainer
 * pattern PipelineMetricsBean and UserHelper already use for every other
 * cross-service call -- not a second multipart hop. FileItemDTO.content
 * (byte[]) round-trips as a base64 JSON string automatically via Jackson.
 *
 * TWO BLOCKERS, confirmed in chat, that must be resolved outside this file
 * before this compiles/runs:
 *   1. ServiceDiscoveryEnum.common_service does not exist yet --
 *      ServiceDiscoveryEnum lives in baseline-common (a compiled
 *      dependency with no source in this project), so it can't be added
 *      from elcm-ui. Needs an entry there mirroring elcm_service/
 *      iams_service, whose value must match whatever common-service is
 *      actually registered as in Eureka.
 *   2. That registration name currently has a real bug: common-service's
 *      application.properties (the shared non-local base file) sets
 *      spring.application.name=ecws-service -- almost certainly a
 *      copy/paste leftover -- while application-local.properties
 *      correctly says common_service. Until that's fixed, non-local
 *      profiles would register common-service under the wrong Eureka
 *      name and this call would resolve to the wrong service (or fail).
 *
 * OWNER/SOURCE VALUES ARE PLACEHOLDERS: ownerType="STAGED_DOCUMENT" and
 * ownerId=0L below are temporary -- there is no real staged_document row
 * yet at the point "Add to Pipeline" fires (that row doesn't exist until
 * elcm-service creates one, which isn't built yet). containerName is set
 * to "elcm-stage-documents" (a dedicated container, not the shared
 * "documents" default) since these are ELCM's own files. Wiring a real
 * ownerId -- and refreshing StageDocumentsBean's table with the response
 * -- is the next task, not part of this increment.
 *
 * ALL data from UploadFilesService is still hardcoded placeholder data --
 * see that class's Javadoc.
 */
@Named
@ViewScoped
@Getter
@Setter
@RequiredArgsConstructor
@Slf4j
public class UploadFilesBean extends BaseBean {

    private static final String CONTAINER_NAME = "elcm-stage-documents";
    private static final String SOURCE_APP = "ELCM";
    // TODO: placeholder until elcm-service creates a real staged_document
    // row before/around this call and gives us its id -- see class Javadoc.
    private static final String OWNER_TYPE = "STAGED_DOCUMENT";
    private static final Long OWNER_ID_PLACEHOLDER = 0L;

    private final UploadFilesService uploadFilesService;

    private WorkspaceOptionRow assignedWorkspace;
    private List<String> assignableUsers;

    private DestinationChoiceEnum destinationChoice;
    private String comments;
    private String assignToUser;

    /** Files received so far via handleFileUpload(), one entry per file --
     * p:fileUpload's advanced/multiple mode invokes the listener once per
     * file rather than once for the whole batch, so this accumulates
     * across however many ajax calls PF('ufdFileUploadWidget').upload()
     * triggers. */
    private List<StagedFileUploadRow> uploadedFiles;

    /** Result of the most recent common-service call, if any -- not yet
     * consumed by StageDocumentsBean's table (see class Javadoc); kept
     * here mainly so the outcome is inspectable/loggable for now. */
    private List<FileUploadRequestDTO> persistedFiles;

    @PostConstruct
    void init() {
        assignedWorkspace = uploadFilesService.findAssignedWorkspace(loginId);
        assignableUsers = uploadFilesService.findAssignableUsers();
        destinationChoice = null;
        comments = null;
        assignToUser = null;
        uploadedFiles = new ArrayList<>();
        persistedFiles = new ArrayList<>();
    }

    /**
     * Listener for p:fileUpload in uploadfilesdialog.xhtml. Fires once per
     * file (not once per batch) since the component is in multiple mode --
     * confirmed as the required behavior ("must support handling multiple
     * files"), so this appends rather than replaces.
     */
    public void handleFileUpload(final FileUploadEvent event) {
        final UploadedFile uploadedFile = event.getFile();
        try {
            uploadedFiles.add(new StagedFileUploadRow(
                uploadedFile.getFileName(),
                uploadedFile.getSize(),
                uploadedFile.getContentType(),
                uploadedFile.getContent()));
        } catch (final Exception e) {
            // getContent() can throw depending on the UploadedFile impl
            // (e.g. a temp-file-backed implementation whose file has
            // already been cleaned up) -- logging rather than letting one
            // bad file silently abort the rest of a multi-file batch.
            log.error("Failed to read uploaded file content for {}", uploadedFile.getFileName(), e);
        }
    }

    /**
     * "Add to Pipeline" button action. Sends every file accumulated in
     * uploadedFiles to common-service's FileStorageController.upload() in
     * a single request, then resets the dialog's own fields so a future
     * re-open starts clean (mirroring the client-side reset already done
     * in upload-files-dialog.js's ufdOnDialogShow()).
     *
     * Does NOT yet refresh StageDocumentsBean's table with the result, and
     * does NOT yet wire a real ownerId -- see class Javadoc for both.
     */
    public void addToPipeline() throws Exception {
        if (!uploadedFiles.isEmpty()) {
            persistedFiles = uploadToCommonService();
            log.info("common-service returned {} persisted file(s)", persistedFiles.size());
        }

        uploadFilesService.submitToPipeline(destinationChoice,
            assignedWorkspace.getCode(), comments, assignToUser);
        init();
    }

    private List<FileUploadRequestDTO> uploadToCommonService() throws Exception{
        final FileUploadRequestDTO request = new FileUploadRequestDTO();
        request.setSourceApp(SOURCE_APP);
        request.setOwnerType(OWNER_TYPE);
        request.setOwnerId(OWNER_ID_PLACEHOLDER);
        request.setContainerName(CONTAINER_NAME);
        request.setFiles(uploadedFiles.stream()
            .map(row -> {
                final FileItemDTO item = new FileItemDTO();
                item.setFileName(row.getFileName());
                item.setContentType(row.getContentType());
                item.setContent(row.getContent());
                return item;
            })
            .collect(Collectors.toList()));

        
        // Same restServiceClient/RESTReqContainer(serviceDiscoveryName,
        // endpoint, requestBody, responseType, httpMethod) shape used by
        // PipelineMetricsBean.init() and every call in UserHelper -- see
        // class Javadoc for the two blockers (ServiceDiscoveryEnum.
        // common_service, and common-service's application.properties bug)
        // that must be resolved before this actually compiles/resolves.
        FileUploadDTOContainer fileUploadDTOContainer = new FileUploadDTOContainer();
        fileUploadDTOContainer.setFileUploadRequestDTO(request);
        final RESTReqContainer<FileUploadDTOContainer> restReqContainer = new RESTReqContainer<>(
            ServiceDiscoveryEnum.common_service.getServiceDiscoveryName(),
            FileStorageControllerAPIEnum.upload.getEndPoint(),
            fileUploadDTOContainer,
            new ParameterizedTypeReference<>() {
            },
            HttpMethod.POST);
      
        final FileUploadDTOContainer response = restServiceClient.callRESTService(restReqContainer);
            return response != null && response.getFileUploadRequestDTOList() != null
                ? response.getFileUploadRequestDTOList()
                : List.of();
        }
}
