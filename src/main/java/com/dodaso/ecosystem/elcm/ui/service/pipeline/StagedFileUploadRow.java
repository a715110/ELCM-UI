package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * One file received by UploadFilesBean.handleFileUpload(), held in memory
 * on the ViewScoped bean between the p:fileUpload ajax call(s) and the
 * eventual "Add to Pipeline" commit action.
 *
 * Plain @Getter class rather than a record -- see StagedDocumentRow for
 * why (EL can't always resolve record-style accessors); content isn't
 * bound in EL at all, but keeping this consistent with every other
 * row/DTO type in this package.
 *
 * NOT YET WIRED to Azure or to StageDocumentService -- content is held
 * here only. The next task (per chat) is UploadFilesBean's commit action
 * reading this list and calling an Azure-upload service, then refreshing
 * StageDocumentsBean's table with the resulting metadata. Until then this
 * is purely an in-memory holding area, so a large batch of big files does
 * mean a correspondingly large ViewScoped bean -- worth revisiting
 * (e.g. streaming straight to Azure per-file instead of buffering all of
 * them) once that next task starts, not a concern for this increment.
 */
@Getter
@AllArgsConstructor
public class StagedFileUploadRow implements Serializable {
    private final String fileName;
    private final long sizeBytes;
    private final String contentType;
    private final byte[] content;

    /** Human-readable size for the dialog's persistent file list (see
     * uploadfilesdialog.xhtml) -- kept here rather than formatted in EL so
     * the same logic isn't duplicated across views if this row type ever
     * gets displayed elsewhere. Not backed by a field, so Lombok's
     * @Getter doesn't touch it. */
    public String getFormattedSize() {
        if (sizeBytes < 1024) {
            return sizeBytes + " B";
        }
        if (sizeBytes < 1024 * 1024) {
            return String.format("%.0f KB", sizeBytes / 1024.0);
        }
        return String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0));
    }
}
