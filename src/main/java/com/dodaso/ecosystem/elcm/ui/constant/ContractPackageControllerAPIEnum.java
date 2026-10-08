package com.dodaso.ecosystem.elcm.ui.constant;

import lombok.Getter;

/**
 * Endpoint constants for elcm-service's ContractPackageController
 * (com.dodaso.ecosystem.elcm.controller.ContractPackageController), same
 * one-enum-per-controller convention as StageDocumentControllerAPIEnum.
 *
 * getContractPackages is also the base path for the write calls:
 *   POST   {base}                                  create a package
 *   POST   {base}/{id}/documents                   add documents to a draft package
 *   DELETE {base}/{id}/documents/{stagedDocumentId} remove one document from a draft package
 *   GET    {base}/{id}/documents                   the documents inside a package
 */
@Getter
public enum ContractPackageControllerAPIEnum {
  getContractPackages("/api/v1/pipeline/contract-packages"),
  getDocumentRoles("/api/v1/pipeline/contract-packages/document-roles");

  final String endPoint;

  ContractPackageControllerAPIEnum(String endPoint) {
    this.endPoint = endPoint;
  }
}
