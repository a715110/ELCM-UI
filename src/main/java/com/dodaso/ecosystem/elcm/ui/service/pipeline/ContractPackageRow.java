package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Row shape returned by ContractPackageService. See StagedDocumentRow's
 * class-level note for why this is a plain class rather than a record, and
 * why it lives in the service package rather than on a bean.
 *
 * Field names match the contract_package columns in
 * elcm_fc1_fc2_schema.sql.
 */
@Getter
@AllArgsConstructor
public class ContractPackageRow implements Serializable {
    private final String batchId;
    private final String packageCode;
    private final int docCount;
    private final String targetRecord;
    private final String workspace;
    private final String assignee;
    private final String roles;
    private final String status;
}