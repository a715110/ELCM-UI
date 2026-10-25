package com.dodaso.ecosystem.elcm.ui.constant;

import lombok.Getter;

@Getter
public enum MessagingChannelControllerAPIEnum {
  messagingChannelController_sendMessage("/messagingChannelController/sendMessage"),
  messagingChannelController_defaultChannel("/messagingChannelController/defaultChannel"),
  messagingChannelController_activeChannels("/messagingChannelController/activeChannels"),  // Add this
  messagingChannelController_status("/messagingChannelController/status");

  final String endPoint;
  MessagingChannelControllerAPIEnum(String endPoint) {
    this.endPoint = endPoint;
  }
}