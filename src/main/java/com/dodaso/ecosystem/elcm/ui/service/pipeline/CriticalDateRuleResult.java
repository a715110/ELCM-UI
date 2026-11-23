package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import java.io.Serializable;

/**
 * ADDED 2026-11-21. Outcome of a critical date rule write: the screen as it stands afterwards when
 * it worked, otherwise a plain message for the user.
 */
public final class CriticalDateRuleResult implements Serializable {

  private final CriticalDateRuleRows.AdminView view;
  private final CriticalDateRuleRows.CheckResult check;
  private final String message;

  private CriticalDateRuleResult(final CriticalDateRuleRows.AdminView view,
      final CriticalDateRuleRows.CheckResult check, final String message) {
    this.view = view;
    this.check = check;
    this.message = message;
  }

  public static CriticalDateRuleResult ok(final CriticalDateRuleRows.AdminView view) {
    return new CriticalDateRuleResult(view, null, null);
  }

  public static CriticalDateRuleResult okCheck(final CriticalDateRuleRows.CheckResult check) {
    return new CriticalDateRuleResult(null, check, null);
  }

  public static CriticalDateRuleResult failed(final String message) {
    return new CriticalDateRuleResult(null, null, message);
  }

  public boolean isOk() {
    return message == null;
  }

  public CriticalDateRuleRows.AdminView getView() {
    return view;
  }

  public CriticalDateRuleRows.CheckResult getCheck() {
    return check;
  }

  public String getMessage() {
    return message;
  }
}
