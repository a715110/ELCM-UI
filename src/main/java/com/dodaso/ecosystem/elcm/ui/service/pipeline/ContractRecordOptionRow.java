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
 * recordCode, itemValue submits id straight into
 * UploadFilesBean.existingRecordId (a plain Long -- no converter needed,
 * same mechanism as f:selectItems' itemValue on a selectOneMenu).
 *
 * No-args constructor + setters required for the same reason as
 * StagedDocumentRow: Jackson's default deserialization (via
 * RestTemplate's message converter, used by UploadFilesService.
 * searchExistingRecords()) needs one or the other.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ContractRecordOptionRow implements Serializable {
    private Long id;
    private String recordCode;
}
