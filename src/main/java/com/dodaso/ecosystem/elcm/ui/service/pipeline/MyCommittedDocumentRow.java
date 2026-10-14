package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One committed document of the signed-in user. Mirrors elcm-service's MyCommittedDocument. */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MyCommittedDocumentRow implements Serializable {
  private Long stagedDocumentId;
  private Long fileUploadId;
  private String fileName;
  private String roleLabel;
  private Long packageId;
  private String packageCode;
  private String workspace;
  private String targetRecord;
  private String appliedAt;

  public String getAppliedAtText() {
    return MySubmissionRow.text(appliedAt);
  }
}
