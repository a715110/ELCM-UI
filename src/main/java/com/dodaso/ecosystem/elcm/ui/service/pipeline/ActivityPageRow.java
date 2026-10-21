package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ADDED 2026-10-20. One page of a package's Activity Log, newest first; pages start at 0. Mirrors
 * elcm-service's ActivityPage.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActivityPageRow implements Serializable {

  private List<ActivityEntryRow> entries = new ArrayList<>();
  private int page;
  private int size;
  private long total;
  private int totalPages;
}
