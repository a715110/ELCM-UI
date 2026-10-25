package com.dodaso.ecosystem.elcm.ui.helper;

import com.dodaso.ecosystem.baseline.common.constant.ServiceDiscoveryEnum;
import com.dodaso.ecosystem.baseline.common.proxy.RESTServiceClient;
import com.dodaso.ecosystem.ecws.dto.SystemMessagingConfigDTO;
import com.dodaso.ecosystem.elcm.ui.constant.MessagingChannelControllerAPIEnum;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messaging")
public class MessagingUIHelper {

  @Autowired
  private RESTServiceClient restServiceClient;

  /**
   * ADDED 2026-10-24 (ported from ECWS).
   * How the Message button reaches Teams: {@code link} opens a Teams chat in the
   * browser with the message prefilled (no sign-in, no app registration, the sender sends it
   * there); {@code graph} sends from the server through Microsoft Graph with the user's Entra
   * sign-in (the earlier behaviour, and still the default).
   */
  @Value("${messaging.teams-mode:graph}")
  private String teamsMode;

  /** The Teams chat link without its query. Personal Microsoft accounts may need another host. */
  @Value("${messaging.teams-link-base:https://teams.microsoft.com/l/chat/0/0}")
  private String teamsLinkBase;

  @GetMapping("/settings")
  public ResponseEntity<Map<String, String>> getSettings() {
    final Map<String, String> settings = new HashMap<>();
    settings.put("teamsMode", "link".equalsIgnoreCase(teamsMode.trim()) ? "link" : "graph");
    settings.put("teamsLinkBase", teamsLinkBase.trim());
    return ResponseEntity.ok(settings);
  }

  @GetMapping("/default-channel")
  public ResponseEntity<SystemMessagingConfigDTO> getDefaultChannel() {
    try {
      SystemMessagingConfigDTO result = restServiceClient.get(
          ServiceDiscoveryEnum.common_service.getServiceDiscoveryName(),
          MessagingChannelControllerAPIEnum.messagingChannelController_defaultChannel.getEndPoint(),
          new ParameterizedTypeReference<SystemMessagingConfigDTO>() {
          }
      );

      if (result == null) {
        SystemMessagingConfigDTO fallback = new SystemMessagingConfigDTO();
        fallback.setConfigured(false);
        fallback.setChannelType("EMAIL");
        fallback.setDisplayName("Email");
        return ResponseEntity.ok(fallback);
      }

      return ResponseEntity.ok(result);
    } catch (Exception e) {
      SystemMessagingConfigDTO fallback = new SystemMessagingConfigDTO();
      fallback.setConfigured(false);
      fallback.setChannelType("EMAIL");
      fallback.setDisplayName("Email");
      return ResponseEntity.ok(fallback);
    }
  }
}