package com.dodaso.ecosystem.elcm.ui.constant;

import lombok.Getter;

/** Endpoint of the Extraction Quality report in elcm-service (ADDED 2026-10-16). */
@Getter
public enum ExtractionQualityControllerAPIEnum {
  getReport("/api/v1/quality/extraction");

  final String endPoint;

  ExtractionQualityControllerAPIEnum(final String endPoint) {
    this.endPoint = endPoint;
  }
}
