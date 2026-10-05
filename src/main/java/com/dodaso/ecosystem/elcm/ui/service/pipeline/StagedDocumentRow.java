package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Row shape returned by StageDocumentService. Lives here (not on a bean)
 * because it's a service-layer output type, not view state -- any bean
 * that needs staged-document data depends on this, not the other way
 * around.
 *
 * Deliberately a plain @Getter class, NOT a record -- see the note that
 * used to live on DashboardBean's nested version: records generate
 * accessor methods without a "get" prefix (fileName(), not getFileName()),
 * which EL's classic property resolution (#{doc.fileName} in xhtml) can't
 * always find depending on the Faces/EL version on the classpath. A plain
 * class sidesteps that regardless of which Faces version ends up running.
 *
 * Field names deliberately match the staged_document columns in
 * elcm_fc1_fc2_schema.sql, so wiring this to a real repository later is a
 * data-source swap inside StageDocumentService, not a redesign of this
 * type or of any bean/page that consumes it.
 *
 * REVISED 2026-10-01 -- added id/fileUploadId/targetRecordId, mirroring the
 * service-side row of the same name -- see that class's Javadoc. Backs
 * dashboard.xhtml's eye icon, which links out to documentviewer.xhtml with
 * these three as query params.
 *
 * REVISED 2026-10-03 -- added recordCounterparty/recordContractType/
 * recordStatus/recordWorkspace/uploadedBy/comments, mirroring the
 * service-side row's own same-date revision -- backs dashboard.xhtml's new
 * p:tooltip hover previews on the File Name and Record columns. Jackson
 * only needs the field names to match (same as every other field here);
 * see the service-side class's Javadoc for the null-handling rules (all
 * four record* fields are null together whenever targetRecordId is null).
 *
 * REVISED 2026-10-04 -- added assigneeTeamName/assigneeRoles/
 * assigneeWorkspaceCodes, mirroring the service-side row's own same-date
 * revision -- backs dashboard.xhtml's new Assignee-column hover preview.
 * All three null together whenever the assignee didn't resolve to an IAMS
 * directory entry (unassigned, or a display-name match miss) -- see the
 * service-side class's Javadoc.
 *
 * REVISED 2026-10-04 (assigneeId -> loginId) -- added assigneeLoginId,
 * mirroring the service-side row. "assignee" is now the resolved display
 * name (server-side), and assigneeLoginId is non-null exactly when the
 * assignee resolved to an IAMS directory entry -- dashboard.xhtml keys its
 * Assignee hover-preview variants off that.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StagedDocumentRow implements Serializable {
    private Long id;
    private String fileName;
    private String type;
    private String workspace;
    private String record;
    private String assignee;
    private String uploadedAt;
    private Long fileUploadId;
    private Long targetRecordId;
    private String recordCounterparty;
    private String recordContractType;
    private String recordStatus;
    private String recordWorkspace;
    private String uploadedBy;
    private String comments;
    private String assigneeTeamName;
    private String assigneeRoles;
    private String assigneeWorkspaceCodes;
    private String assigneeLoginId;
}