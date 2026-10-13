package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One row of the Contract Records list. Mirrors elcm-service's ContractRecordSummary. */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContractRecordRow implements Serializable {
    private Long id;
    private String recordCode;
    private String contractType;
    private String counterparty;
    private String workspace;
    private String status;
    private int termCount;
    private String lastAppliedAt;

    /** "2026-10-12 21:30" style, or a dash when nothing was applied yet. */
    public String getLastAppliedText() {
        return TermRow.timeText(lastAppliedAt);
    }
}
