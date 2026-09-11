package com.sky.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;

import com.sky.common.error.BusinessException;
import com.sky.security.Audience;
import com.sky.security.AuthErrorCode;
import com.sky.security.TokenService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端通知的握手后鉴权:契约 §4.1 用**关闭码**表达失败(4401/4403),
 * 而不是握手阶段的 HTTP 401/403——浏览器拿不到握手失败的原因。
 */
class AdminNotificationWebSocketHandlerTest {

    private final TokenService tokenService = mock(TokenService.class);
    private final AdminNotificationWebSocketHandler handler =
            new AdminNotificationWebSocketHandler(tokenService);

    private WebSocketSession session;

    @BeforeEach
    void setUp() {
        session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("s-1");
        when(session.isOpen()).thenReturn(true);
    }

    private void connectWith(String token) {
        when(session.getUri()).thenReturn(URI.create("ws://localhost/ws/admin/notifications?token=" + token));
        handler.afterConnectionEstablished(session);
    }

    @Test
    void adminTokenIsAcceptedAndRegistered() throws Exception {
        when(tokenService.verifyAccess("admin-token"))
                .thenReturn(new TokenService.TokenPayload(1L, Audience.ADMIN, "STAFF"));

        connectWith("admin-token");

        assertThat(handler.localSessionCount()).isEqualTo(1);
        verify(session, never()).close(any(CloseStatus.class));
    }

    @Test
    void missingTokenClosesWith4401() throws Exception {
        when(session.getUri()).thenReturn(URI.create("ws://localhost/ws/admin/notifications"));

        handler.afterConnectionEstablished(session);

        ArgumentCaptor<CloseStatus> status = ArgumentCaptor.forClass(CloseStatus.class);
        verify(session).close(status.capture());
        assertThat(status.getValue().getCode()).isEqualTo(4401);
        assertThat(handler.localSessionCount()).isZero();
    }

    @Test
    void invalidTokenClosesWith4401() throws Exception {
        when(tokenService.verifyAccess("bad")).thenThrow(new BusinessException(AuthErrorCode.AUTH_TOKEN_INVALID));

        connectWith("bad");

        ArgumentCaptor<CloseStatus> status = ArgumentCaptor.forClass(CloseStatus.class);
        verify(session).close(status.capture());
        assertThat(status.getValue().getCode()).isEqualTo(4401);
    }

    /** 顾客令牌连管理端通知:受众不匹配,重连也没用 → 4403。 */
    @Test
    void customerTokenClosesWith4403() throws Exception {
        when(tokenService.verifyAccess("customer-token"))
                .thenReturn(new TokenService.TokenPayload(9L, Audience.CUSTOMER, "CUSTOMER"));

        connectWith("customer-token");

        ArgumentCaptor<CloseStatus> status = ArgumentCaptor.forClass(CloseStatus.class);
        verify(session).close(status.capture());
        assertThat(status.getValue().getCode()).isEqualTo(4403);
    }

    @Test
    void pingIsAnsweredWithPong() throws Exception {
        when(tokenService.verifyAccess("admin-token"))
                .thenReturn(new TokenService.TokenPayload(1L, Audience.ADMIN, "ADMIN"));
        connectWith("admin-token");

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"PING\"}"));

        ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(sent.capture());
        assertThat(sent.getValue().getPayload()).contains("\"type\":\"PONG\"").contains("serverTime");
    }

    @Test
    void broadcastReachesLocalSessions() throws Exception {
        when(tokenService.verifyAccess("admin-token"))
                .thenReturn(new TokenService.TokenPayload(1L, Audience.ADMIN, "ADMIN"));
        connectWith("admin-token");

        handler.broadcastLocal(AdminNotificationService.envelope("ORDER_NEW", java.util.Map.of("orderId", 4001L)));

        ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(sent.capture());
        assertThat(sent.getValue().getPayload()).contains("ORDER_NEW").contains("4001");
    }

    @Test
    void idleSweepKeepsFreshSessionsAndDropsClosedOnes() throws Exception {
        when(tokenService.verifyAccess("admin-token"))
                .thenReturn(new TokenService.TokenPayload(1L, Audience.ADMIN, "ADMIN"));
        connectWith("admin-token");

        handler.closeIdleSessions();
        assertThat(handler.localSessionCount()).isEqualTo(1);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        assertThat(handler.localSessionCount()).isZero();
    }

    @Test
    void unknownMessageTypeIsIgnored() throws Exception {
        when(tokenService.verifyAccess("admin-token"))
                .thenReturn(new TokenService.TokenPayload(1L, Audience.ADMIN, "ADMIN"));
        connectWith("admin-token");

        handler.handleTextMessage(session, new TextMessage("not-json"));

        verify(session, never()).sendMessage(any(TextMessage.class));
    }
}
