package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A contract record with its applied terms. Mirrors elcm-service's ContractRecordTerms. */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContractRecordTermsRow implements Serializable {
    private ContractRecordRow record;
    private List<TermRow> terms = new ArrayList<>();
}
