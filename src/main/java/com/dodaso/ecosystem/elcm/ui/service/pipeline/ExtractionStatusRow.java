package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Progress of a submitted package's extraction. Mirrors elcm-service's ExtractionStatus; fields
 * the screen does not use (timestamps) are ignored. state: NOT_SUBMITTED, QUEUED, RUNNING, DONE, FAILED, REVIEWED, APPLIED, DECLINED.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtractionStatusRow implements Serializable {
    private Long packageId;
    private Long submissionId;
    private String state;
    private String message;
    private Integer queuePosition;
    private int documentsTotal;
    private int documentsDone;
    private int documentsFailed;
    private int needsReviewTotal;
    private List<DocumentProgress> documents = new ArrayList<>();

    /** True while the extraction is still moving, so the screen keeps polling. */
    public boolean isActive() {
        return "QUEUED".equals(state) || "RUNNING".equals(state);
    }

    public boolean isFailed() {
        return "FAILED".equals(state);
    }

    public boolean isDone() {
        return "DONE".equals(state);
    }

    /** The reviewer has completed the review; values are read only. */
    public boolean isReviewed() {
        return "REVIEWED".equals(state);
    }

    /** The reviewed values were written to the contract record; everything is read only. */
    public boolean isApplied() {
        return "APPLIED".equals(state);
    }

    /** The reviewer sent the package back to Assembly; message holds who and why. */
    public boolean isDeclined() {
        return "DECLINED".equals(state);
    }

    /** Whole-package progress for the bar: finished documents over all documents. */
    public int getPercent() {
        return documentsTotal <= 0 ? 0 : Math.min(100, (documentsDone + documentsFailed) * 100 / documentsTotal);
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DocumentProgress implements Serializable {
        private Long stagedDocumentId;
        private String fileName;
        private String status;
        private int attempts;
        private Integer elapsedSeconds;
        private Integer needsReviewCount;
        private String errorMessage;

        /** "2:05" style, or empty before the job starts. */
        public String getElapsedText() {
            if (elapsedSeconds == null) {
                return "";
            }
            return (elapsedSeconds / 60) + ":" + String.format("%02d", elapsedSeconds % 60);
        }

        public String getSeverity() {
            if (status == null) {
                return "info";
            }
            return switch (status) {
                case "COMPLETED" -> "success";
                case "RUNNING" -> "warn";
                case "FAILED" -> "danger";
                default -> "info";
            };
        }
    }
}
