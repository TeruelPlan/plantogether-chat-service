package com.plantogether.chat.security;

import com.plantogether.common.security.SecurityConstants;
import java.util.Collections;
import java.util.UUID;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class DeviceIdChannelInterceptor implements ChannelInterceptor {

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
      String deviceIdHeader = accessor.getFirstNativeHeader(SecurityConstants.DEVICE_ID_HEADER);
      if (deviceIdHeader != null && !deviceIdHeader.isBlank()) {
        try {
          UUID deviceId = UUID.fromString(deviceIdHeader.trim());
          accessor.setUser(
              new UsernamePasswordAuthenticationToken(
                  deviceId.toString(), null, Collections.emptyList()));
        } catch (IllegalArgumentException ignored) {
          // Invalid UUID — leave principal unset; broker will reject unauthenticated CONNECT
        }
      }
    }
    return message;
  }
}
