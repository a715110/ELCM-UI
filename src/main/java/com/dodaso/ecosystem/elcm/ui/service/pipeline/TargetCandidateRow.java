package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A contract record a package without a target can be applied to. Mirrors elcm-service's TargetCandidate. */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TargetCandidateRow implements Serializable {
    private Long id;
    private String recordCode;
    private String contractType;
    private String counterparty;
    private String status;

    /** "OFFICE-00001 - Cedar Point Realty (Property Lease, Active)". */
    public String getLabel() {
        final StringBuilder sb = new StringBuilder(recordCode == null ? "" : recordCode);
        if (counterparty != null && !counterparty.isBlank()) {
            sb.append(" - ").append(counterparty);
        }
        sb.append(" (").append(contractType == null ? "" : contractType)
            .append(status == null ? "" : ", " + status).append(')');
        return sb.toString();
    }
}
