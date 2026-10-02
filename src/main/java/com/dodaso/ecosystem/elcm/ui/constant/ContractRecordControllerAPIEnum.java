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
  searchContractRecords("/api/v1/pipeline/contract-record/search");

  final String endPoint;

  ContractRecordControllerAPIEnum(String endPoint) {
    this.endPoint = endPoint;
  }
}
