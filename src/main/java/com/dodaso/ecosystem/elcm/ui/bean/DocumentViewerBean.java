package com.dodaso.ecosystem.elcm.ui.bean;

import java.util.List;
import java.util.Set;

import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import com.dodaso.ecosystem.elcm.dto.ContractRecordDTO;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.DocumentViewerService;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Backing bean for WEB-INF/documentviewer.xhtml -- the Stage Documents
 * dashboard's new file-preview feature (the eye icon opens this page in a
 * new browser tab, per explicit decision in chat, split into a file-preview
 * pane and a record-details pane).
 *
 * All inputs arrive as request/view params on the URL dashboard.xhtml's
 * eye icon builds (see that page's h:outputLink) -- fileUploadId and
 * recordId are real ids this bean uses to fetch data; fileName/type/
 * workspace/record/assignee/uploadedAt are the SAME display strings
 * dashboard.xhtml's table already computed (StagedDocumentRow), passed
 * straight through as plain text rather than re-derived here, so this page
 * doesn't need a second "get one staged_document by id" endpoint that
 * doesn't exist yet -- just to redisplay values the dashboard already had
 * in hand. These are read-only display values; nothing here writes them
 * back anywhere.
 *
 * recordId is null whenever the staged document isn't linked to a real
 * ContractRecord yet (see StagedDocumentRow's Javadoc -- a NOT_SURE
 * submission, or any row older than the synchronous record-creation
 * feature) -- loadRecordDetail() below treats that as "nothing to fetch",
 * not an error, and the xhtml shows a "Not yet linked to a record" message
 * instead of the details panel in that case.
 *
 * File-type-based preview decision (isPdf/isImage/isPreviewable) is made
 * from the "type" param alone (the same file-extension string
 * StageDocumentService.deriveFileType() already computed) -- deliberately
 * NOT a second call to common-service for the file's real contentType,
 * since the extension is already known and good enough for "can a browser
 * render this inline" purposes; DocumentPreviewController still resolves
 * and sends the real contentType header when the preview pane's <iframe>/
 * <img> actually requests the bytes.
 */
@Named
@ViewScoped
@Getter
@Setter
@RequiredArgsConstructor(onConstructor_ = @Inject)
@Slf4j
public class DocumentViewerBean extends BaseBean {

    /** Rendered via an <embed>/<iframe> in documentviewer.xhtml. */
    private static final Set<String> PDF_TYPES = Set.of("PDF");

    /** Rendered via a plain <img> in documentviewer.xhtml. */
    private static final List<String> IMAGE_TYPES = List.of("PNG", "JPG", "JPEG", "GIF", "BMP", "WEBP");

    private final DocumentViewerService documentViewerService;

    private Long fileUploadId;

    /**
     * Deliberately a String, NOT a Long -- dashboard.xhtml's <f:param
     * name="recordId" value="#{doc.targetRecordId}"/> renders an empty
     * string (never omits the param entirely) when doc.targetRecordId is
     * null, and f:viewParam's default Long converter throws trying to
     * convert "" to a Long. A String field sidesteps that entirely; see
     * isRecordLinked()/recordIdAsLong() below for where the actual
     * null/blank-vs-real-id check and parsing happen.
     */
    private String recordId;

    private String fileName;
    private String type;
    private String workspace;
    private String record;
    private String assignee;
    private String uploadedAt;

    /** Populated by loadRecordDetail() (see xhtml's preRenderView) only
     * when recordId is non-null -- stays null for an unlinked document,
     * which the xhtml must check for before reading any of its fields. */
    private ContractRecordDTO recordDetail;

    /** Set when loadRecordDetail() is attempted but fails (elcm-service
     * down, or recordId pointing at a since-deleted record) -- shown as a
     * warning in the record-details pane instead of silently leaving it
     * blank with no explanation. */
    private String recordDetailError;

    /**
     * Bound to documentviewer.xhtml's <f:event type="preRenderView">, so it
     * runs once after f:viewParam has populated fileUploadId/recordId from
     * the URL, before the page actually renders. Only recordDetail needs a
     * server call -- fileUploadId itself is just handed straight to the
     * preview pane's <iframe>/<img> src (DocumentPreviewController does the
     * actual file lookup), so there's nothing to pre-fetch for the file
     * side.
     */
    public void loadRecordDetail() {
        if (!isRecordLinked()) {
            return;
        }
        try {
            recordDetail = documentViewerService.getRecordDetail(Long.valueOf(recordId));
        } catch (final Exception e) {
            log.error("Failed to load record detail for recordId={}", recordId, e);
            recordDetailError = "Couldn't load this record's details right now.";
        }
    }

    /** See recordId's own Javadoc for why this checks blank, not null --
     * an unlinked document arrives as an empty-string query param, not a
     * missing one. Bound by documentviewer.xhtml instead of a raw null
     * check on recordId. */
    public boolean isRecordLinked() {
        return recordId != null && !recordId.isBlank();
    }

    public boolean isPdf() {
        return type != null && PDF_TYPES.contains(type.toUpperCase());
    }

    public boolean isImage() {
        return type != null && IMAGE_TYPES.contains(type.toUpperCase());
    }

    public boolean isPreviewable() {
        return isPdf() || isImage();
    }
}
