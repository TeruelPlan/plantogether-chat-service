package com.plantogether.chat.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.plantogether.common.security.SecurityConstants;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

/**
 * Unit tests for DeviceIdChannelInterceptor (R04 — CRITICAL security gap).
 *
 * <p>Acceptance criteria: - STOMP CONNECT with valid X-Device-Id UUID → principal set on accessor -
 * STOMP CONNECT without X-Device-Id → principal remains null (broker rejects unauthenticated) -
 * STOMP CONNECT with invalid UUID format → principal remains null - Non-CONNECT frames are not
 * modified
 *
 * <p>TDD RED PHASE: Remove @Disabled once verified passing. These tests exercise existing
 * implementation — expected to pass after enabling.
 */
@ExtendWith(MockitoExtension.class)
class DeviceIdChannelInterceptorTest {

  private DeviceIdChannelInterceptor interceptor;
  private MessageChannel channel;

  @BeforeEach
  void setUp() {
    interceptor = new DeviceIdChannelInterceptor();
    channel = mock(MessageChannel.class);
  }

  // -------------------------------------------------------------------------
  // R04-1: Valid UUID → principal set
  // -------------------------------------------------------------------------

  @Test
  void connectWithValidDeviceId_setsPrincipalOnAccessor() {
    UUID deviceId = UUID.randomUUID();
    Message<byte[]> message = buildStompConnect(deviceId.toString());

    Message<?> result = interceptor.preSend(message, channel);

    StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
    assertThat(resultAccessor.getUser()).isNotNull();
    assertThat(resultAccessor.getUser().getName()).isEqualTo(deviceId.toString());
  }

  // -------------------------------------------------------------------------
  // R04-2: Missing header → user remains null
  // -------------------------------------------------------------------------

  @Test
  void connectWithMissingDeviceIdHeader_principalRemainsNull() {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
    Message<byte[]> message =
        MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

    Message<?> result = interceptor.preSend(message, channel);

    StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
    assertThat(resultAccessor.getUser()).isNull();
  }

  // -------------------------------------------------------------------------
  // R04-3: Invalid UUID format → user remains null
  // -------------------------------------------------------------------------

  @Test
  void connectWithInvalidUuidFormat_principalRemainsNull() {
    Message<byte[]> message = buildStompConnect("not-a-valid-uuid");

    Message<?> result = interceptor.preSend(message, channel);

    StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
    assertThat(resultAccessor.getUser()).isNull();
  }

  // -------------------------------------------------------------------------
  // R04-4: Blank header value → user remains null
  // -------------------------------------------------------------------------

  @Test
  void connectWithBlankDeviceIdHeader_principalRemainsNull() {
    Message<byte[]> message = buildStompConnect("   ");

    Message<?> result = interceptor.preSend(message, channel);

    StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
    assertThat(resultAccessor.getUser()).isNull();
  }

  // -------------------------------------------------------------------------
  // R04-5: Non-CONNECT frame is passed through unmodified
  // -------------------------------------------------------------------------

  @Test
  void sendFrame_isPassedThroughWithoutModification() {
    UUID deviceId = UUID.randomUUID();
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
    accessor.addNativeHeader(SecurityConstants.DEVICE_ID_HEADER, deviceId.toString());
    accessor.setLeaveMutable(true);
    Message<byte[]> message =
        MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

    Message<?> result = interceptor.preSend(message, channel);

    StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
    // SEND frames are not CONNECT — interceptor must not set user on them
    assertThat(resultAccessor.getUser()).isNull();
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private Message<byte[]> buildStompConnect(String deviceIdHeaderValue) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
    accessor.addNativeHeader(SecurityConstants.DEVICE_ID_HEADER, deviceIdHeaderValue);
    accessor.setLeaveMutable(true); // keep headers mutable so the interceptor can call setUser()
    return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
  }
}
