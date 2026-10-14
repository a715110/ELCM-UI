package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Extracted values of a package, per document. Mirrors elcm-service's ExtractionResults. */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtractionResultsRow implements Serializable {
    private Long packageId;
    private List<DocumentResult> documents = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DocumentResult implements Serializable {
        private Long stagedDocumentId;
        private Long fileUploadId;
        private String fileName;
        private String jobStatus;
        private String model;
        private int needsReviewCount;
        private List<FieldRow> fields = new ArrayList<>();

        /**
         * True when the pane beside the fields can show the file: PDF, image, TIFF, or an office
         * file (shown through its converted PDF).
         */
        public boolean isPreviewable() {
            return fileName != null
                && fileName.toLowerCase(java.util.Locale.ROOT)
                    .matches(".*\\.(pdf|png|jpe?g|gif|bmp|webp|tiff?|docx?|xlsx?|pptx?)$");
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FieldRow implements Serializable {
        private Long resultId;
        private String key;
        private String label;
        private String dataType;
        private boolean critical;
        private boolean required;
        private String value;
        private String correctedValue;
        private String status;
        private String quote;
        private String problem;
        private String note;
        private String candidates;
        private boolean needsReview;
        private boolean verified;
        private Integer pageNo;
        private String verifiedBy;

        /** What the reviewer should see: the corrected value when there is one, else the extracted one. */
        public String getShownValue() {
            if (correctedValue != null && !correctedValue.isBlank()) {
                return correctedValue;
            }
            return value != null ? value : "";
        }

        public String getStatusLabel() {
            if (status == null) {
                return "";
            }
            return switch (status) {
                case "verified" -> "Verified";
                case "inferred_absent" -> "Not in document";
                case "unverified" -> "Check";
                case "invalid" -> "Invalid";
                case "missing" -> "Missing";
                default -> status;
            };
        }

        public String getSeverity() {
            if (status == null) {
                return "info";
            }
            return switch (status) {
                case "verified" -> "success";
                case "inferred_absent" -> "info";
                case "unverified" -> "warn";
                default -> "danger";
            };
        }
    }
}
