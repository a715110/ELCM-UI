package com.dodaso.ecosystem.elcm.ui.bean;

import com.dodaso.ecosystem.baseline.common.proxy.RESTServiceClient;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.primefaces.PrimeFaces;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;

import com.dodaso.ecosystem.baseline.common.constant.ServiceDiscoveryEnum;
import com.dodaso.ecosystem.baseline.common.container.RESTReqContainer;
import com.dodaso.ecosystem.common.container.FileUploadDTOContainer;
import com.dodaso.ecosystem.common.dto.FileItemDTO;
import com.dodaso.ecosystem.common.dto.FileUploadDTO;
import com.dodaso.ecosystem.common.dto.FileUploadRequestDTO;
import com.dodaso.ecosystem.elcm.dto.ContractRecordDTO;
import com.dodaso.ecosystem.elcm.ui.constant.DestinationChoiceEnum;
import com.dodaso.ecosystem.elcm.ui.constant.FileStorageControllerAPIEnum;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.ContractRecordOptionRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.StagedFileUploadRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.UploadFilesService;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.WorkspaceOptionRow;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
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
 * mode="advanced" multiple="true" auto="true" listener="#{uploadFilesBean.
 * handleFileUpload}"> in uploadfilesdialog.xhtml. auto="true" (changed
 * from "false" per chat): each dropped/selected file is transmitted
 * immediately rather than queuing client-side until "Add to Pipeline" is
 * clicked - that's what actually drives PrimeFaces' native per-file
 * progress bar at the moment the user expects to see it (on drop), and
 * lands the file in this bean's uploadedFiles well before "Add to
 * Pipeline" is ever clicked. See uploadfilesdialog.xhtml's onstart/
 * oncomplete/onerror wiring for how the button is disabled while any
 * upload is still in flight, since auto mode decouples the button's
 * click from any single file's own completion.
 *
 * FILE TRANSPORT (elcm-ui -> common-service): addToPipeline() below posts
 * the accumulated uploadedFiles to common-service's
 * FileStorageController.upload() as a single JSON request
 * (FileUploadRequestDTO), via the same restServiceClient/RESTReqContainer
 * pattern PipelineMetricsBean and UserHelper already use for every other
 * cross-service call - not a second multipart hop. FileItemDTO.content
 * (byte[]) round-trips as a base64 JSON string automatically via Jackson.
 *
 * LIFECYCLE / RESET (confirmed in chat): this bean is @ViewScoped, so a
 * page refresh alone already discards any uploadedFiles that were sitting
 * in it - a refresh requests a new view, which gets an entirely new bean
 * instance, and the old one (holding whatever files had auto-uploaded
 * into it but never reached addToPipeline()) becomes unreachable and is
 * garbage-collected. No explicit reset call is needed for that specific
 * case; preDestroy() below only adds a log line for production
 * observability (so an abandoned-mid-upload batch is visible in logs),
 * it doesn't do anything the GC wasn't already going to do.
 *
 * The real risk reset()/resetState() actually address is a SAME-page,
 * later re-open of the dialog after an earlier attempt was abandoned
 * (Cancel/X, or simply navigating away within the same view without a
 * refresh) partway through populating uploadedFiles: since
 * handleFileUpload() only APPENDS and the only reset point used to be
 * success-only (inside addToPipeline() after a completed submission),
 * those orphaned entries would otherwise silently survive and get
 * bundled into the NEXT submission alongside genuinely new files.
 * reset() is wired (see uploadfilesdialog.xhtml / dashboard.xhtml) to
 * fire on every dialog OPEN (the actual correctness fix) and on
 * Cancel/close (frees the in-memory file bytes promptly on explicit
 * abandonment rather than leaving them held until whatever later moment
 * the dialog happens to reopen). This matters more now that
 * p:fileUpload is auto="true" - files land in uploadedFiles the moment
 * they're dropped, well before "Add to Pipeline" is ever clicked, so
 * there's a wider window in which Cancel/X could be hit with files
 * already sitting in memory.
 *
 * FAILURE HANDLING: a failed addToPipeline() (common-service unreachable,
 * network error, etc.) does NOT reset state and does NOT close the dialog
 * - the user's queued files, comments, and destination choice are left
 * intact so they can retry without re-entering anything, and an error
 * FacesMessage is added (rendered via <p:messages> in the dialog) so the
 * failure is visible rather than silently swallowed or surfaced only as
 * PrimeFaces' generic ajax-error handling.
 *
 * TWO BLOCKERS, confirmed in chat, that must be resolved outside this file
 * before this compiles/runs:
 * 1. ServiceDiscoveryEnum.common_service does not exist yet --
 * ServiceDiscoveryEnum lives in baseline-common (a compiled
 * dependency with no source in this project), so it can't be added
 * from elcm-ui. Needs an entry there mirroring elcm_service/
 * iams_service, whose value must match whatever common-service is
 * actually registered as in Eureka.
 * 2. That registration name currently has a real bug: common-service's
 * application.properties (the shared non-local base file) sets
 * spring.application.name=ecws-service - almost certainly a
 * copy/paste leftover - while application-local.properties
 * correctly says common_service. Until that's fixed, non-local
 * profiles would register common-service under the wrong Eureka
 * name and this call would resolve to the wrong service (or fail).
 *
 * OWNER/SOURCE/COMPANY VALUES ARE PLACEHOLDERS: ownerType="STAGED_DOCUMENT"
 * and companyId=0L below are temporary. There is no real staged_document
 * row yet at the point "Add to Pipeline" fires (that row doesn't exist
 * until elcm-service creates one, which isn't built yet), and elcm-ui has
 * no established way yet to resolve the logged-in user's company/tenant
 * id - that needs to come from wherever ELCM's own multi-tenant
 * identification actually lives (IAMS profile? a workspace concept?
 * something else?), which hasn't been decided. ownerId uses a random
 * positive long per submission rather than a fixed placeholder, so
 * multiple test uploads don't all collide under the same owner while
 * that's unresolved - still a placeholder, just one that avoids
 * accidental collisions in the interim. containerName is set to
 * "elcm-stage-documents" (a dedicated container, not the shared
 * "documents" default) since these are ELCM's own files - company
 * scoping is done via Azure Blob Index Tags on the common-service side,
 * not the container/path, per the naming-convention decision in chat.
 * Wiring a real ownerId/companyId - and refreshing StageDocumentsBean's
 * table with the response - is the next task, not part of this
 * increment.
 *
 * REVISED 2026-09-29: destinationChoice, comments, assignToUser, and the
 * New Record/Existing Record sub-panel fields are now real, bound form
 * fields (see uploadfilesdialog.xhtml) instead of static markup -- and
 * critically, commitToPipeline's remoteCommand had to change from
 * process="@this" to process="@form", since @this meant none of those
 * fields (nor the workspace override) were ever actually decoded into
 * this bean regardless of what the user typed/selected. addToPipeline()
 * no longer overwrites destinationChoice to a hardcoded NEW_RECORD before
 * use -- that line was a leftover from before the cards had any real
 * selection at all (see uploadfilesdialog.xhtml's DESTINATION CARDS
 * comment) and would have silently misrouted every Existing Record/Not
 * Sure submission as if it were New Record.
 */
@Named
@ViewScoped
@Getter
@Setter
@RequiredArgsConstructor(onConstructor_ = @Inject)
@Slf4j
public class UploadFilesBean extends BaseBean {

    private static final String CONTAINER_NAME = "elcm-documents";
    private static final String SOURCE_APP = "elcm";
    private static final String OWNER_TYPE = "staged_document";
    // TODO: placeholder until elcm-ui has a real way to resolve the
    // logged-in user's company/tenant id -- see class Javadoc.
    private static final Long COMPANY_ID_PLACEHOLDER = 0L;

    /** Matches the dialog's Contract Type dropdown default selection
     * (ufdNewContractType's first f:selectItem) -- see resetState(). */
    private static final String DEFAULT_NEW_RECORD_CONTRACT_TYPE = "PROPERTY_LEASE";

    private final UploadFilesService uploadFilesService;

    private WorkspaceOptionRow assignedWorkspace;

    private String assignedWorkspaceCode;

    /** Full pick list for the WORKSPACE selector on uploadfilesdialog.xhtml's
     * right panel ("change to override" the onboarding-assigned workspace).
     * Populated in resetState() alongside assignedWorkspace; assignedWorkspace
     * itself remains the selected value AND the one addToPipeline() reads
     * (assignedWorkspace.getCode()) -- the dropdown binds directly to
     * assignedWorkspace.code, so overriding the selection updates the same
     * object the rest of this bean already uses, with no separate
     * "selected workspace" field to keep in sync. */
    private List<WorkspaceOptionRow> availableWorkspaces;

    private List<String> assignableUsers;

    private DestinationChoiceEnum destinationChoice;
    private String comments;
    private String assignToUser;

    @Override
    public RESTServiceClient getRestServiceClient() {
        return super.getRestServiceClient();
    }

    /** New Record sub-panel fields (uploadfilesdialog.xhtml) -- only
     * meaningful, and only required, when destinationChoice == NEW_RECORD.
     * See addToPipeline()'s validateDestinationSpecificFields(). */
    private String newRecordName;
    private String newRecordCounterparty;
    private String newRecordPropertyAddress;
    private String newRecordContractType = DEFAULT_NEW_RECORD_CONTRACT_TYPE;

    /** ADDED 2026-10-01 -- New Record sub-panel's Address Line 2/City/
     * State/Zip fields (uploadfilesdialog.xhtml's ufd-field-grid-3 row).
     * City is marked required (*) on the dialog but, same as the rest of
     * this increment, not yet enforced in validateDestinationSpecificFields()
     * below -- see chat note to revisit that consistently. */
    private String newRecordAddressLine2;
    private String newRecordCity;
    private String newRecordState;
    private String newRecordZip;

    /** Existing Record sub-panel's search field -- only meaningful when
     * destinationChoice == EXISTING_RECORD. Kept only as an audit trail on
     * staged_document now (see that entity's Javadoc) -- the actual link
     * to a real record is existingRecordId below, the id the user picked
     * from the autocomplete, not this raw search text. */
    private String existingRecordQuery;

    /** ADDED 2026-10-01 -- the record actually selected from
     * ufdExistingRecordSearch's p:autoComplete (itemValue="#{rec.id}"), as
     * opposed to existingRecordQuery above, which only ever holds
     * whatever free text the user last typed into the box. This is what
     * elcm-service's StageDocumentService.resolveTargetRecord() actually
     * looks up -- see validateDestinationSpecificFields() below, which
     * requires this to be non-null for EXISTING_RECORD. */
    private Long existingRecordId;

    /** ADDED 2026-10-02 -- populated by onExistingRecordSelected() once the
     * user picks a result from ufdExistingRecordSearch; shown in the
     * dialog's new "Existing Record" detail panel (counterparty/address
     * for now -- see uploadfilesdialog.xhtml). Reuses the same
     * ContractRecordDTO shape the document-viewer feature already uses,
     * via UploadFilesService.getRecordDetail() -- same underlying
     * elcm-service endpoint, no new backend route. Null until a record is
     * actually selected, and reset to null by resetState()/changing the
     * selection. */
    private ContractRecordDTO existingRecordDetail;

    /** Set when onExistingRecordSelected()'s detail lookup fails (e.g.
     * elcm-service down) -- shown as a small inline warning instead of
     * silently leaving the detail panel blank. Does not block submission:
     * existingRecordId is already known and valid (it came straight from
     * the search result the user clicked), so a failed detail *lookup* is
     * cosmetic, not a reason to stop the user from proceeding. */
    private String existingRecordDetailError;

    /** Files received so far via handleFileUpload(), one entry per file --
     * p:fileUpload's advanced/multiple mode invokes the listener once per
     * file rather than once for the whole batch, so this accumulates
     * across however many ajax calls PF('ufdFileUploadWidget').upload()
     * triggers. */
    private List<StagedFileUploadRow> uploadedFiles;

    /** Result of the most recent common-service call, if any -- not yet
     * consumed by StageDocumentsBean's table (see class Javadoc); kept
     * here mainly so the outcome is inspectable/loggable for now. */
    private List<FileUploadDTO> persistedFiles;

    /** Set at the end of every addToPipeline() attempt (true on success,
     * false on failure) -- read by uploadfilesdialog.xhtml's
     * commitToPipeline remote command to decide whether to close the
     * dialog. Starts true so an ajax response glitch before the field is
     * ever set doesn't accidentally block a legitimate close; every real
     * addToPipeline() call always sets it explicitly either way before
     * the ajax response is rendered, so this default is never actually
     * observed by the client in practice. */
    private boolean submissionSuccessful = true;

    @PostConstruct
    void init() {
        resetState();
    }

    /**
     * Purely observational -- see class Javadoc's LIFECYCLE / RESET
     * section. Doesn't clear anything (there's nothing left to clear;
     * this whole instance is being discarded), just logs when a page
     * refresh/navigation-away is discarding files that were auto-uploaded
     * into this bean but never reached addToPipeline(), so that's visible
     * in production logs rather than silently vanishing.
     */
    @PreDestroy
    void preDestroy() {
        if (uploadedFiles != null && !uploadedFiles.isEmpty()) {
            log.warn("UploadFilesBean destroyed with {} file(s) never submitted via addToPipeline() "
                + "(likely a page refresh or navigation away before clicking Add to Pipeline); "
                + "these were held in memory only and are now discarded.", uploadedFiles.size());
        }
    }

    /**
     * Public re-entry point for resetState() -- see class Javadoc's
     * LIFECYCLE / RESET section for why this needs to be callable outside
     * @PostConstruct (dialog re-open and Cancel/close, not just bean
     * construction). Bound as a real ajax action from
     * uploadfilesdialog.xhtml (Cancel/X-close) and dashboard.xhtml
     * (the "Upload Files" button that opens this dialog).
     */
    public void reset() {
        resetState();
    }

    /**
     * Pick list for uploadfilesdialog.xhtml's "WHERE SHOULD THESE DOCUMENTS
     * GO?" destination cards, bound as a custom-layout p:selectOneRadio over
     * destinationChoice. Deliberately just DestinationChoiceEnum.values() --
     * the visible card copy ("New Record" / "Existing Record" / "Not sure --
     * leave instructions") is written directly into the xhtml rather than
     * derived from this enum, so this bean never needs to know that enum's
     * constant names, only that it has exactly three values in the same
     * order as the three cards (0=New Record, 1=Existing Record, 2=Not
     * Sure) -- see the dialog's p:radioButton itemIndex values, which are
     * keyed to this same order.
     */
    public DestinationChoiceEnum[] getDestinationChoiceOptions() {
        return DestinationChoiceEnum.values();
    }

    private void resetState() {
        try {
            assignedWorkspace = uploadFilesService.findAssignedWorkspace(loginId);
            assignedWorkspaceCode = (assignedWorkspace != null) ? assignedWorkspace.getCode() : null;
            availableWorkspaces = uploadFilesService.findAllWorkspaces();
            assignableUsers = uploadFilesService.findAssignableUsers();
        } catch (final Exception e) {
            // A hiccup loading placeholder lookup data shouldn't block the
            // dialog from opening/resetting at all -- degrade to empty/
            // null rather than leaving the dialog stuck unusable, but
            // surface it so it's not silently invisible either.
            log.error("Failed to load workspace/assignable-users data while resetting UploadFilesBean", e);
            assignedWorkspace = null;
            assignedWorkspaceCode = null;
            availableWorkspaces = List.of();
            assignableUsers = List.of();
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
                FacesMessage.SEVERITY_WARN, "Some upload options could not be loaded", "You can still add files."));
        }
        destinationChoice = null;
        comments = null;
        assignToUser = null;
        newRecordName = null;
        newRecordCounterparty = null;
        newRecordPropertyAddress = null;
        newRecordContractType = DEFAULT_NEW_RECORD_CONTRACT_TYPE;
        newRecordAddressLine2 = null;
        newRecordCity = null;
        newRecordState = null;
        newRecordZip = null;
        existingRecordQuery = null;
        existingRecordId = null;
        existingRecordDetail = null;
        existingRecordDetailError = null;
        uploadedFiles = new ArrayList<>();
        persistedFiles = new ArrayList<>();
        submissionSuccessful = true;
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
            // already been cleaned up) -- logging AND surfacing to the
            // user (via the dialog's <p:messages>) rather than letting one
            // bad file silently vanish from a multi-file batch with no
            // indication anything went wrong.
            log.error("Failed to read uploaded file content for {}", uploadedFile.getFileName(), e);
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
                FacesMessage.SEVERITY_ERROR, "Couldn't read " + uploadedFile.getFileName(),
                "This file was skipped; the others in this batch are unaffected."));
        }
    }

    /**
     * Removes one already-uploaded file from the dialog's persistent list
     * (see uploadfilesdialog.xhtml's ufdUploadedFileList) before "Add to
     * Pipeline" is clicked. Reference-based removal (StagedFileUploadRow
     * has no equals()/hashCode() override) is fine here -- the instance
     * passed back from EL is the exact same one held in this list, since
     * it came from iterating uploadedFiles itself in the first place.
     */
    public void removeFile(final StagedFileUploadRow file) {
        uploadedFiles.remove(file);
    }

    /**
     * "Add to Pipeline" button action (invoked via the commitToPipeline
     * remote command once every queued file has finished uploading -- see
     * uploadfilesdialog.xhtml). Sends every file accumulated in
     * uploadedFiles to common-service's FileStorageController.upload() in
     * a single request, then creates the matching staged_document row(s)
     * via UploadFilesService.submitToPipeline().
     *
     * On success: resets the dialog's own fields (resetState()) so a
     * future re-open starts clean, and sets submissionSuccessful=true so
     * the client closes the dialog. On failure: leaves all state intact
     * (nothing is lost -- the user can just retry) and sets
     * submissionSuccessful=false so the client keeps the dialog open and
     * shows the error message added below.
     *
     * REVISED 2026-09-29: no longer overwrites destinationChoice to a
     * hardcoded NEW_RECORD -- see class Javadoc. Now validates a
     * destination was actually chosen, and (for New Record specifically)
     * that Counterparty/Property Address were filled in, before doing
     * anything else -- a validation failure here leaves uploadedFiles
     * alone (nothing has been sent to common-service yet at that point),
     * same "nothing is lost" guarantee as every other failure path below.
     *
     * Does NOT yet refresh StageDocumentsBean's table with the result, and
     * does NOT yet wire a real companyId -- see class Javadoc for both.
     */
    public void addToPipeline() {
        try {
            //we will add all the validations at a later phase of development
            if (assignedWorkspaceCode == null) {
                throw new IllegalStateException("No workspace is assigned -- cannot determine where to file these documents.");
            }
            if (destinationChoice == null) {
                throw new IllegalStateException("Please choose where these documents should go.");
            }
            validateDestinationSpecificFields();

            if (!uploadedFiles.isEmpty()) {
                persistedFiles = uploadToCommonService();
                log.info("common-service returned {} persisted file(s)", persistedFiles.size());
            }

            uploadFilesService.submitToPipeline(destinationChoice,
                assignedWorkspaceCode, comments, assignToUser, loginId, persistedFiles,
                newRecordName, newRecordCounterparty, newRecordPropertyAddress, newRecordContractType,
                newRecordAddressLine2, newRecordCity, newRecordState, newRecordZip,
                existingRecordQuery, existingRecordId);

            resetState();
            submissionSuccessful = true;
        } catch (final IllegalStateException e) {
            // Validation failures (no workspace, no destination chosen,
            // New Record missing a required field) -- these messages are
            // written for the user, unlike a generic downstream failure
            // below, so show them directly rather than a canned summary.
            log.warn("addToPipeline validation failed: {}", e.getMessage());
            submissionSuccessful = false;
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
                FacesMessage.SEVERITY_WARN, e.getMessage(), null));
        } catch (final Exception e) {
            log.error("addToPipeline failed", e);
            submissionSuccessful = false;
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
                FacesMessage.SEVERITY_ERROR, "Couldn't add these files to the pipeline",
                "Nothing was lost -- your files and details are still here. Please try again."));
        }
        PrimeFaces.current().ajax().addCallbackParam("submissionSuccessful", submissionSuccessful);
    }

    /**
     * The dialog only marks Counterparty/Property Address as required
     * (asterisks in uploadfilesdialog.xhtml) for the New Record
     * destination -- Existing Record's search field and Not Sure have no
     * required fields of their own beyond the destination choice itself.
     */
    private void validateDestinationSpecificFields() {
        if (destinationChoice == DestinationChoiceEnum.NEW_RECORD) {
            if (newRecordCounterparty == null || newRecordCounterparty.isBlank()) {
                throw new IllegalStateException("Counterparty is required for a new record.");
            }
            if (newRecordPropertyAddress == null || newRecordPropertyAddress.isBlank()) {
                throw new IllegalStateException("Property address is required for a new record.");
            }
            // ADDED 2026-10-01 -- City is marked required (*) on the dialog
            // now that it's a real column feeding Address/Property creation
            // (see RecordProvisioningService.createNewRecord()); enforcing
            // it here too rather than only in the markup.
            if (newRecordCity == null || newRecordCity.isBlank()) {
                throw new IllegalStateException("City is required for a new record.");
            }
        } else if (destinationChoice == DestinationChoiceEnum.EXISTING_RECORD) {
            // ADDED 2026-10-01 -- now that Existing Record is a real
            // autocomplete bound to existingRecordId (not just free text),
            // require an actual selection before submitting -- otherwise
            // elcm-service's resolveTargetRecord() would reject the batch
            // anyway (see StageDocumentService), just later and less
            // helpfully.
            if (existingRecordId == null) {
                throw new IllegalStateException("Please select an existing record from the search results.");
            }
        }
    }

    /**
     * ADDED 2026-10-01 -- completeMethod for uploadfilesdialog.xhtml's
     * ufdExistingRecordSearch p:autoComplete. Delegates to
     * UploadFilesService.searchExistingRecords() (a real elcm-service
     * call, not a client-side filter); also stashes the raw query text
     * into existingRecordQuery so it's still captured as an audit trail on
     * staged_document even though existingRecordId (set by the
     * autocomplete's own value binding once the user picks a suggestion)
     * is what actually drives record linking.
     */
    public List<ContractRecordOptionRow> completeExistingRecords(final String query) {
        existingRecordQuery = query;
        try {
            return uploadFilesService.searchExistingRecords(query);
        } catch (final Exception e) {
            // Same "don't block the dialog over a degraded lookup" stance
            // as resetState()'s workspace/assignable-users load -- an
            // autocomplete that errors out should look like "no matches"
            // to the user, not blow up the ajax request.
            log.error("Failed to search existing records for query '{}'", query, e);
            return List.of();
        }
    }

    /**
     * ADDED 2026-10-02 -- itemSelect listener for ufdExistingRecordSearch,
     * backing the new "show details once you've picked a record" panel.
     * PrimeFaces fires itemSelect AFTER the component's own value binding
     * has already set existingRecordId (forceSelection="true" on the
     * autocomplete means the event's selected object is always one of the
     * completeMethod's own results, never free text) -- so this reads
     * existingRecordId directly rather than the event payload, keeping one
     * source of truth for "which record is selected" instead of two that
     * could disagree.
     *
     * A failed lookup here does NOT clear existingRecordId or block
     * submission -- see existingRecordDetailError's own Javadoc -- it only
     * means the detail panel shows a warning instead of the record's
     * counterparty/address.
     */
    public void onExistingRecordSelected() {
        existingRecordDetail = null;
        existingRecordDetailError = null;
        if (existingRecordId == null) {
            return;
        }
        try {
            existingRecordDetail = uploadFilesService.getRecordDetail(existingRecordId);
        } catch (final Exception e) {
            log.error("Failed to load existing record detail for id={}", existingRecordId, e);
            existingRecordDetailError = "Couldn't load this record's details right now.";
        }
    }

    private List<FileUploadDTO> uploadToCommonService() throws Exception {
        final FileUploadRequestDTO request = new FileUploadRequestDTO();
        request.setCompanyId(COMPANY_ID_PLACEHOLDER);
        request.setSourceApp(SOURCE_APP);
        request.setOwnerType(OWNER_TYPE);
        // Random rather than a fixed placeholder -- see class Javadoc's
        // OWNER/SOURCE/COMPANY VALUES ARE PLACEHOLDERS note for why.
        request.setOwnerId((long) ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE));
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
        final FileUploadDTOContainer fileUploadDTOContainer = new FileUploadDTOContainer();
        fileUploadDTOContainer.setFileUploadRequestDTO(request);
        final RESTReqContainer<FileUploadDTOContainer> restReqContainer = new RESTReqContainer<>(
            ServiceDiscoveryEnum.common_service.getServiceDiscoveryName(),
            FileStorageControllerAPIEnum.upload.getEndPoint(),
            fileUploadDTOContainer,
            new ParameterizedTypeReference<>() {
            },
            HttpMethod.POST);

        final FileUploadDTOContainer response = restServiceClient.callRESTService(restReqContainer);
        if (response == null) {
            // A null response with no exception thrown would otherwise
            // look identical to "zero files uploaded" -- treat it as a
            // failure explicitly rather than silently returning an empty
            // list and letting resetState() run as if nothing was wrong.
            throw new IllegalStateException("common-service returned no response for the upload request.");
        }
        return response.getFileUploadDTOList() != null ? response.getFileUploadDTOList() : List.of();
    }
}