package org.softwiz.platform.iot.common.lib.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.List;

/**
 * 게이트웨이에서 전달받은 사용자 정보를 담는 컨텍스트
 *
 * ThreadLocal로 관리하여 현재 요청 스레드 내에서 어디서든 접근 가능
 *
 * 사용 시나리오:
 * 1. GatewayHeaderInterceptor에서 헤더 파싱 후 setContext() 호출
 * 2. Service, Controller 등에서 getCurrent()로 사용자 정보 접근
 * 3. 요청 완료 후 afterCompletion에서 clear() 호출 (메모리 누수 방지)
 *
 * 보안 주의사항:
 * - userId는 복호화된 평문 (예: user@example.com)
 * - accessToken은 JWT 토큰 (마이크로서비스 간 전파용)
 * - 로그에 출력 시 마스킹 필수
 * - 외부 API 응답에 포함 금지
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayContext {
    /**
     * 사용자 번호 (시스템 내부 식별자)
     * 평문 전달 - 민감정보 아님
     */
    private Long userNo;

    /**
     * 사용자 ID (복호화된 평문)
     * 보안: 로그 출력 시 마스킹 필수
     */
    private String userId;

    /**
     * 서비스 ID (예: NEST, COMMON 등)
     */
    private String serviceId;

    /**
     * 역할 (예: USER, ADMIN, SERVICE_ADMIN 등)
     */
    private String role;

    /**
     * 복합 권한 리스트
     * Gateway로부터 X-Auth 헤더를 통해 JSON 배열로 전달받음
     * JWT의 auth 클레임과 타입 일관성 유지
     */
    private List<String> auth;

    /**
     * SNS 제공자 (예: EMAIL, KAKAO, NAVER 등)
     */
    private String provider;

    /**
     * 닉네임 (공개 정보)
     */
    private String nickName;

    /**
     * 클라이언트 IP
     */
    private String clientIp;

    /**
     * 디바이스 코드 (예: ANDROID, IOS 등)
     */
    private String deviceCd;

    /**
     * 디바이스 상세 정보
     */
    private String deviceStr;

    /**
     * JWT Access Token (마이크로서비스 간 전파용)
     *
     * 용도:
     * - 마이크로서비스 간 내부 호출 시 Authorization 헤더 전달
     * - Gateway 검증을 통과한 유효한 토큰만 저장됨
     *
     * 보안:
     * - ThreadLocal로 요청별 격리
     * - 요청 종료 시 자동 삭제 (clear() 호출)
     * - 로그 출력 시 절대 전체 토큰 노출 금지
     * - DB나 외부 저장소에 저장 금지
     */
    private String accessToken;

    /**
     * ThreadLocal 컨텍스트 홀더
     */
    private static final ThreadLocal<GatewayContext> contextHolder = new ThreadLocal<>();

    /**
     * 현재 스레드의 컨텍스트 설정
     *
     * @param context Gateway에서 전달받은 사용자 정보
     */
    public static void setContext(GatewayContext context) {
        contextHolder.set(context);
    }

    /**
     * 현재 스레드의 컨텍스트 가져오기
     *
     * @return 현재 요청의 Gateway 컨텍스트 (없으면 null)
     */
    public static GatewayContext getContext() {
        return contextHolder.get();
    }

    /**
     * 현재 스레드의 컨텍스트 가져오기 (alias)
     *
     * @return 현재 요청의 Gateway 컨텍스트 (없으면 null)
     */
    public static GatewayContext getCurrent() {
        return getContext();
    }

    /**
     * 현재 스레드의 컨텍스트 제거
     *
     * 중요: 메모리 누수 방지를 위해 요청 완료 후 반드시 호출
     * - GatewayHeaderInterceptor의 afterCompletion에서 자동 호출
     * - MdcFilter의 finally 블록에서도 안전장치로 호출
     */
    public static void clear() {
        contextHolder.remove();
    }

    /**
     * 컨텍스트 존재 여부 확인
     *
     * @return 컨텍스트가 설정되어 있으면 true
     */
    public static boolean hasContext() {
        return contextHolder.get() != null;
    }

    /**
     * 현재 사용자 번호 가져오기 (편의 메서드)
     *
     * @return 사용자 번호 (없으면 null)
     */
    public static Long getCurrentUserNo() {
        GatewayContext context = getCurrent();
        return context != null ? context.getUserNo() : null;
    }

    /**
     * 현재 사용자 ID 가져오기 (편의 메서드)
     *
     * @return 사용자 ID (없으면 null)
     */
    public static String getCurrentUserId() {
        GatewayContext context = getCurrent();
        return context != null ? context.getUserId() : null;
    }

    /**
     * 현재 닉네임 가져오기 (편의 메서드)
     *
     * @return 닉네임 (없으면 null)
     */
    public static String getCurrentNickName() {
        GatewayContext context = getCurrent();
        return context != null ? context.getNickName() : null;
    }

    /**
     * 현재 역할 가져오기 (편의 메서드)
     *
     * @return 역할 (없으면 null)
     */
    public static String getCurrentRole() {
        GatewayContext context = getCurrent();
        return context != null ? context.getRole() : null;
    }

    /**
     * 현재 권한 리스트 가져오기 (편의 메서드)
     *
     * @return 권한 리스트 (없으면 빈 리스트)
     */
    public static List<String> getCurrentAuth() {
        GatewayContext context = getCurrent();
        return context != null && context.getAuth() != null
                ? context.getAuth()
                : List.of();
    }

    /**
     * 현재 클라이언트 IP 가져오기 (편의 메서드)
     *
     * @return 클라이언트 IP (없으면 null)
     */
    public static String getCurrentClientIp() {
        GatewayContext context = getCurrent();
        return context != null ? context.getClientIp() : null;
    }

    /**
     * 현재 Access Token 가져오기 (편의 메서드)
     *
     * 용도: 마이크로서비스 간 내부 호출 시 Authorization 헤더 전달
     *
     * 보안 주의:
     * - 반환된 토큰을 로그에 출력하지 말 것
     * - DB나 캐시에 저장하지 말 것
     * - HTTP 헤더로만 전달할 것
     *
     * @return Access Token (없으면 null)
     */
    public static String getCurrentAccessToken() {
        GatewayContext context = getCurrent();
        return context != null ? context.getAccessToken() : null;
    }

    /**
     * Access Token 존재 여부 확인 (편의 메서드)
     *
     * @return Access Token이 있으면 true
     */
    public static boolean hasAccessToken() {
        String token = getCurrentAccessToken();
        return token != null && !token.isEmpty();
    }

    /**
     * 특정 권한 보유 여부 확인 (인스턴스 메서드)
     *
     * @param target 확인할 권한
     * @return 보유 여부
     */
    public boolean hasAuth(String target) {
        return auth != null && auth.contains(target);
    }

    /**
     * 여러 권한 중 하나라도 보유 여부 확인 (인스턴스 메서드)
     *
     * @param targets 확인할 권한들
     * @return 하나라도 보유하면 true
     */
    public boolean hasAnyAuth(String... targets) {
        if (auth == null || auth.isEmpty() || targets == null || targets.length == 0) {
            return false;
        }
        return Arrays.stream(targets).anyMatch(auth::contains);
    }

    /**
     * 모든 권한 보유 여부 확인 (인스턴스 메서드)
     *
     * @param targets 확인할 권한들
     * @return 모두 보유하면 true
     */
    public boolean hasAllAuth(String... targets) {
        if (auth == null || auth.isEmpty() || targets == null || targets.length == 0) {
            return false;
        }
        return Arrays.stream(targets).allMatch(auth::contains);
    }

    /**
     * 특정 권한 보유 여부 확인 (정적 메서드 - 현재 컨텍스트 기준)
     *
     * @param target 확인할 권한
     * @return 보유 여부
     */
    public static boolean currentHasAuth(String target) {
        GatewayContext context = getCurrent();
        return context != null && context.hasAuth(target);
    }

    /**
     * 여러 권한 중 하나라도 보유 여부 확인 (정적 메서드 - 현재 컨텍스트 기준)
     *
     * @param targets 확인할 권한들
     * @return 하나라도 보유하면 true
     */
    public static boolean currentHasAnyAuth(String... targets) {
        GatewayContext context = getCurrent();
        return context != null && context.hasAnyAuth(targets);
    }

    /**
     * 모든 권한 보유 여부 확인 (정적 메서드 - 현재 컨텍스트 기준)
     *
     * @param targets 확인할 권한들
     * @return 모두 보유하면 true
     */
    public static boolean currentHasAllAuth(String... targets) {
        GatewayContext context = getCurrent();
        return context != null && context.hasAllAuth(targets);
    }

    /**
     * toString 오버라이드 - 보안을 위해 accessToken은 마스킹 처리
     */
    @Override
    public String toString() {
        return "GatewayContext{" +
                "userNo=" + userNo +
                ", userId='" + maskUserId(userId) + '\'' +
                ", serviceId='" + serviceId + '\'' +
                ", role='" + role + '\'' +
                ", auth=" + auth +
                ", provider='" + provider + '\'' +
                ", nickName='" + nickName + '\'' +
                ", clientIp='" + clientIp + '\'' +
                ", deviceCd='" + deviceCd + '\'' +
                ", deviceStr='" + deviceStr + '\'' +
                ", hasAccessToken=" + (accessToken != null && !accessToken.isEmpty()) +
                '}';
    }

    /**
     * userId 마스킹 처리 (로그 출력용)
     */
    private String maskUserId(String userId) {
        if (userId == null || userId.length() <= 3) {
            return "***";
        }
        return userId.substring(0, 3) + "***";
    }
}