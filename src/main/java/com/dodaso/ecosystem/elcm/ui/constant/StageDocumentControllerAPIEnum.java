package com.dodaso.ecosystem.elcm.ui.constant;

import lombok.Getter;

/**
 * Endpoint constants for elcm-service's StageDocumentController
 * (com.dodaso.ecosystem.elcm.controller.StageDocumentController), same
 * one-enum-per-controller convention as PipelineMetricsControllerAPIEnum /
 * FileStorageControllerAPIEnum.
 */
@Getter
public enum StageDocumentControllerAPIEnum {
  getStagedDocuments("/api/v1/pipeline/staged-documents");

  final String endPoint;

  StageDocumentControllerAPIEnum(String endPoint) {
    this.endPoint = endPoint;
  }
}