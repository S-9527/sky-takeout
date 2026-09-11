package com.sky.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.sky.common.error.BusinessException;
import com.sky.common.util.Json;
import com.sky.security.Audience;
import com.sky.security.TokenService;

/**
 * 管理端通知 WebSocket(契约 §4):{@code /ws/admin/notifications?token=<accessToken>}。
 *
 * <p>鉴权放在**连接建立之后**用关闭码表达,而不是在握手里返回 401:契约规定的失败信号是
 * `4401`(令牌缺失/无效)与 `4403`(受众不匹配),这两个码只有连接建立之后才发得出去。
 *
 * <p>会话表只在**本实例**内,跨实例靠 Redis Pub/Sub({@link AdminNotificationService} 发、
 * {@link NotificationSubscriber} 收)广播——契约 §4.1 的"多端/集群"要求。
 */
@Component
public class AdminNotificationWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AdminNotificationWebSocketHandler.class);

    static final int CLOSE_UNAUTHORIZED = 4401;
    static final int CLOSE_FORBIDDEN = 4403;

    /** 契约 §4.3:90 秒未收到任何帧则关闭。 */
    static final long IDLE_TIMEOUT_MILLIS = 90_000L;

    private final TokenService tokenService;

    /** sessionId → 会话上下文。 */
    private final Map<String, SessionContext> sessions = new ConcurrentHashMap<>();

    public AdminNotificationWebSocketHandler(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String token = tokenOf(session);
        if (token == null || token.isBlank()) {
            close(session, CLOSE_UNAUTHORIZED);
            return;
        }
        try {
            TokenService.TokenPayload payload = tokenService.verifyAccess(token);
            if (payload.audience() != Audience.ADMIN) {
                // 顾客令牌连管理端通知:受众不匹配,重连也没用
                close(session, CLOSE_FORBIDDEN);
                return;
            }
            sessions.put(session.getId(), new SessionContext(payload.subjectId(), session));
            log.debug("管理端通知连接建立:employeeId={} sessionId={}", payload.subjectId(), session.getId());
        } catch (BusinessException ex) {
            close(session, CLOSE_UNAUTHORIZED);
        }
    }

    /** 客户端只发 PING(契约 §4.3),服务端立即回 PONG。 */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        touch(session);
        String type = typeOf(message.getPayload());
        if ("PING".equals(type)) {
            send(session, AdminNotificationService.envelope("PONG",
                    Map.of("serverTime", java.time.OffsetDateTime.now(com.sky.common.util.Times.ZONE)
                            .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME))));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    /** 心跳超时清理:定时任务每 30 秒扫一遍,超时按契约用 1000 正常关闭(客户端指数退避重连)。 */
    @Scheduled(fixedDelayString = "PT30S")
    public void closeIdleSessions() {
        long deadline = System.currentTimeMillis() - IDLE_TIMEOUT_MILLIS;
        sessions.values().stream()
                .filter(context -> context.lastSeenAt() < deadline)
                .forEach(context -> close(context.session(), CloseStatus.NORMAL.getCode()));
    }

    /** 把信封原样推给本实例持有的所有管理端会话。 */
    public void broadcastLocal(String envelopeJson) {
        for (SessionContext context : sessions.values()) {
            send(context.session(), envelopeJson);
        }
    }

    /** 当前实例持有的会话数(测试与运维观察用)。 */
    public int localSessionCount() {
        return sessions.size();
    }

    private void touch(WebSocketSession session) {
        SessionContext context = sessions.get(session.getId());
        if (context != null) {
            context.touch();
        }
    }

    private static void send(WebSocketSession session, String payload) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(payload));
            }
        } catch (IOException ex) {
            log.warn("推送失败,关闭会话:{}", session.getId(), ex);
            close(session, CloseStatus.SERVER_ERROR.getCode());
        }
    }

    private static void close(WebSocketSession session, int code) {
        try {
            if (session.isOpen()) {
                session.close(new CloseStatus(code));
            }
        } catch (IOException ex) {
            log.debug("关闭会话失败:{}", session.getId(), ex);
        }
    }

    private static String tokenOf(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null || uri.getQuery() == null) {
            return null;
        }
        for (String pair : uri.getQuery().split("&")) {
            int index = pair.indexOf('=');
            if (index > 0 && "token".equals(pair.substring(0, index))) {
                return java.net.URLDecoder.decode(pair.substring(index + 1), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static String typeOf(String payload) {
        try {
            Object type = Json.readMap(payload).get("type");
            return type == null ? null : String.valueOf(type);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** 会话上下文:员工 id + 最近一次收到帧的时间(心跳超时判定)。 */
    private static final class SessionContext {

        private final Long employeeId;
        private final WebSocketSession session;
        private volatile long lastSeenAt = System.currentTimeMillis();

        private SessionContext(Long employeeId, WebSocketSession session) {
            this.employeeId = employeeId;
            this.session = session;
        }

        private WebSocketSession session() {
            return session;
        }

        private long lastSeenAt() {
            return lastSeenAt;
        }

        private void touch() {
            this.lastSeenAt = System.currentTimeMillis();
        }
    }
}
