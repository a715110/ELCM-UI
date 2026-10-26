package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ADDED 2026-10-25. One page of the Contract Packages list. nextOffset is null when there is no
 * more; canSeeAll tells whether the user may use the All view.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PackageListPageRow implements Serializable {

  private List<PackageListRow> items = new ArrayList<>();
  private Integer total;
  private Integer nextOffset;
  private boolean canSeeAll;
}
