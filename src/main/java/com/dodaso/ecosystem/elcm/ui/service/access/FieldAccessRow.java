package com.dodaso.ecosystem.elcm.ui.service.access;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One field-level rule inside a page, as returned by IAMS
 * (FieldAccessDTO). Only the two properties the UI uses are mapped, and
 * everything else in the payload (id, createdAt, createdBy) is ignored, so a
 * change to those never breaks deserialization here.
 *
 * accessLevel is H (hidden) or R (read only); see RoleAccessRow.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FieldAccessRow implements Serializable {
  private String fieldName;
  private String accessLevel;
}
