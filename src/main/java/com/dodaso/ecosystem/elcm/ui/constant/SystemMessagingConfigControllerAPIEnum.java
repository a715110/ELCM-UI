package com.dodaso.ecosystem.elcm.ui.constant;

import lombok.Getter;

/**
 * Enum for SystemMessagingConfig Controller API endpoints
 */
@Getter
public enum SystemMessagingConfigControllerAPIEnum {

  systemMessagingConfigController_findAll("/systemMessagingConfigController/findAll"),
  systemMessagingConfigController_findAllActive("/systemMessagingConfigController/findAllActive"),
  systemMessagingConfigController_findByChannelType("/systemMessagingConfigController/findByChannelType"),
  systemMessagingConfigController_findById("/systemMessagingConfigController/findById"),
  systemMessagingConfigController_save("/systemMessagingConfigController/save"),
  systemMessagingConfigController_saveAll("/systemMessagingConfigController/saveAll"),
  systemMessagingConfigController_delete("/systemMessagingConfigController/delete"),
  systemMessagingConfigController_toggleActive("/systemMessagingConfigController/toggleActive"),
  systemMessagingConfigController_testConnection("/systemMessagingConfigController/testConnection"),
  systemMessagingConfigController_initializeDefaults("/systemMessagingConfigController/initializeDefaults"),
  systemMessagingConfigController_resetToDefaults("/systemMessagingConfigController/resetToDefaults");

  private final String endPoint;

  SystemMessagingConfigControllerAPIEnum(String endPoint) {
    this.endPoint = endPoint;
  }

  public String getEndPoint() {
    return endPoint;
  }
}