package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * elcm-ui's own copy of the row shape elcm-service's
 * ContractRecordController.search() returns -- unrelated class, same name,
 * same convention already used for StagedDocumentRow (see that class's
 * Javadoc for why two separate classes beat a shared module type here).
 * Jackson only needs the field names to match the JSON (id, recordCode).
 *
 * Bound by uploadfilesdialog.xhtml's Existing Record p:autoComplete:
 * completeMethod returns a List<ContractRecordOptionRow>, itemLabel shows
 * recordCode (now with counterpartyName appended -- see the xhtml's
 * itemLabel EL), itemValue submits id straight into
 * UploadFilesBean.existingRecordId (a plain Long -- no converter needed,
 * same mechanism as f:selectItems' itemValue on a selectOneMenu).
 *
 * No-args constructor + setters required for the same reason as
 * StagedDocumentRow: Jackson's default deserialization (via
 * RestTemplate's message converter, used by UploadFilesService.
 * searchExistingRecords()) needs one or the other.
 *
 * ADDED 2026-10-02 -- counterpartyName, so a counterparty-matched search
 * result shows WHY it matched (a bare record code gives no clue when the
 * typed text was "Acme", not "RETAIL-"). Null for a record with no
 * counterparty on file; the xhtml's itemLabel only appends it when
 * non-null. See elcm-service's RecordProvisioningService.search() for how
 * this is populated.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ContractRecordOptionRow implements Serializable {
    private Long id;
    private String recordCode;
    private String counterpartyName;
}
