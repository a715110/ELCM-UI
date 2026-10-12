package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** What reviewers did to one field. Mirrors elcm-service's FieldHistory. */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FieldHistoryRow implements Serializable {
    private Long resultId;
    private String label;
    private String extractedValue;
    private List<Revision> revisions = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Revision implements Serializable {
        private int revisionNo;
        private String action;
        private String previousValue;
        private String newValue;
        private boolean confirmed;
        private String changedBy;
        private String changedAt;
        private String reason;

        public String getActionLabel() {
            if (action == null) {
                return "";
            }
            return switch (action) {
                case "CORRECTED" -> "Value changed";
                case "CONFIRMED" -> "Confirmed";
                case "UNCONFIRMED" -> "Confirmation removed";
                case "CONFIRM_ALL" -> "Confirmed (all verified)";
                default -> action;
            };
        }

        /** 2026-10-12T14:03:22.123 shown as 2026-10-12 14:03 (UTC). */
        public String getChangedAtText() {
            if (changedAt == null) {
                return "";
            }
            final String t = changedAt.replace('T', ' ');
            return (t.length() >= 16 ? t.substring(0, 16) : t) + " UTC";
        }
    }
}
