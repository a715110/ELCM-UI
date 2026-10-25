package com.dodaso.ecosystem.elcm.ui.bean;

import com.dodaso.ecosystem.baseline.common.constant.ServiceDiscoveryEnum;
import com.dodaso.ecosystem.baseline.common.container.RESTReqContainer;
import com.dodaso.ecosystem.baseline.common.proxy.RESTServiceClient;
import com.dodaso.ecosystem.ecws.container.DirectMessageDTOContainer;
import com.dodaso.ecosystem.ecws.dto.DirectMessageDTO;
import com.dodaso.ecosystem.ecws.dto.SendResultDTO;
import com.dodaso.ecosystem.ecws.dto.SystemMessagingConfigDTO;
import com.dodaso.ecosystem.elcm.ui.constant.MessagingChannelControllerAPIEnum;
import com.dodaso.ecosystem.elcm.ui.teams.TeamsTokenStore;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpSession;
import java.io.Serial;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.primefaces.PrimeFaces;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;

/**
 * JSF Managed Bean for sending direct messages to users. Supports multiple messaging channels
 * (Teams, Email, Slack, etc.) based on system configuration.
 */
/**
 * ADDED 2026-10-24 (ported from ECWS). Sends a direct message to a user through ecws-service.
 */
@Named
@jakarta.faces.view.ViewScoped
@Slf4j
@Getter
@Setter
public class DirectMessage implements java.io.Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /** REST client for ecws-service (ADDED for ELCM; ECWS got it from its BaseBean). */
  @Autowired
  private transient RESTServiceClient restServiceClient;

  @Value("${messaging.mock:false}")
  private boolean messagingMock;

  @Value("${messaging.default-channel:}")
  private String defaultChannelOverride;

  /**
   * The message content to send
   */
  private String message;

  /**
   * UPN/email or AAD object id of the recipient
   */
  private String recipientId;

  /**
   * Optional: Specific channel to use (TEAMS, EMAIL, SLACK, etc.) If not specified, the default
   * configured channel will be used
   */
  private String channelType;

  private SystemMessagingConfigDTO defaultConfig;

  @Autowired
  private transient OAuth2AuthorizedClientService authorizedClientService;

  /** ADDED 2026-10-23. Graph tokens of the signed-in person, kept in the HTTP session. */
  @Autowired
  private transient TeamsTokenStore teamsTokenStore;

  /**
   * Send a direct message to the specified recipient. Called from the remoteCommand in
   * template.xhtml
   */
  public void sendDirectMessage() {
    FacesContext faces = FacesContext.getCurrentInstance();

    // Pull params from remoteCommand
    Map<String, String> params = faces.getExternalContext().getRequestParameterMap();
    String reqRecipient = params.get("recipientId");
    String reqMessage = params.get("message");
    String reqChannel = params.get("channelType");

    if (reqRecipient != null) {
      this.recipientId = reqRecipient;
    }
    if (reqMessage != null) {
      this.message = reqMessage;
    }
    if (reqChannel != null) {
      this.channelType = reqChannel;
    }

    // Validate inputs
    if (StringUtils.isBlank(recipientId)) {
      faces.addMessage(null,
          new FacesMessage(FacesMessage.SEVERITY_ERROR, "Recipient is required", null));
      PrimeFaces.current().ajax().addCallbackParam("success", false);
      PrimeFaces.current().ajax().addCallbackParam("detail", "No recipient specified");
      return;
    }

    if (StringUtils.isBlank(message)) {
      faces.addMessage(null,
          new FacesMessage(FacesMessage.SEVERITY_ERROR, "Message is required", null));
      PrimeFaces.current().ajax().addCallbackParam("success", false);
      PrimeFaces.current().ajax().addCallbackParam("detail", "No message content");
      return;
    }

    try {
      // ------- MOCK MODE -------
      if (messagingMock) {
        handleMockMode(faces);
        return;
      }

      // ------- REAL MODE -------
      sendRealMessage(faces);

    } catch (Exception e) {
      log.error("Unexpected error sending message", e);
      faces.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR,
          "Unexpected error: " + e.getMessage(), null));
      PrimeFaces.current().ajax().addCallbackParam("success", false);
      PrimeFaces.current().ajax().addCallbackParam("detail", "Unexpected error");
    }
  }

  /**
   * Handle mock mode for testing
   */
  private void handleMockMode(FacesContext faces) throws Exception {
    String txt = message != null ? message : "";

    if (txt.startsWith("unauth:")) {
      faces.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN,
          "Sign-in required", null));
      PrimeFaces.current().ajax().addCallbackParam("needsLogin", true);
      return;
    }

    if (txt.startsWith("fail:")) {
      faces.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR,
          "Send failed (mock 500)", null));
      PrimeFaces.current().ajax().addCallbackParam("success", false);
      PrimeFaces.current().ajax().addCallbackParam("httpStatus", 500);
      PrimeFaces.current().ajax().addCallbackParam("detail", "mock failure");
      return;
    }

    if (txt.startsWith("slow:")) {
      try {
        Thread.sleep(1500);
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
      }
    }

    // Determine which channel would be used
    String effectiveChannel = determineChannel();

    // Mock success
    faces.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO,
        "Sent to " + recipientId + " via " + effectiveChannel + " (mock)", null));
    PrimeFaces.current().ajax().addCallbackParam("success", true);
    PrimeFaces.current().ajax().addCallbackParam("channel", effectiveChannel);
    message = "";
  }

  /**
   * Send message through the configured channel
   */
  private void sendRealMessage(FacesContext faces) throws Exception {
    // Get current user info
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    String senderLoginId = (auth != null ? auth.getName() : "unknown");

    // Determine which channel to use
    String effectiveChannel = determineChannel();

    if (StringUtils.isBlank(effectiveChannel)) {
      faces.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR,
          "No messaging channel configured", null));
      PrimeFaces.current().ajax().addCallbackParam("success", false);
      PrimeFaces.current().ajax().addCallbackParam("detail", "No channel available");
      return;
    }

    // Get access token if needed for OAuth-based channels
    String accessToken = null;
    if (requiresOAuthToken(effectiveChannel)) {
      accessToken = getAccessToken(auth, effectiveChannel);

      if (accessToken == null && "TEAMS".equalsIgnoreCase(effectiveChannel)) {
        // Teams requires authentication
        faces.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN,
            "Microsoft sign-in required", null));
        PrimeFaces.current().ajax().addCallbackParam("needsLogin", true);
        PrimeFaces.current().ajax().addCallbackParam("loginProvider", "entra");
        return;
      }
    }

    DirectMessageDTO directMessageDTO = new DirectMessageDTO
        (senderLoginId, recipientId, recipientId, message, effectiveChannel, accessToken);

    DirectMessageDTOContainer container = new DirectMessageDTOContainer();
    container.setDirectMessageDTO(directMessageDTO);

    RESTReqContainer<DirectMessageDTOContainer> restReqContainer = new RESTReqContainer<>(
        ServiceDiscoveryEnum.common_service.getServiceDiscoveryName(),
        MessagingChannelControllerAPIEnum.messagingChannelController_sendMessage
            .getEndPoint(),
        container, new ParameterizedTypeReference<>() {
    }, HttpMethod.POST);

    container = restServiceClient.callRESTService(restReqContainer);
    SendResultDTO result = container.getSendResult();

    // Send the message
    //SendResult result = messagingChannelService.sendMessage(
    //    senderLoginId,
    //    recipientId,
    //    recipientId, // For now, assume recipientId could be email
    //    message,
    //    effectiveChannel,
    //    accessToken
    //);

    // Handle result
    handleSendResult(faces, result, effectiveChannel);
  }

  /**
   * Determine which channel to use based on configuration
   */
  private String determineChannel() throws Exception {
    // 1. Use explicitly specified channel if provided
    if (StringUtils.isNotBlank(channelType)) {
      return channelType.toUpperCase();
    }

    // 2. Use override from properties if set
    if (StringUtils.isNotBlank(defaultChannelOverride)) {
      return defaultChannelOverride.toUpperCase();
    }

    // 3. Get default from system configuration
    defaultConfig = restServiceClient.get(
        ServiceDiscoveryEnum.common_service.getServiceDiscoveryName(),
        MessagingChannelControllerAPIEnum.messagingChannelController_defaultChannel
            .getEndPoint(), new ParameterizedTypeReference<>() {
        });

    //SystemMessagingConfig defaultConfig = messagingChannelService.getDefaultChannel();
    if (defaultConfig != null) {
      return defaultConfig.getChannelType();
    }

    // 4. Fallback to EMAIL
    return "EMAIL";
  }

  /**
   * Check if a channel requires OAuth token
   */
  private boolean requiresOAuthToken(String channel) {
    return "TEAMS".equalsIgnoreCase(channel) || "SKYPE".equalsIgnoreCase(channel);
  }

  /**
   * Get OAuth access token for the specified channel
   */
  private String getAccessToken(Authentication auth, String channel) {
    if (auth == null) {
      return null;
    }

    // ADDED 2026-10-23: Teams uses the Connect Microsoft account flow (TeamsConnectController),
    // not a Spring OAuth2 login registration, so the SSO sign-in stays untouched.
    if ("TEAMS".equalsIgnoreCase(channel)) {
      Object session = FacesContext.getCurrentInstance().getExternalContext().getSession(false);
      return teamsTokenStore.accessToken(session instanceof HttpSession http ? http : null);
    }

    String principalName = auth.getName();
    String clientRegistrationId = getOAuthClientId(channel);

    if (clientRegistrationId == null) {
      return null;
    }

    try {
      OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
          clientRegistrationId, principalName);

      if (client == null || client.getAccessToken() == null) {
        return null;
      }

      // Check if token is expired
      if (Instant.now().isAfter(Objects.requireNonNull(client.getAccessToken().getExpiresAt()))) {
        return null;
      }

      return client.getAccessToken().getTokenValue();

    } catch (Exception e) {
      log.error("Error getting access token for {}", channel, e);
      return null;
    }
  }

  /**
   * Get OAuth client registration ID for a channel
   */
  private String getOAuthClientId(String channel) {
    switch (channel.toUpperCase()) {
      case "TEAMS":
      case "SKYPE":
        return "entra"; // Microsoft Entra ID (Azure AD)
      case "SLACK":
        return "slack";
      default:
        return null;
    }
  }

  /**
   * Handle the send result and set appropriate callback params
   */
  private void handleSendResult(FacesContext faces, SendResultDTO result, String channel) {
    if (result.isNeedsLogin()) {
      faces.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN,
          result.getMessage(), null));
      PrimeFaces.current().ajax().addCallbackParam("needsLogin", true);
      PrimeFaces.current().ajax().addCallbackParam("loginProvider", getOAuthClientId(channel));
      return;
    }

    if (result.isSuccess()) {
      faces.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO,
          "Sent to " + recipientId, null));
      PrimeFaces.current().ajax().addCallbackParam("success", true);
      PrimeFaces.current().ajax().addCallbackParam("channel", channel);
      message = ""; // Clear message on success
    } else {
      faces.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR,
          result.getMessage(), null));
      PrimeFaces.current().ajax().addCallbackParam("success", false);
      if (result.getHttpStatus() > 0) {
        PrimeFaces.current().ajax().addCallbackParam("httpStatus", result.getHttpStatus());
      }
      PrimeFaces.current().ajax().addCallbackParam("detail", result.getMessage());
    }
  }

  /**
   * Get information about the current default channel (for UI display)
   */
  public String getDefaultChannelDisplayName() {
    SystemMessagingConfigDTO config = defaultConfig;
    if (config != null) {
      return StringUtils.isNotBlank(config.getDisplayName())
          ? config.getDisplayName()
          : config.getChannelType();
    }
    return "Not configured";
  }

  /**
   * Get the icon for the current default channel
   */
  public String getDefaultChannelIcon() {
    SystemMessagingConfigDTO config = defaultConfig;
    if (config == null) {
      return "pi pi-comment";
    }

    switch (config.getChannelType().toUpperCase()) {
      case "TEAMS":
        return "pi pi-microsoft";
      case "SKYPE":
        return "pi pi-skype";
      case "SLACK":
        return "pi pi-slack";
      case "EMAIL":
        return "pi pi-envelope";
      default:
        return "pi pi-comment";
    }
  }

  /**
   * Check if messaging is available (at least one channel configured)
   */
  public boolean isMessagingAvailable() {
    return defaultConfig != null && defaultConfig.getChannelType() != null;
  }
}