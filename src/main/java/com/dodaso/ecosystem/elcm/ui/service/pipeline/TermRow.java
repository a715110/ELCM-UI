package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import com.dodaso.ecosystem.elcm.ui.service.ShownTime;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One applied value of a contract record. Mirrors elcm-service's ContractTermView. */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TermRow implements Serializable {
    private Long id;
    private String fieldKey;
    private String label;
    private String dataType;
    private String value;
    private boolean current;
    private String packageCode;
    private Long submissionId;
    private Integer revisionNo;
    private String appliedBy;
    private String appliedAt;
    private String supersededAt;

    public String getAppliedAtText() {
        return timeText(appliedAt);
    }

    public String getSupersededAtText() {
        return timeText(supersededAt);
    }

    /** The applied time in UTC, for exports. */
    public String getAppliedAtUtc() {
        return ShownTime.utc(appliedAt);
    }

    /** The superseded time in UTC, for exports. */
    public String getSupersededAtUtc() {
        return ShownTime.utc(supersededAt);
    }

    /** True when the reviewer corrected the value before it was applied (revision above zero). */
    public boolean isCorrected() {
        return revisionNo != null && revisionNo > 0;
    }

    static String timeText(final String iso) {
        return ShownTime.text(iso, "-");
    }
}
