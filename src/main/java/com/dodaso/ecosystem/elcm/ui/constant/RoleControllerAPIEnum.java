package com.dodaso.ecosystem.elcm.ui.constant;

import lombok.Getter;

/**
 * Endpoint constants for IAMS's RoleController
 * (ADEV-IAMS-SERVICE, base path /roleController), same one-enum-per-controller
 * convention as StageDocumentControllerAPIEnum. Lives in this UI project
 * because the shared IAMS data model has no enum for this controller yet.
 */
@Getter
public enum RoleControllerAPIEnum {
  getPageAccessByRoleName("/roleController/getPageAccessByRoleName");

  final String endPoint;

  RoleControllerAPIEnum(String endPoint) {
    this.endPoint = endPoint;
  }
}
