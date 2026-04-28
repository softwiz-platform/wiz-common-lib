package org.softwiz.platform.iot.common.lib.stomp;

import lombok.extern.slf4j.Slf4j;
import org.softwiz.platform.iot.common.lib.util.JwtUtil;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.util.Map;

/**
 * STOMP 인증 채널 인터셉터 — JWT 검증만 담당하는 추상 베이스.
 *
 * <p>책임 분리:</p>
 * <ul>
 *   <li><b>이 베이스</b>: STOMP CONNECT 프레임의 {@code Authorization: Bearer <jwt>} 검증.
 *       성공 시 토큰의 클레임(userNo / userId / serviceId / role / nickName)을
 *       세션 attributes 에 저장.</li>
 *   <li><b>SUBSCRIBE / SEND</b>: 세션에 인증 정보가 없으면 차단. 있을 경우 인증된 userNo 를
 *       꺼내서 추상 메서드 {@link #isSubscriptionAllowed} / {@link #isSendAllowed}
 *       로 위임 — <b>토픽별 권한 검증은 각 서비스가 본인 도메인 로직으로 직접 구현</b>.</li>
 * </ul>
 *
 * <p>설계 원칙:</p>
 * <ol>
 *   <li>JWT 검증은 한 곳에서만 (이 베이스). 모든 서비스가 같은 방식으로 검증.</li>
 *   <li>권한 검증은 서비스마다 다름 — 모임 멤버십, 페어링 정보, 채팅방 멤버 등 도메인 데이터 기반.</li>
 *   <li>native userNo 헤더는 절대 신뢰하지 않음. 세션의 토큰 검증 결과만 신뢰.</li>
 * </ol>
 *
 * <p>사용 예 (moi-api):</p>
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class MoiStompInterceptor extends AbstractJwtStompChannelInterceptor {
 *
 *     private final MeetingMapper meetingMapper;
 *
 *     public MoiStompInterceptor(JwtUtil jwtUtil, MeetingMapper meetingMapper) {
 *         super(jwtUtil);
 *         this.meetingMapper = meetingMapper;
 *     }
 *
 *     @Override
 *     protected boolean isSubscriptionAllowed(Long userNo, String destination,
 *                                              StompHeaderAccessor accessor) {
 *         // /topic/meeting/{id}/location 토픽이면 모임 멤버 + locationSharing=true 인지
 *         Matcher m = LOCATION_TOPIC_PATTERN.matcher(destination);
 *         if (m.matches()) {
 *             String meetingId = m.group(1);
 *             MeetingMember member = meetingMapper.selectMember(meetingId, userNo);
 *             return member != null && Boolean.TRUE.equals(member.getLocationSharing());
 *         }
 *         return true; // 그 외는 통과 (정책 따라 false 로 변경 가능)
 *     }
 * }
 * }</pre>
 *
 * <p>그리고 {@code WebSocketConfig} 의 {@code configureClientInboundChannel} 에 등록:</p>
 * <pre>{@code
 * @Override
 * public void configureClientInboundChannel(ChannelRegistration registration) {
 *     registration.interceptors(moiStompInterceptor);
 * }
 * }</pre>
 *
 * <p>컨트롤러에서 인증된 userNo 가 필요할 때 — 정적 헬퍼 사용:</p>
 * <pre>{@code
 * @MessageMapping("/chat/{roomId}/send")
 * public void sendMessage(@DestinationVariable String roomId,
 *                         @Payload SendRequest req,
 *                         SimpMessageHeaderAccessor headers) {
 *     Long senderUserNo = AbstractJwtStompChannelInterceptor.getAuthenticatedUserNo(headers);
 *     // payload 의 senderId 는 신뢰하지 않음 — 세션의 senderUserNo 만 사용
 * }
 * }</pre>
 */
@Slf4j
public abstract class AbstractJwtStompChannelInterceptor implements ChannelInterceptor {

