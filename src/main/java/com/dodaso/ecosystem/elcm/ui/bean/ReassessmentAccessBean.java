package com.dodaso.ecosystem.elcm.ui.bean;

import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import org.springframework.beans.factory.annotation.Value;

/**
 * ADDED 2026-11-11 -- tells the menu and the pages whether lease reassessment is switched on
 * (elcm.reassessment.enabled, default false). The same switch is read by elcm-service, which
 * refuses calls when it is off, so hiding the menu item is convenience, not security.
 */
@Named
@SessionScoped
public class ReassessmentAccessBean implements Serializable {

  @Value("${elcm.reassessment.enabled:false}")
  private boolean enabled;

  public boolean isEnabled() {
    return enabled;
  }
}
