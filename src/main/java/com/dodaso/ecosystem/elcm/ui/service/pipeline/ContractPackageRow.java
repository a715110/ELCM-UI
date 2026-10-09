package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Row shape of the Contract Packages table, mirroring elcm-service's ContractPackageRow (same
 * field names, so the JSON maps one to one). See StagedDocumentRow's class-level note for why
 * this is a plain class rather than a record.
 *
 * REVISED 2026-10-07 -- no longer a mock. batchId is gone (no such concept in the schema).
 * id keys the add and remove calls. statusCode is the stable code the page tests (for example
 * ASSEMBLY); status is the display label. assignee is the resolved display name and
 * assigneeLoginId the stored login id. Needs setters and a no-argument constructor because it
 * is filled from JSON.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ContractPackageRow implements Serializable {
    private Long id;
    private String packageCode;
    private int docCount;
    private String targetRecord;
    private String workspace;
    private String assignee;
    private String assigneeLoginId;
    private String roles;
    private String status;
    private String statusCode;
    private int rolesAssigned;
}