    /** STOMP 세션 attributes 키 — 다른 코드에서 헬퍼로 접근하지 않을 때 raw 접근용. */
    public static final String SESSION_USER_NO    = "STOMP_USER_NO";
    public static final String SESSION_USER_ID    = "STOMP_USER_ID";
    public static final String SESSION_SERVICE_ID = "STOMP_SERVICE_ID";
    public static final String SESSION_ROLE       = "STOMP_ROLE";
    public static final String SESSION_NICK_NAME  = "STOMP_NICK_NAME";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    protected AbstractJwtStompChannelInterceptor(JwtUtil jwtUtil) {
        if (jwtUtil == null) {
            throw new IllegalArgumentException("JwtUtil 가 null 이면 STOMP JWT 인터셉터를 사용할 수 없습니다. application.yml 에 jwt.enabled=true 설정이 되어 있는지 확인하세요.");
        }
        this.jwtUtil = jwtUtil;
    }

    @Override
    public final Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (command == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(command)) {
            return handleConnect(message, accessor);
        }
        if (StompCommand.SUBSCRIBE.equals(command)) {
            return handleSubscribe(message, accessor);
        }
        if (StompCommand.SEND.equals(command)) {
            return handleSend(message, accessor);
        }
        // DISCONNECT / UNSUBSCRIBE / ACK / NACK 등은 통과
        return message;
    }

    // ──────────────── CONNECT ────────────────

    /**
     * CONNECT 프레임 처리 — Authorization 헤더 검증 + 세션 attributes 에 인증 정보 저장.
     * 토큰 없거나 무효 → null 반환 (메시지 차단, 클라이언트는 STOMP ERROR 수신).
     */
    private Message<?> handleConnect(Message<?> message, StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("STOMP CONNECT 거부 — Authorization 헤더 없음/형식 오류");
            return null;
        }
        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            log.warn("STOMP CONNECT 거부 — 빈 토큰");
            return null;
        }

        try {
            if (!jwtUtil.isTokenValid(token)) {
                log.warn("STOMP CONNECT 거부 — 토큰 서명 검증 실패");
                return null;
            }
            if (jwtUtil.isTokenExpired(token)) {
                log.warn("STOMP CONNECT 거부 — 토큰 만료");
                return null;
            }
            Long userNo = jwtUtil.extractUserNo(token);
            if (userNo == null) {
                log.warn("STOMP CONNECT 거부 — userNo 클레임 없음");
                return null;
            }

            Map<String, Object> attrs = accessor.getSessionAttributes();
            if (attrs == null) {
                log.warn("STOMP CONNECT 거부 — 세션 attributes 없음 (handshake 단계 문제)");
                return null;
            }
            attrs.put(SESSION_USER_NO, userNo);
            attrs.put(SESSION_USER_ID, safeExtract(() -> jwtUtil.extractUserId(token)));
            attrs.put(SESSION_SERVICE_ID, safeExtract(() -> jwtUtil.extractServiceId(token)));
            attrs.put(SESSION_ROLE, safeExtract(() -> jwtUtil.extractRole(token)));
            attrs.put(SESSION_NICK_NAME, safeExtract(() -> jwtUtil.extractNickName(token)));

            if (log.isDebugEnabled()) {
                log.debug("STOMP CONNECT 인증 성공 — userNo={}", userNo);
            }
            return message;
        } catch (Exception e) {
            log.warn("STOMP CONNECT 거부 — 토큰 파싱 예외: {}", e.getMessage());
            return null;
        }
    }

    private static <T> T safeExtract(java.util.function.Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            return null;
        }
    }

    // ──────────────── SUBSCRIBE ────────────────

    /**
     * SUBSCRIBE 프레임 처리 — 세션 userNo 추출 → 서비스별 권한 위임.
     * 인증 없거나 권한 없으면 null 반환 (구독 거부).
     */
    private Message<?> handleSubscribe(Message<?> message, StompHeaderAccessor accessor) {
        Long userNo = getAuthenticatedUserNo(accessor);
        if (userNo == null) {
            log.warn("STOMP SUBSCRIBE 거부 — 인증된 사용자 없음 (CONNECT 누락?) destination={}",
                    accessor.getDestination());
            return null;
        }
        String destination = accessor.getDestination();
        if (destination == null) {
            return message;
        }

        try {
            if (!isSubscriptionAllowed(userNo, destination, accessor)) {
                log.warn("STOMP SUBSCRIBE 거부 — 권한 없음 destination={}, userNo={}", destination, userNo);
                return null;
            }
        } catch (Exception e) {
            log.error("STOMP SUBSCRIBE 권한 검증 중 예외 — destination={}, userNo={}",
                    destination, userNo, e);
            return null;
        }
        return message;
    }

    // ──────────────── SEND ────────────────

    /**
     * SEND 프레임 처리 — 세션 userNo 추출 → 서비스별 송신 권한 위임.
     * 기본 동작은 인증만 통과되면 허용 (서비스가 {@link #isSendAllowed} 오버라이드 가능).
     */
    private Message<?> handleSend(Message<?> message, StompHeaderAccessor accessor) {
        Long userNo = getAuthenticatedUserNo(accessor);
        if (userNo == null) {
            log.warn("STOMP SEND 거부 — 인증된 사용자 없음 destination={}", accessor.getDestination());
            return null;
        }
        String destination = accessor.getDestination();
        if (destination == null) {
            return message;
        }

        try {
            if (!isSendAllowed(userNo, destination, accessor)) {
                log.warn("STOMP SEND 거부 — 권한 없음 destination={}, userNo={}", destination, userNo);
                return null;
            }
        } catch (Exception e) {
            log.error("STOMP SEND 권한 검증 중 예외 — destination={}, userNo={}",
                    destination, userNo, e);
            return null;
        }
        return message;
    }

    // ──────────────── 추상 메서드 (서비스 구현) ────────────────

    /**
     * 서비스 도메인 권한 — 토픽 구독 허용 여부.
     *
     * @param userNo      JWT 에서 검증된 사용자 번호 (절대 클라이언트 헤더에서 가져온 값 아님)
     * @param destination 구독 대상 토픽 (예: {@code /topic/meeting/abc/location})
     * @param accessor    추가 헤더가 필요할 때 사용 (예: native 헤더 추가 정보)
     * @return true = 구독 허용 / false = 거부
     */
    protected abstract boolean isSubscriptionAllowed(Long userNo,
                                                     String destination,
                                                     StompHeaderAccessor accessor);

    /**
     * 서비스 도메인 권한 — 메시지 송신(SEND) 허용 여부.
     * 기본은 허용 — 클라이언트가 서버로 SEND 하는 케이스가 있는 서비스(채팅 등)에서 오버라이드.
     *
     * @return true = 송신 허용 / false = 거부
     */
    protected boolean isSendAllowed(Long userNo, String destination, StompHeaderAccessor accessor) {
        return true;
    }

    // ──────────────── 정적 헬퍼 ────────────────

    /**
     * 채널 인터셉터 단계에서 세션 userNo 꺼내기.
     */
    public static Long getAuthenticatedUserNo(StompHeaderAccessor accessor) {
        if (accessor == null) return null;
        return readUserNoFromAttrs(accessor.getSessionAttributes());
    }

    /**
     * {@code @MessageMapping} 컨트롤러에서 세션 userNo 꺼내기.
     * 사용법: 컨트롤러 메서드 시그니처에 {@code SimpMessageHeaderAccessor headers} 추가.
     */
    public static Long getAuthenticatedUserNo(SimpMessageHeaderAccessor accessor) {
        if (accessor == null) return null;
        return readUserNoFromAttrs(accessor.getSessionAttributes());
    }

    public static String getAuthenticatedUserId(StompHeaderAccessor accessor) {
        if (accessor == null) return null;
        Map<String, Object> attrs = accessor.getSessionAttributes();
        return attrs != null ? (String) attrs.get(SESSION_USER_ID) : null;
    }

    public static String getAuthenticatedServiceId(StompHeaderAccessor accessor) {
        if (accessor == null) return null;
        Map<String, Object> attrs = accessor.getSessionAttributes();
        return attrs != null ? (String) attrs.get(SESSION_SERVICE_ID) : null;
    }

    public static String getAuthenticatedRole(StompHeaderAccessor accessor) {
        if (accessor == null) return null;
        Map<String, Object> attrs = accessor.getSessionAttributes();
        return attrs != null ? (String) attrs.get(SESSION_ROLE) : null;
    }

    public static String getAuthenticatedNickName(StompHeaderAccessor accessor) {
        if (accessor == null) return null;
        Map<String, Object> attrs = accessor.getSessionAttributes();
        return attrs != null ? (String) attrs.get(SESSION_NICK_NAME) : null;
    }

    private static Long readUserNoFromAttrs(Map<String, Object> attrs) {
        if (attrs == null) return null;
        Object raw = attrs.get(SESSION_USER_NO);
        if (raw instanceof Long) return (Long) raw;
        if (raw instanceof Number) return ((Number) raw).longValue();
        return null;
    }
}
