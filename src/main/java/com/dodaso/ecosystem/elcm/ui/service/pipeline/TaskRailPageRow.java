package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One page of Task Rail lines (ADDED 2026-10-18). nextBefore is null when there is no more. */
@Getter
@Setter
@NoArgsConstructor
public class TaskRailPageRow implements Serializable {

  private List<TaskRailRow> items = new ArrayList<>();
  private Long nextBefore;
}
