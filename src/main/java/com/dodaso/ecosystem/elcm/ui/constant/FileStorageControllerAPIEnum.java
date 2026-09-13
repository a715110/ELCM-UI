package com.dodaso.ecosystem.elcm.ui.constant;

import lombok.Getter;

@Getter 
public enum FileStorageControllerAPIEnum {
    upload("/api/v1/files/upload");

    final String endPoint;

    FileStorageControllerAPIEnum(String endPoint) {
        this.endPoint = endPoint;
    }    
}
