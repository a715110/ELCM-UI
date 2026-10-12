package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** What Apply to contract did. Mirrors elcm-service's ApplyResult; outcome is set by the UI service. */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApplyResultRow implements Serializable {
    private int written;
    private int unchanged;
    private List<String> skipped = new ArrayList<>();
    private String counterparty;
    /** NONE, CREATED, LINKED or UNCHANGED. */
    private String counterpartyAction;
    private String recordCode;

    @JsonIgnore
    private PackageOutcome outcome = PackageOutcome.OK;

    public static ApplyResultRow failed(final PackageOutcome outcome) {
        final ApplyResultRow row = new ApplyResultRow();
        row.setOutcome(outcome);
        return row;
    }
}
