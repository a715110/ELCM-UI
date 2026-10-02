package com.dodaso.ecosystem.elcm.ui.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dodaso.ecosystem.common.dto.FileUploadDTO;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.DocumentViewerService;

import lombok.RequiredArgsConstructor;

/**
 * ADDED 2026-10-01 -- a plain Spring MVC @RestController living alongside
 * elcm-ui's JSF/PrimeFaces pages (this app already runs both, via
 * JoinFaces's Faces servlet + Spring Boot's own DispatcherServlet; no new
 * servlet wiring needed). Backs the Stage Documents dashboard's new
 * file-preview feature.
 *
 * WHY THIS EXISTS: a browser can't call common-service directly -- it's
 * resolved via Eureka service name (ServiceDiscoveryEnum), not a public
 * URL the browser's own network stack can reach, the same reason every
 * other cross-service call in this codebase goes through
 * RESTServiceClient/RESTReqContainer from server-side code, never straight
 * from the client. So documentviewer.xhtml's preview pane (an <iframe>/
 * <img>) and its "Download" fallback link both point here instead, at a
 * route that IS reachable from the browser (elcm-ui's own context), and
 * this controller does the actual common-service call server-side,
 * streaming the raw bytes straight through in the HTTP response.
 *
 * Two routes rather than one route + a request param: keeps the intent
 * obvious from the URL alone (a link or <iframe src> is self-documenting),
 * and avoids a stray "?inline=true/false" the person could edit by hand in
 * the address bar for no benefit.
 *
 * No security/auth check here yet -- same caveat as every other controller
 * in this codebase at this stage (see PipelineMetricsController's
 * class-level note); anyone who can reach elcm-ui and guess/enumerate a
 * fileUploadId can retrieve that file. Revisit once elcm-ui has a real
 * security context to check ownership/workspace access against.
 */
@RestController
@RequestMapping("/api/v1/pipeline/document-viewer")
@RequiredArgsConstructor
public class DocumentPreviewController {

    private final DocumentViewerService documentViewerService;

    /**
     * Rendered inline in documentviewer.xhtml's preview pane (an <iframe>
     * for PDFs, an <img> for images) -- see that page's Javadoc-equivalent
     * comment for the file-type check that decides which, and the
     * fallback message shown for anything else.
     */
    @GetMapping("/{fileUploadId}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable final Long fileUploadId) throws Exception {
        return stream(fileUploadId, true);
    }

    /**
     * The preview pane's "Download" fallback link, and also usable as a
     * normal save-as download regardless of file type.
     */
    @GetMapping("/{fileUploadId}/download")
    public ResponseEntity<byte[]> download(@PathVariable final Long fileUploadId) throws Exception {
        return stream(fileUploadId, false);
    }

    private ResponseEntity<byte[]> stream(final Long fileUploadId, final boolean inline) throws Exception {
        final FileUploadDTO metadata = documentViewerService.getFileMetadata(fileUploadId);
        final byte[] content = documentViewerService.getFileBytes(fileUploadId, inline);

        final MediaType mediaType = metadata.getContentType() != null
            ? MediaType.parseMediaType(metadata.getContentType())
            : MediaType.APPLICATION_OCTET_STREAM;
        final String dispositionType = inline ? "inline" : "attachment";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                dispositionType + "; filename=\"" + metadata.getFileName() + "\"")
            .contentType(mediaType)
            .body(content);
    }
}