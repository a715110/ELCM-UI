package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An earlier upload with the same content. Mirrors elcm-service's DuplicateMatch. */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DuplicateMatchRow implements Serializable {
  private String hash;
  private Long stagedDocumentId;
  private String fileName;
  private String uploadedBy;
  private String uploadedAt;
  private String packageCode;

  /** One line for the warning: who, when and where the earlier copy is. */
  public String getSummary() {
    final StringBuilder text = new StringBuilder("Same content as ");
    text.append(fileName == null || fileName.isBlank() ? "an earlier upload" : fileName);
    if (uploadedBy != null) {
      text.append(", uploaded by ").append(uploadedBy);
    }
    if (uploadedAt != null && uploadedAt.length() >= 10) {
      text.append(" on ").append(uploadedAt, 0, 10);
    }
    text.append(
        packageCode != null ? " (in package " + packageCode + ")" : " (not in a package yet)");
    return text.toString();
  }
}
