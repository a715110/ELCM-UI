package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The Task Rail badge number (ADDED 2026-10-18): open tasks assigned to the signed-in user. */
@Getter
@Setter
@NoArgsConstructor
public class TaskRailCountRow implements Serializable {

  private int assignedToMe;
}
