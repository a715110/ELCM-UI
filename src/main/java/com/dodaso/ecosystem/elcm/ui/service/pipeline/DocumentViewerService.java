package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import com.dodaso.ecosystem.baseline.common.constant.ServiceDiscoveryEnum;
import com.dodaso.ecosystem.baseline.common.proxy.RESTServiceClient;
import com.dodaso.ecosystem.common.dto.DocumentConversionDTO;
import com.dodaso.ecosystem.common.dto.FileUploadDTO;
import com.dodaso.ecosystem.elcm.dto.ContractRecordDTO;
import com.dodaso.ecosystem.elcm.ui.constant.ContractRecordControllerAPIEnum;
import com.dodaso.ecosystem.elcm.ui.constant.FileStorageControllerAPIEnum;

import lombok.extern.slf4j.Slf4j;

/**
 * ADDED 2026-10-01 -- data access for the Stage Documents dashboard's new
 * file-preview feature (the eye icon -> documentviewer.xhtml). Two
 * unrelated things this talks to, both over the same restServiceClient/
 * ServiceDiscoveryEnum pattern every other cross-service call in this
 * codebase already uses:
 *
 *   - common-service, for the uploaded file itself: metadata (file name/
 *     content type, via getFileMetadata()) and raw bytes (via
 *     getFileBytes(), used by DocumentPreviewController to actually stream
 *     the preview/download response -- see that class's Javadoc for why
 *     the browser can't call common-service directly).
 *   - elcm-service, for the linked ContractRecord's full detail (via
 *     getRecordDetail()) -- null targetRecordId (see StagedDocumentRow's
 *     Javadoc) means "not yet linked to a record", so callers must check
 *     for that before calling this, not treat a lookup failure as the
 *     signal.
 *
 * getFileBytes()'s ParameterizedTypeReference<byte[]> assumes
 * restServiceClient's underlying RestTemplate has a byte-array message
 * converter available for arbitrary (non-JSON) response content types --
 * the same assumption Spring's default RestTemplate configuration
 * satisfies out of the box (ByteArrayHttpMessageConverter is registered by
 * default and handles any media type when nothing more specific matches).
 * If restServiceClient wraps a RestTemplate with a customized converter
 * list that dropped that default, this call would need to change to
 * whatever raw/streaming method (if any) restServiceClient actually
 * exposes -- verify in a dev environment before relying on this beyond
 * local testing, same as this codebase's other documented assumptions
 * about restServiceClient's exact capabilities.
 */
@Service
@Slf4j
public class DocumentViewerService {

    @Autowired
    private RESTServiceClient restServiceClient;

    public FileUploadDTO getFileMetadata(final Long fileUploadId) throws Exception {
        return restServiceClient.get(
            ServiceDiscoveryEnum.common_service.getServiceDiscoveryName(),
            FileStorageControllerAPIEnum.files.getEndPoint() + "/" + fileUploadId,
            new ParameterizedTypeReference<FileUploadDTO>() {
            });
    }

    /**
     * @param inline true renders in-browser (Content-Disposition: inline,
     *                common-service's new ?inline=true param); false forces
     *                a save-as download -- see DocumentPreviewController's
     *                two routes.
     */
    public byte[] getFileBytes(final Long fileUploadId, final boolean inline) throws Exception {
        return restServiceClient.get(
            ServiceDiscoveryEnum.common_service.getServiceDiscoveryName(),
            FileStorageControllerAPIEnum.files.getEndPoint() + "/" + fileUploadId + "/download?inline=" + inline,
            new ParameterizedTypeReference<byte[]>() {
            });
    }

    /**
     * @param recordId never null -- callers (DocumentViewerBean) must check
     *                 for a null targetRecordId themselves and skip this
     *                 call entirely ("not yet linked to a record" is a
     *                 valid state, not a lookup failure).
     */
    public ContractRecordDTO getRecordDetail(final Long recordId) throws Exception {
        return restServiceClient.get(
            ServiceDiscoveryEnum.elcm_service.getServiceDiscoveryName(),
            ContractRecordControllerAPIEnum.contractRecordBase.getEndPoint() + "/" + recordId,
            new ParameterizedTypeReference<ContractRecordDTO>() {
            });
    }

    /**
     * ADDED 2026-10-02 -- Gotenberg office-document-conversion feature
     * (docx/xlsx/pptx preview support). Mirrors getFileMetadata() against
     * common-service's new GET /{id}/conversion/status endpoint, which
     * 404s when the file was never eligible for conversion (already a
     * PDF/image, or predates this feature).
     *
     * Returns null (never throws) on a 404/lookup failure -- the bean's
     * polling loop (DocumentViewerBean.checkConversionStatus()) treats a
     * null result the same as "no conversion applies here" and stops
     * polling, same "empty means not applicable, not an error" contract
     * FileUploadService.getConversionStatus() uses server-side. The exact
     * exception restServiceClient throws for a 404 isn't something this
     * codebase has documented elsewhere yet (no prior caller here needed
     * to distinguish "not found" from "service down") -- if this ends up
     * masking real outages too broadly, tighten the catch once
     * restServiceClient's actual exception type for 404 is confirmed in a
     * dev environment.
     */
    public DocumentConversionDTO getConversionStatus(final Long fileUploadId) {
        try {
            return restServiceClient.get(
                ServiceDiscoveryEnum.common_service.getServiceDiscoveryName(),
                FileStorageControllerAPIEnum.files.getEndPoint() + "/" + fileUploadId + "/conversion/status",
                new ParameterizedTypeReference<DocumentConversionDTO>() {
                });
        } catch (final Exception e) {
            log.debug("No conversion status for fileUploadId={} (likely 404 -- not eligible for conversion)",
                fileUploadId, e);
            return null;
        }
    }

    /**
     * Converted PDF bytes, once DocumentConversionDTO.statusDTO.code is
     * COMPLETED -- used by DocumentPreviewController's converted-preview
     * route. Same inline-rendering intent as getFileBytes(); there's no
     * "force download" variant since a converted PDF is only ever shown in
     * the preview pane, never offered as its own download link (the
     * header's Download link always points at the ORIGINAL file).
     */
    public byte[] getConvertedPdfBytes(final Long fileUploadId) throws Exception {
        return restServiceClient.get(
            ServiceDiscoveryEnum.common_service.getServiceDiscoveryName(),
            FileStorageControllerAPIEnum.files.getEndPoint() + "/" + fileUploadId + "/conversion",
            new ParameterizedTypeReference<byte[]>() {
            });
    }
}