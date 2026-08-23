package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;

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
 */
@Getter
@AllArgsConstructor
public class StagedDocumentRow implements Serializable {
    private final String fileName;
    private final String type;
    private final String workspace;
    private final String record;
    private final String assignee;
    private final String uploadedAt;
}