package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import java.io.Serializable;

import com.dodaso.ecosystem.elcm.dto.WorkspaceDTO;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * One entry in the workspace picker on uploadfilesdialog.xhtml ("WORKSPACE"
 * field). Plain @Getter class rather than a record -- see StagedDocumentRow
 * for why (EL can't always resolve record-style accessors).
 */
@Getter
@AllArgsConstructor
public class WorkspaceOptionRow implements Serializable {
    private final String code; // e.g. "RETAIL"
    private final String label; // e.g. "Retail" (the pill text)
    private final String description; // e.g. "Retail Portfolio" idential to Business Area

    public static WorkspaceOptionRow fromDto(WorkspaceDTO dto) {
        if (dto == null) {
            return null;
        }
        return new WorkspaceOptionRow(
                dto.getCode(),
                dto.getName(),
                dto.getBusinessArea());
    }
}
