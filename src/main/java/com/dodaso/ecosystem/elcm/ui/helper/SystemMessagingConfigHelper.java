package com.dodaso.ecosystem.elcm.ui.helper;

import com.dodaso.ecosystem.baseline.common.constant.ServiceDiscoveryEnum;
import com.dodaso.ecosystem.baseline.common.container.RESTReqContainer;
import com.dodaso.ecosystem.baseline.common.proxy.RESTServiceClient;
import com.dodaso.ecosystem.ecws.container.SystemMessagingConfigDTOContainer;
import com.dodaso.ecosystem.ecws.dto.SystemMessagingConfigDTO;
import com.dodaso.ecosystem.elcm.ui.constant.SystemMessagingConfigControllerAPIEnum;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;

/**
 * Helper bean for managing system-wide messaging configurations.
 * Used by administrators to configure messaging channels (Teams, Skype, Slack, Email, etc.)
 *
 * <p>ADDED 2026-10-26 - copy of the ECWS UI helper for the ELCM Configure Messaging page. The
 * settings are stored once in common-service and are shared by ECWS and ELCM.
 */
@Data
@Slf4j
@ViewScoped
@Named
public class SystemMessagingConfigHelper implements Serializable {

  private static final long serialVersionUID = 1L;

  @Autowired
  protected RESTServiceClient restServiceClient;

  // List of all messaging configurations
  private List<SystemMessagingConfigDTO> configurations = new ArrayList<>();

  // Selected configuration for editing in dialog
  private SystemMessagingConfigDTO selectedConfig;

  // Flag to control dialog visibility
  private boolean showConfigDialog = false;

  // Available channel types with metadata
  private static final Map<String, ChannelTypeInfo> CHANNEL_TYPE_INFO = new HashMap<>();

  static {
    CHANNEL_TYPE_INFO.put("TEAMS", new ChannelTypeInfo("Microsoft Teams", "pi pi-microsoft", "#6264A7"));
    CHANNEL_TYPE_INFO.put("SKYPE", new ChannelTypeInfo("Skype", "pi pi-skype", "#00AFF0"));
    CHANNEL_TYPE_INFO.put("SLACK", new ChannelTypeInfo("Slack", "pi pi-slack", "#4A154B"));
    CHANNEL_TYPE_INFO.put("ZOOM", new ChannelTypeInfo("Zoom", "pi pi-video", "#2D8CFF"));
    CHANNEL_TYPE_INFO.put("EMAIL", new ChannelTypeInfo("Email", "pi pi-envelope", "#EA4335"));
  }

  @PostConstruct
  public void init() {
    log.debug("Initializing SystemMessagingConfigHelper");
    loadConfigurations();
  }

  /**
   * Load all messaging configurations from backend
   */
  public void loadConfigurations() {
    try {
      SystemMessagingConfigDTOContainer container = restServiceClient.get(
          ServiceDiscoveryEnum.common_service.getServiceDiscoveryName(),
          SystemMessagingConfigControllerAPIEnum.systemMessagingConfigController_findAll.getEndPoint(),
          new ParameterizedTypeReference<>() {
          });

      configurations = container.getSystemMessagingConfigDTOS();

      if (configurations == null || configurations.isEmpty()) {
        // Initialize defaults if no configurations exist
        initializeDefaults();
      }

      log.debug("Loaded {} messaging configurations",
          configurations != null ? configurations.size() : 0);

    } catch (Exception e) {
      log.error("Error loading messaging configurations", e);
      configurations = new ArrayList<>();
      addErrorMessage("Error loading messaging configurations: " + e.getMessage());
    }
  }

