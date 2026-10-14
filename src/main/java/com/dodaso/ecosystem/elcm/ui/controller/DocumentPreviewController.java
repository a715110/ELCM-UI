package com.dodaso.ecosystem.elcm.ui.controller;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dodaso.ecosystem.common.dto.DocumentConversionDTO;
import com.dodaso.ecosystem.common.dto.FileUploadDTO;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.DocumentViewerService;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.TiffPreviewService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
@Slf4j
public class DocumentPreviewController {

    private final DocumentViewerService documentViewerService;
    private final TiffPreviewService tiffPreviewService;

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

    /**
     * ADDED 2026-10-02 -- Gotenberg office-document-conversion feature.
     * Rendered inline in documentviewer.xhtml's preview pane the same way
     * the plain preview() route is, but streams the CONVERTED PDF
     * (common-service's GET /{id}/conversion) rather than the original
     * office-format bytes, which a browser can't render natively.
     *
     * REVISED 2026-10-02 -- originally called getConvertedPdfBytes()
     * directly, trusting the bean's polling to never hit this route before
     * status was COMPLETED. That held for the <iframe> the bean itself
     * renders, but this route is also reachable directly (anyone can type
     * the URL, and that's exactly how a "why is Firefox blocking my
     * preview" report got diagnosed) -- common-service 404s when there's
     * no conversion row at all, which getConvertedPdfBytes() doesn't
     * catch, so that 404 propagated up as an UNCAUGHT exception and
     * Spring's default error handling turned it into a generic Whitelabel
     * error page (confusingly also a 404, but with no useful information
     * -- and, inside an <iframe>, indistinguishable at a glance from an
     * X-Frame-Options block). Now checks status first and returns a
     * proper 404/202, the same "not ready yet, not an error" contract
     * stream() area and common-service's own endpoints already follow.
     */
    @GetMapping("/{fileUploadId}/converted-preview")
    public ResponseEntity<byte[]> convertedPreview(@PathVariable final Long fileUploadId) throws Exception {
        final DocumentConversionDTO statusDto = documentViewerService.getConversionStatus(fileUploadId);
        if (statusDto == null || statusDto.getStatusDTO() == null) {
            log.warn("No document_conversion row for fileUploadId={} -- this file was never eligible for "
                    + "conversion, or predates the feature", fileUploadId);
            return ResponseEntity.notFound().build();
        }
        if (!"COMPLETED".equals(statusDto.getStatusDTO().getCode())) {
            // Still PENDING/PROCESSING, or FAILED -- not an error condition
            // for this route to throw over; the bean's own polling is what
            // decides what the user sees for each of those.
            return ResponseEntity.accepted().build();
        }

        final byte[] content = documentViewerService.getConvertedPdfBytes(fileUploadId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"converted.pdf\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(content);
    }

    /**
     * ADDED 2026-10-14 -- conversion state of an office file as small JSON, so the review pane can
     * wait for the converted PDF without downloading it on every check. status is the conversion
     * status code (PENDING, PROCESSING, COMPLETED, FAILED), or NONE when the file has no conversion.
     */
    @GetMapping("/{fileUploadId}/conversion-status")
    public ResponseEntity<Map<String, String>> conversionStatus(@PathVariable final Long fileUploadId) {
        final DocumentConversionDTO dto = documentViewerService.getConversionStatus(fileUploadId);
        final String status = dto == null || dto.getStatusDTO() == null ? "NONE" : dto.getStatusDTO().getCode();
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
            .body(Map.of("status", status));
    }

    /**
     * ADDED 2026-10-14 -- a TIFF cannot be shown by a browser, so this page stacks one PNG per TIFF
     * page (served by tiffPage()). The pages have ids p1, p2 and so on, so a link ending in #p3
     * scrolls to page 3.
     */
    @GetMapping(value = "/{fileUploadId}/tiff-preview", produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> tiffPreview(@PathVariable final Long fileUploadId) throws Exception {
        final byte[] tiff = tiffPreviewService.bytes(fileUploadId, () -> documentViewerService.getFileBytes(fileUploadId, true));
        final int pages = tiffPreviewService.pageCount(tiff);
        final StringBuilder html = new StringBuilder(
            "<!doctype html><html><head><meta charset=\"utf-8\"><title>TIFF preview</title><style>"
            + "body{margin:0;padding:8px;background:#EDEBE4;font:13px sans-serif;color:#55544D}"
            + "img{display:block;max-width:100%;margin:4px auto 12px;background:#fff;box-shadow:0 0 0 1px #D6D2C7}"
            + ".n{text-align:center}</style></head><body>");
        if (pages < 1) {
            html.append("<p class=\"n\">This TIFF could not be read. Download it to open it.</p>");
        }
        final int shown = Math.min(pages, TiffPreviewService.MAX_PAGES);
        for (int page = 1; page <= shown; page++) {
            html.append("<div class=\"n\">Page ").append(page).append(" of ").append(pages).append("</div>")
                .append("<img id=\"p").append(page).append("\" src=\"tiff-page/").append(page)
                .append("\" alt=\"Page ").append(page).append("\">");
        }
        if (pages > shown) {
            html.append("<p class=\"n\">Only the first ").append(shown).append(" pages are shown. Download the file to see the rest.</p>");
        }
        html.append("</body></html>");
        return ResponseEntity.ok().contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
            .body(html.toString());
    }

    /** ADDED 2026-10-14 -- one page of a TIFF as a PNG image; 404 when the page does not exist. */
    @GetMapping("/{fileUploadId}/tiff-page/{page}")
    public ResponseEntity<byte[]> tiffPage(@PathVariable final Long fileUploadId, @PathVariable final int page)
        throws Exception {
        final byte[] tiff = tiffPreviewService.bytes(fileUploadId, () -> documentViewerService.getFileBytes(fileUploadId, true));
        final byte[] png = tiffPreviewService.pagePng(tiff, page);
        if (png == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
            .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300").body(png);
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