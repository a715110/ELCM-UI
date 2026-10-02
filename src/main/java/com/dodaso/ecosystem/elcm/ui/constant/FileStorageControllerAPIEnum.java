package com.dodaso.ecosystem.elcm.ui.constant;

import lombok.Getter;

@Getter
public enum FileStorageControllerAPIEnum {
    upload("/api/v1/files/upload"),
    // ADDED 2026-10-01 -- base path for common-service's FileStorageController
    // ("/api/v1/files"), used by DocumentPreviewController to build
    // "{files}/{id}" (metadata) and "{files}/{id}/download" (bytes) for the
    // Stage Documents dashboard's new file-preview feature. A base path
    // rather than two separate full endpoints since the id is a path
    // segment, not a query param -- same reasoning as
    // ContractRecordControllerAPIEnum if it ever needs a get-by-id route.
    files("/api/v1/files");

    final String endPoint;

    FileStorageControllerAPIEnum(String endPoint) {
        this.endPoint = endPoint;
    }
}