  /**
   * Initialize default configurations for all channel types
   */
  public void initializeDefaults() {
    try {
      SystemMessagingConfigDTOContainer container = new SystemMessagingConfigDTOContainer();

      RESTReqContainer<SystemMessagingConfigDTOContainer> restReqContainer =
          new RESTReqContainer<>(
              ServiceDiscoveryEnum.common_service.getServiceDiscoveryName(),
              SystemMessagingConfigControllerAPIEnum
                  .systemMessagingConfigController_initializeDefaults.getEndPoint(),
              container,
              new ParameterizedTypeReference<SystemMessagingConfigDTOContainer>() {
              },
              HttpMethod.POST
          );

      container = restServiceClient.callRESTService(restReqContainer);

      // Reload configurations
      loadConfigurations();

      log.info("Initialized default messaging configurations");

    } catch (Exception e) {
      log.error("Error initializing default configurations", e);
      addErrorMessage("Error initializing defaults: " + e.getMessage());
    }
  }

  /**
   * Handle toggle of active status
   */
  public void onToggleActive(SystemMessagingConfigDTO config) {
    try {
      // If enabling, check if configuration is complete
      if (Boolean.TRUE.equals(config.getActiveInd()) && !isConfigured(config)) {
        addInfoMessage("Please configure " + getDisplayName(config.getChannelType()) +
            " settings before enabling.");
      }

      // Save the change immediately
      saveConfig(config);

      log.debug("Toggled active status for {}: {}",
          config.getChannelType(), config.getActiveInd());

    } catch (Exception e) {
      log.error("Error toggling active status", e);
      addErrorMessage("Error updating status: " + e.getMessage());
    }
  }

  /**
   * Open configuration dialog for a specific channel
   */
  public void openConfigDialog(SystemMessagingConfigDTO config) {
    this.selectedConfig = config;
    this.showConfigDialog = true;
    log.debug("Opened config dialog for: {}", config.getChannelType());
  }

  /**
   * Close configuration dialog
   */
  public void closeConfigDialog() {
    this.showConfigDialog = false;
    this.selectedConfig = null;
  }

  /**
   * Check if a configuration has all required fields populated
   */
  public boolean isConfigured(SystemMessagingConfigDTO config) {
    if (config == null || StringUtils.isBlank(config.getChannelType())) {
      return false;
    }

    switch (config.getChannelType().toUpperCase()) {
      case "TEAMS":
        return StringUtils.isNotBlank(config.getTenantId()) &&
            StringUtils.isNotBlank(config.getClientId());

      case "SKYPE":
        return StringUtils.isNotBlank(config.getClientId());

      case "SLACK":
        return StringUtils.isNotBlank(config.getTenantId()) &&
            StringUtils.isNotBlank(config.getClientId()) &&
            StringUtils.isNotBlank(config.getWebhookUrl());

      case "ZOOM":
        return StringUtils.isNotBlank(config.getClientId()) &&
            StringUtils.isNotBlank(config.getTenantId());

      case "EMAIL":
        return StringUtils.isNotBlank(config.getSmtpHost()) &&
            config.getSmtpPort() != null &&
            StringUtils.isNotBlank(config.getEmailFromAddress());

      default:
        return false;
    }
  }

  /**
   * Get configuration status label
   */
  public String getStatusLabel(SystemMessagingConfigDTO config) {
    if (!Boolean.TRUE.equals(config.getActiveInd())) {
      return "Disabled";
    }
    return isConfigured(config) ? "Configured" : "Not Configured";
  }

  /**
   * Get configuration status severity for p:tag
   */
  public String getStatusSeverity(SystemMessagingConfigDTO config) {
    if (!Boolean.TRUE.equals(config.getActiveInd())) {
      return "info";
    }
    return isConfigured(config) ? "success" : "warning";
  }

