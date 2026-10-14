package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What the signed-in user may do in the review of a package. Mirrors elcm-service's ReviewAccess.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReviewAccessRow implements Serializable {

  /**
   * Has the review permission (Reviewer role).
   */
  private boolean permitted;
  /**
   * May edit, confirm, complete and apply: permitted and not the person who submitted the package.
   */
  private boolean canReview;
  private String reason;
  /**
   * Has the approve permission (Approver role).
   */
  private boolean approvePermitted;
  /**
   * May approve and apply, or send back: approvePermitted and not the submitter or the reviewer.
   */
  private boolean canApprove;
  private String approveReason;
}