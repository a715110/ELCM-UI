package com.dodaso.ecosystem.elcm.ui.constant;

import lombok.Getter;

@Getter 
public enum PipelineMetricsControllerAPIEnum {
    getMetrics("/api/v1/pipeline/metrics/getMetrics");

    final String endPoint;

    PipelineMetricsControllerAPIEnum(String endPoint) {
        this.endPoint = endPoint;
    }    
}