  /**
   * Save a single configuration
   */
  public void saveConfig(SystemMessagingConfigDTO config) {
    try {
      SystemMessagingConfigDTOContainer container = new SystemMessagingConfigDTOContainer();
      container.setSystemMessagingConfigDTO(config);

      RESTReqContainer<SystemMessagingConfigDTOContainer> restReqContainer =
          new RESTReqContainer<>(
              ServiceDiscoveryEnum.common_service.getServiceDiscoveryName(),
              SystemMessagingConfigControllerAPIEnum
                  .systemMessagingConfigController_save.getEndPoint(),
              container,
              new ParameterizedTypeReference<SystemMessagingConfigDTOContainer>() {
              },
              HttpMethod.POST
          );

      container = restServiceClient.callRESTService(restReqContainer);

      // Update local list
      SystemMessagingConfigDTO savedConfig = container.getSystemMessagingConfigDTO();
      updateLocalConfig(savedConfig);

      addSuccessMessage(getDisplayName(config.getChannelType()) + " configuration saved.");
      log.info("Saved configuration for: {}", config.getChannelType());

    } catch (Exception e) {
      log.error("Error saving configuration", e);
      addErrorMessage("Error saving configuration: " + e.getMessage());
    }
  }

  /**
   * Save selected configuration from dialog
   */
  public void saveSelectedConfig() {
    if (selectedConfig != null) {
      saveConfig(selectedConfig);
      closeConfigDialog();
    }
  }

  /**
   * Save all configurations
   */
  public void saveAll() {
    try {
      SystemMessagingConfigDTOContainer container = new SystemMessagingConfigDTOContainer();
      container.setSystemMessagingConfigDTOS(configurations);

      RESTReqContainer<SystemMessagingConfigDTOContainer> restReqContainer =
          new RESTReqContainer<>(
              ServiceDiscoveryEnum.common_service.getServiceDiscoveryName(),
              SystemMessagingConfigControllerAPIEnum
                  .systemMessagingConfigController_saveAll.getEndPoint(),
              container,
              new ParameterizedTypeReference<SystemMessagingConfigDTOContainer>() {
              },
              HttpMethod.POST
          );

      container = restServiceClient.callRESTService(restReqContainer);
      configurations = container.getSystemMessagingConfigDTOS();

      addSuccessMessage("All messaging configurations saved successfully.");
      log.info("Saved all messaging configurations");

    } catch (Exception e) {
      log.error("Error saving all configurations", e);
      addErrorMessage("Error saving configurations: " + e.getMessage());
    }
  }

  /**
   * Test connection for a configuration
   */
  public void testConnection(SystemMessagingConfigDTO config) {
    try {
      SystemMessagingConfigDTOContainer container = new SystemMessagingConfigDTOContainer();
      container.setSystemMessagingConfigDTO(config);

      RESTReqContainer<SystemMessagingConfigDTOContainer> restReqContainer =
          new RESTReqContainer<>(
              ServiceDiscoveryEnum.common_service.getServiceDiscoveryName(),
              SystemMessagingConfigControllerAPIEnum
                  .systemMessagingConfigController_testConnection.getEndPoint(),
              container,
              new ParameterizedTypeReference<SystemMessagingConfigDTOContainer>() {
              },
              HttpMethod.POST
          );

      container = restServiceClient.callRESTService(restReqContainer);

      String message = container.getResultMessage();
      if (StringUtils.isNotBlank(message)) {
        if (message.toLowerCase().contains("successful") ||
            message.toLowerCase().contains("validated")) {
          addSuccessMessage(message);
        } else {
          addWarningMessage(message);
        }
      }

      log.info("Connection test completed for: {}", config.getChannelType());

    } catch (Exception e) {
      log.error("Error testing connection", e);
      addErrorMessage("Connection test failed: " + e.getMessage());
    }
  }

