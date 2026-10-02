package com.dodaso.ecosystem.elcm.ui.constant;

import lombok.Getter;

/**
 * Endpoint constants for elcm-service's ContractRecordController
 * (com.dodaso.ecosystem.elcm.controller.ContractRecordController), same
 * one-enum-per-controller convention as StageDocumentControllerAPIEnum /
 * PipelineMetricsControllerAPIEnum / FileStorageControllerAPIEnum.
 *
 * ADDED 2026-10-01 -- backs the Upload Files dialog's Existing Record
 * autocomplete (see UploadFilesService.searchExistingRecords()).
 */
@Getter
public enum ContractRecordControllerAPIEnum {
  searchContractRecords("/api/v1/pipeline/contract-record/search"),
  // ADDED 2026-10-01 -- base path for ContractRecordController.getById()
  // ("/api/v1/pipeline/contract-record/{id}"), backing the file-preview
  // feature's "record details" panel. A base path (not the full route)
  // since the id is a path segment appended at the call site -- see
  // DocumentViewerService.getRecordDetail().
  contractRecordBase("/api/v1/pipeline/contract-record");

  final String endPoint;

  ContractRecordControllerAPIEnum(String endPoint) {
    this.endPoint = endPoint;
  }
}
