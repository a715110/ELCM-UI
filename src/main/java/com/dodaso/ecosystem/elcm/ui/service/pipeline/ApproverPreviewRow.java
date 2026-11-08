package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import com.dodaso.ecosystem.auth.dto.UserDirectoryDTO;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ADDED 2026-11-07 -- what elcm-service answers for the Complete review dialog: the login id of the
 * approver it would pick (null when nobody qualifies) and the people the reviewer may choose.
 */
@Getter
@Setter
@NoArgsConstructor
public class ApproverPreviewRow implements Serializable {
  private String suggested;
  private List<UserDirectoryDTO> candidates = new ArrayList<>();
}