  /**
   * Reset all configurations to defaults
   */
  public void resetToDefaults() {
    try {
      SystemMessagingConfigDTOContainer container = new SystemMessagingConfigDTOContainer();

      RESTReqContainer<SystemMessagingConfigDTOContainer> restReqContainer =
          new RESTReqContainer<>(
              ServiceDiscoveryEnum.common_service.getServiceDiscoveryName(),
              SystemMessagingConfigControllerAPIEnum
                  .systemMessagingConfigController_resetToDefaults.getEndPoint(),
              container,
              new ParameterizedTypeReference<SystemMessagingConfigDTOContainer>() {
              },
              HttpMethod.POST
          );

      container = restServiceClient.callRESTService(restReqContainer);

      // Reload configurations
      loadConfigurations();

      addSuccessMessage("All messaging configurations have been reset to defaults.");
      log.info("Reset messaging configurations to defaults");

    } catch (Exception e) {
      log.error("Error resetting to defaults", e);
      addErrorMessage("Error resetting configurations: " + e.getMessage());
    }
  }

  /**
   * Update local configuration list after save
   */
  private void updateLocalConfig(SystemMessagingConfigDTO savedConfig) {
    for (int i = 0; i < configurations.size(); i++) {
      if (configurations.get(i).getChannelType().equals(savedConfig.getChannelType())) {
        configurations.set(i, savedConfig);
        return;
      }
    }
    // Not found, add to list
    configurations.add(savedConfig);
  }

  /**
   * Get display name for channel type
   */
  public String getDisplayName(String channelType) {
    ChannelTypeInfo info = CHANNEL_TYPE_INFO.get(channelType);
    return info != null ? info.displayName : channelType;
  }

  /**
   * Get icon class for channel type
   */
  public String getIcon(String channelType) {
    ChannelTypeInfo info = CHANNEL_TYPE_INFO.get(channelType);
    return info != null ? info.icon : "pi pi-question";
  }

  /**
   * Get brand color for channel type
   */
  public String getBrandColor(String channelType) {
    ChannelTypeInfo info = CHANNEL_TYPE_INFO.get(channelType);
    return info != null ? info.brandColor : "#666666";
  }

  /**
   * Check if channel is Teams
   */
  public boolean isTeams(SystemMessagingConfigDTO config) {
    return config != null && "TEAMS".equalsIgnoreCase(config.getChannelType());
  }

  /**
   * Check if channel is Skype
   */
  public boolean isSkype(SystemMessagingConfigDTO config) {
    return config != null && "SKYPE".equalsIgnoreCase(config.getChannelType());
  }

  /**
   * Check if channel is Slack
   */
  public boolean isSlack(SystemMessagingConfigDTO config) {
    return config != null && "SLACK".equalsIgnoreCase(config.getChannelType());
  }

  /**
   * Check if channel is Zoom
   */
  public boolean isZoom(SystemMessagingConfigDTO config) {
    return config != null && "ZOOM".equalsIgnoreCase(config.getChannelType());
  }

  /**
   * Check if channel is Email
   */
  public boolean isEmail(SystemMessagingConfigDTO config) {
    return config != null && "EMAIL".equalsIgnoreCase(config.getChannelType());
  }

  // ========== Helper Methods for Messages ==========

  private void addSuccessMessage(String message) {
    FacesContext.getCurrentInstance().addMessage(null,
        new FacesMessage(FacesMessage.SEVERITY_INFO, "Success", message));
  }

  private void addInfoMessage(String message) {
    FacesContext.getCurrentInstance().addMessage(null,
        new FacesMessage(FacesMessage.SEVERITY_INFO, "Info", message));
  }

  private void addWarningMessage(String message) {
    FacesContext.getCurrentInstance().addMessage(null,
        new FacesMessage(FacesMessage.SEVERITY_WARN, "Warning", message));
  }

  private void addErrorMessage(String message) {
    FacesContext.getCurrentInstance().addMessage(null,
        new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error", message));
  }

  // ========== Inner class for channel type metadata ==========

  public static class ChannelTypeInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String displayName;
    public final String icon;
    public final String brandColor;

    public ChannelTypeInfo(String displayName, String icon, String brandColor) {
      this.displayName = displayName;
      this.icon = icon;
      this.brandColor = brandColor;
    }
  }
}