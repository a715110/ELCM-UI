package com.dodaso.ecosystem.elcm.ui.service.access;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One page-level rule for a role, as returned by IAMS's
 * RoleController.getPageAccessByRoleName (a PageAccessDTO). Mapped to a small
 * UI-side type instead of reusing PageAccessDTO because that DTO also carries
 * Instant timestamps and back-references to the role that the UI never reads,
 * and a type with only these fields cannot fail on them.
 *
 * accessLevel: H = hidden, R = read only, E = editable. The JSON property
 * name "fieldAccesseDtoList" (sic) is what the IAMS DTO actually uses.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RoleAccessRow implements Serializable {
  private String pageName;
  private String accessLevel;
  private List<FieldAccessRow> fieldAccesseDtoList = new ArrayList<>();
}
