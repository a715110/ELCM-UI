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

  /** ADDED 2026-10-25, Open list: all open tasks the user may see. */
  private Integer total;

  /** ADDED 2026-10-25, Open list: start of the next page; null when there is no more. */
  private Integer nextOffset;

  /** ADDED 2026-10-25: whether the user may use the All view. */
  private boolean canSeeAll;
}
