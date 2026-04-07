package org.softwiz.platform.iot.common.lib.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 푸시 알림 요청 유틸리티
 *
 * <p>다른 서비스에서 WizMessage 푸시 서비스로 요청을 보낼 때 사용합니다.</p>
 *
 * <pre>
 * 사용 예시:
 * {@code
 * // 1. 시스템 알림 (간단한 푸시)
 * PushRequest request = PushUtil.builder()
 *     .serviceId("NEST")
 *     .userNo(1001L)
 *     .content("시스템 점검 안내입니다")
 *     .system()
 *     .build();
 *
 * // 2. 거래 알림
 * PushRequest request = PushUtil.builder()
 *     .serviceId("NEST")
 *     .userNo(1001L)
 *     .title("주문 완료")
 *     .content("주문이 완료되었습니다")
 *     .transaction()
 *     .warnDiv(PushUtil.WarnDiv.INFO)
 *     .linkUrl("https://app.example.com/order/123")
 *     .build();
 *
 * // 3. 인증 푸시 (title 필수)
 * PushRequest request = PushUtil.builder()
 *     .serviceId("NEST")
 *     .userNo(1001L)
 *     .title("본인인증")
 *     .content("인증번호: 123456")
 *     .verify()
 *     .build();
 *
 * // 3-1. 인증 푸시 (편의 메서드)
 * PushRequest request = PushUtil.verify("NEST", 1001L, "본인인증", "인증번호: 123456");
 *
 * // 4. 마케팅 푸시 (동의 체크 필요)
 * PushRequest request = PushUtil.builder()
 *     .serviceId("NEST")
 *     .userNo(1001L)
 *     .title("특별 할인 이벤트")
 *     .content("지금 바로 확인하세요!")
 *     .marketing()
 *     .imageUrl("https://cdn.example.com/promo.png")
 *     .build();
 *
 * // 4-1. 야간 푸시 (야간 푸시 동의 체크)
 * PushRequest request = PushUtil.builder()
 *     .serviceId("NEST")
 *     .userNo(1001L)
 *     .title("긴급 공지")
 *     .content("야간 시간대 중요 알림입니다")
 *     .system()
 *     .consentType(PushUtil.ConsentType.NIGHT_PUSH)
 *     .build();
 *
 * // 5. 예약 발송
 * PushRequest request = PushUtil.builder()
 *     .serviceId("NEST")
 *     .userNo(1001L)
 *     .content("예약된 알림입니다")
 *     .system()
 *     .scheduledAt(LocalDateTime.now().plusHours(1))
 *     .build();
 *
 * // 6. 추가 데이터 포함
 * PushRequest request = PushUtil.builder()
 *     .serviceId("NEST")
 *     .userNo(1001L)
 *     .content("새로운 메시지가 도착했습니다")
 *     .transaction()
 *     .dataField("chatRoomId", "123")
 *     .dataField("messageId", "456")
 *     .linkUrl("nest://chat/123")
 *     .build();
 *
 * // 7. 동의 확인 스킵 (긴급 알림)
 * PushRequest request = PushUtil.builder()
 *     .serviceId("NEST")
 *     .userNo(1001L)
 *     .content("긴급 알림입니다")
 *     .system()
 *     .skipConsentCheck()
 *     .build();
 *
 * // 8. 템플릿 기반 발송 (개인)
 * TemplatePushRequest request = PushUtil.templateBuilder()
 *     .serviceId("NEST")
 *     .templateCode("ORDER_COMPLETE")
 *     .userNo(1001L)
 *     .variable("orderNo", "12345")
 *     .variable("deliveryDate", "2025-12-20")
 *     .build();
 *
 * // 9. 템플릿 기반 발송 (다중)
 * TemplatePushRequest request = PushUtil.templateBuilder()
 *     .serviceId("NEST")
 *     .templateCode("MARKETING_EVENT")
 *     .userNos(1001L, 1002L, 1003L)
 *     .variable("eventName", "연말 할인")
 *     .variable("eventContent", "최대 50% 할인!")
 *     .build();
 *
 * // 10. 템플릿 기반 발송 (전체)
 * TemplatePushRequest request = PushUtil.templateBuilder()
 *     .serviceId("NEST")
 *     .templateCode("SYSTEM_NOTICE")
 *     .sendAll()
 *     .variable("noticeTitle", "서버 점검")
 *     .skipConsentCheck()
 *     .build();
 *
 * // 11. 토큰 저장 요청
 * TokenRequest request = PushUtil.tokenBuilder()
 *     .serviceId("NEST")
 *     .userNo(1001L)
 *     .pushToken("fCm_token_xxx...")
 *     .deviceId("abc123def456")
 *     .android()
 *     .deviceModel("Galaxy S24")
 *     .osVersion("14")
 *     .appVersion("1.0.0")
 *     .build();
 *
 * // 12. RestTemplate으로 발송
 * String pushServiceUrl = "http://wizmessage:8095/api/v2/push/send";
 * ApiResponse response = restTemplate.postForObject(pushServiceUrl, request, ApiResponse.class);
 *
 * // 13. 템플릿 발송
 * String templatePushUrl = "http://wizmessage:8095/api/v2/push/template/send";
 * ApiResponse response = restTemplate.postForObject(templatePushUrl, request, ApiResponse.class);
 *
 * // 14. 인증 푸시 발송 (전용 API)
 * String verifyPushUrl = "http://wizmessage:8095/api/v2/push/verify/send";
 * ApiResponse response = restTemplate.postForObject(verifyPushUrl, request, ApiResponse.class);
 * }
 * </pre>
 */
@Slf4j
public class PushUtil {

    private PushUtil() {
        // Utility class
    }

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // ========================================
    // 상수
    // ========================================

    /**
     * 알림 구분 (warnDiv)
     */
    public enum WarnDiv {
        INFO("I"),      // 정보
        WARNING("W"),   // 경고
        DANGER("D");    // 위험

        private final String code;

        WarnDiv(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    /**
     * 푸시 타입 (통일)
     */
    public enum PushType {
        SYSTEM("SYSTEM"),           // 시스템 알림
        TRANSACTION("TRANSACTION"), // 거래 알림
        VERIFY("VERIFY"),           // 인증 알림
        MARKETING("MARKETING");     // 마케팅 알림

        private final String code;

        PushType(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    /**
     * 동의 유형
     */
    public enum ConsentType {
        PUSH("PUSH"),
        MARKETING_PUSH("MARKETING_PUSH"),
        NIGHT_PUSH("NIGHT_PUSH"),
        LOCATION("LOCATION");
        private final String code;

        ConsentType(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    /**
     * OS 타입
     */
    public enum OsType {
        ANDROID("ANDROID"),
        IOS("IOS"),
        WEB("WEB");

        private final String code;

        OsType(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    // ========================================
    // 푸시 요청 DTO
    // ========================================

    /**
     * 푸시 발송 요청 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PushRequest {
        private String serviceId;
        private Long userNo;
        private String content;
        private String title;
        private String pushType;
        private String consentType;
        private String data;
        private String linkUrl;
        private String imageUrl;
        private String warnDiv;
        private String pushValue;
        private String eventTime;
        private Boolean skipConsentCheck;
        private String deviceId;
    }

    /**
     * 템플릿 푸시 발송 요청 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TemplatePushRequest {
        private String serviceId;
        private String templateCode;
        private Long userNo;
        private List<Long> userNos;
        private Boolean sendAll;
        private Map<String, String> variables;
        private String data;
        private String linkUrl;
        private String imageUrl;
        private String eventTime;
        private Boolean skipConsentCheck;
        private String deviceId;
    }

    /**
     * 푸시 토큰 저장 요청 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TokenRequest {
        private String serviceId;
        private Long userNo;
        private String pushToken;
        private String deviceId;
        private String uuid;
        private String isPush;
        private String os;
        private String deviceModel;
        private String osVersion;
        private String appVersion;
    }

    // ========================================
    // Builder 팩토리 메서드
    // ========================================

    public static PushRequestBuilder builder() {
        return new PushRequestBuilder();
    }

    public static TemplatePushRequestBuilder templateBuilder() {
        return new TemplatePushRequestBuilder();
    }

    public static TokenRequestBuilder tokenBuilder() {
        return new TokenRequestBuilder();
    }

    // ========================================
    // 푸시 요청 빌더
    // ========================================

    public static class PushRequestBuilder {
        private String serviceId;
        private Long userNo;
        private String content;
        private String title;
        private String pushType;
        private String consentType;
        private String warnDiv;
        private String pushValue;
        private String linkUrl;
        private String imageUrl;
        private LocalDateTime scheduledAt;
        private Boolean skipConsentCheck;
        private String deviceId;
        private final Map<String, Object> dataMap = new HashMap<>();

        public PushRequestBuilder serviceId(String serviceId) {
            this.serviceId = serviceId;
            return this;
        }

        public PushRequestBuilder userNo(Long userNo) {
            this.userNo = userNo;
            return this;
        }

        public PushRequestBuilder content(String content) {
            this.content = content;
            return this;
        }

        public PushRequestBuilder title(String title) {
            this.title = title;
            return this;
        }

        public PushRequestBuilder pushType(String pushType) {
            this.pushType = pushType;
            return this;
        }

        public PushRequestBuilder pushType(PushType pushType) {
            this.pushType = pushType.getCode();
            return this;
        }

        public PushRequestBuilder system() {
            this.pushType = PushType.SYSTEM.getCode();
            return this;
        }

        public PushRequestBuilder transaction() {
            this.pushType = PushType.TRANSACTION.getCode();
            return this;
        }

        public PushRequestBuilder verify() {
            this.pushType = PushType.VERIFY.getCode();
            return this;
        }

        public PushRequestBuilder marketing() {
            this.pushType = PushType.MARKETING.getCode();
            this.consentType = ConsentType.MARKETING_PUSH.getCode();
            return this;
        }

        public PushRequestBuilder consentType(String consentType) {
            this.consentType = consentType;
            return this;
        }

        public PushRequestBuilder consentType(ConsentType consentType) {
            this.consentType = consentType.getCode();
            return this;
        }

        public PushRequestBuilder warnDiv(WarnDiv warnDiv) {
            this.warnDiv = warnDiv.getCode();
            return this;
        }

        public PushRequestBuilder warnDiv(String warnDiv) {
            this.warnDiv = warnDiv;
            return this;
        }

        public PushRequestBuilder pushValue(String pushValue) {
            this.pushValue = pushValue;
            return this;
        }

        public PushRequestBuilder linkUrl(String linkUrl) {
            this.linkUrl = linkUrl;
            return this;
        }

        public PushRequestBuilder imageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
            return this;
        }

        public PushRequestBuilder scheduledAt(LocalDateTime scheduledAt) {
            this.scheduledAt = scheduledAt;
            return this;
        }

        public PushRequestBuilder skipConsentCheck() {
            this.skipConsentCheck = true;
            return this;
        }

        public PushRequestBuilder skipConsentCheck(boolean skip) {
            this.skipConsentCheck = skip;
            return this;
        }

        public PushRequestBuilder deviceId(String deviceId) {
            this.deviceId = deviceId;
            return this;
        }

        public PushRequestBuilder dataField(String key, Object value) {
            this.dataMap.put(key, value);
            return this;
        }

        public PushRequestBuilder dataFields(Map<String, Object> data) {
            this.dataMap.putAll(data);
            return this;
        }

        public PushRequest build() {
            String dataJson = null;
            if (!dataMap.isEmpty()) {
                dataJson = JsonUtil.toJson(dataMap);
            }

            String eventTime = null;
            if (scheduledAt != null) {
                eventTime = scheduledAt.format(DATE_TIME_FORMATTER);
            }

            return PushRequest.builder()
                    .serviceId(serviceId)
                    .userNo(userNo)
                    .content(content)
                    .title(title)
                    .pushType(pushType)
                    .consentType(consentType)
                    .data(dataJson)
                    .linkUrl(linkUrl)
                    .imageUrl(imageUrl)
                    .warnDiv(warnDiv)
                    .pushValue(pushValue)
                    .eventTime(eventTime)
                    .skipConsentCheck(skipConsentCheck)
                    .deviceId(deviceId)
                    .build();
        }
    }

    // ========================================
    // 템플릿 푸시 요청 빌더
    // ========================================

    public static class TemplatePushRequestBuilder {
        private String serviceId;
        private String templateCode;
        private Long userNo;
        private List<Long> userNos;
        private Boolean sendAll;
        private final Map<String, String> variables = new HashMap<>();
        private String linkUrl;
        private String imageUrl;
        private LocalDateTime scheduledAt;
        private Boolean skipConsentCheck;
        private String deviceId;
        private final Map<String, Object> dataMap = new HashMap<>();

        public TemplatePushRequestBuilder serviceId(String serviceId) {
            this.serviceId = serviceId;
            return this;
        }

        public TemplatePushRequestBuilder templateCode(String templateCode) {
            this.templateCode = templateCode;
            return this;
        }

        public TemplatePushRequestBuilder userNo(Long userNo) {
            this.userNo = userNo;
            return this;
        }

        public TemplatePushRequestBuilder userNos(Long... userNos) {
            this.userNos = Arrays.asList(userNos);
            return this;
        }

        public TemplatePushRequestBuilder userNos(List<Long> userNos) {
            this.userNos = userNos;
            return this;
        }

        public TemplatePushRequestBuilder sendAll() {
            this.sendAll = true;
            return this;
        }

        public TemplatePushRequestBuilder variable(String key, String value) {
            this.variables.put(key, value);
            return this;
        }

        public TemplatePushRequestBuilder variable(String key, Number value) {
            this.variables.put(key, value != null ? value.toString() : "");
            return this;
        }

        public TemplatePushRequestBuilder variables(Map<String, String> variables) {
            this.variables.putAll(variables);
            return this;
        }

        public TemplatePushRequestBuilder linkUrl(String linkUrl) {
            this.linkUrl = linkUrl;
            return this;
        }

        public TemplatePushRequestBuilder imageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
            return this;
        }

        public TemplatePushRequestBuilder scheduledAt(LocalDateTime scheduledAt) {
            this.scheduledAt = scheduledAt;
            return this;
        }

        public TemplatePushRequestBuilder skipConsentCheck() {
            this.skipConsentCheck = true;
            return this;
        }

        public TemplatePushRequestBuilder skipConsentCheck(boolean skip) {
            this.skipConsentCheck = skip;
            return this;
        }

        public TemplatePushRequestBuilder deviceId(String deviceId) {
            this.deviceId = deviceId;
            return this;
        }

        public TemplatePushRequestBuilder dataField(String key, Object value) {
            this.dataMap.put(key, value);
            return this;
        }

        public TemplatePushRequestBuilder dataFields(Map<String, Object> data) {
            this.dataMap.putAll(data);
            return this;
        }

        public TemplatePushRequest build() {
            String dataJson = null;
            if (!dataMap.isEmpty()) {
                dataJson = JsonUtil.toJson(dataMap);
            }

            String eventTime = null;
            if (scheduledAt != null) {
                eventTime = scheduledAt.format(DATE_TIME_FORMATTER);
            }

            return TemplatePushRequest.builder()
                    .serviceId(serviceId)
                    .templateCode(templateCode)
                    .userNo(userNo)
                    .userNos(userNos)
                    .sendAll(sendAll)
                    .variables(variables.isEmpty() ? null : variables)
                    .data(dataJson)
                    .linkUrl(linkUrl)
                    .imageUrl(imageUrl)
                    .eventTime(eventTime)
                    .skipConsentCheck(skipConsentCheck)
                    .deviceId(deviceId)
                    .build();
        }
    }

    // ========================================
    // 토큰 요청 빌더
    // ========================================

    public static class TokenRequestBuilder {
        private String serviceId;
        private Long userNo;
        private String pushToken;
        private String deviceId;
        private String uuid;
        private String isPush = "Y";
        private String os;
        private String deviceModel;
        private String osVersion;
        private String appVersion;

        public TokenRequestBuilder serviceId(String serviceId) {
            this.serviceId = serviceId;
            return this;
        }

        public TokenRequestBuilder userNo(Long userNo) {
            this.userNo = userNo;
            return this;
        }

        public TokenRequestBuilder pushToken(String pushToken) {
            this.pushToken = pushToken;
            return this;
        }

        public TokenRequestBuilder deviceId(String deviceId) {
            this.deviceId = deviceId;
            return this;
        }

        public TokenRequestBuilder uuid(String uuid) {
            this.uuid = uuid;
            return this;
        }

        public TokenRequestBuilder isPush(boolean isPush) {
            this.isPush = isPush ? "Y" : "N";
            return this;
        }

        public TokenRequestBuilder isPush(String isPush) {
            this.isPush = isPush;
            return this;
        }

        public TokenRequestBuilder os(String os) {
            this.os = os;
            return this;
        }

        public TokenRequestBuilder os(OsType osType) {
            this.os = osType.getCode();
            return this;
        }

        public TokenRequestBuilder android() {
            this.os = OsType.ANDROID.getCode();
            return this;
        }

        public TokenRequestBuilder ios() {
            this.os = OsType.IOS.getCode();
            return this;
        }

        public TokenRequestBuilder web() {
            this.os = OsType.WEB.getCode();
            return this;
        }

        public TokenRequestBuilder deviceModel(String deviceModel) {
            this.deviceModel = deviceModel;
            return this;
        }

        public TokenRequestBuilder osVersion(String osVersion) {
            this.osVersion = osVersion;
            return this;
        }

        public TokenRequestBuilder appVersion(String appVersion) {
            this.appVersion = appVersion;
            return this;
        }

        public TokenRequest build() {
            return TokenRequest.builder()
                    .serviceId(serviceId)
                    .userNo(userNo)
                    .pushToken(pushToken)
                    .deviceId(deviceId)
                    .uuid(uuid)
                    .isPush(isPush)
                    .os(os)
                    .deviceModel(deviceModel)
                    .osVersion(osVersion)
                    .appVersion(appVersion)
                    .build();
        }
    }

    // ========================================
    // 편의 메서드
    // ========================================

    public static PushRequest info(String serviceId, Long userNo, String content) {
        return builder()
                .serviceId(serviceId)
                .userNo(userNo)
                .content(content)
                .pushType(PushType.SYSTEM)
                .warnDiv(WarnDiv.INFO)
                .build();
    }

    public static PushRequest info(String serviceId, Long userNo, String title, String content) {
        return builder()
                .serviceId(serviceId)
                .userNo(userNo)
                .title(title)
                .content(content)
                .pushType(PushType.SYSTEM)
                .warnDiv(WarnDiv.INFO)
                .build();
    }

    public static PushRequest info(String serviceId, Long userNo, String title, String content, String linkUrl) {
        return builder()
                .serviceId(serviceId)
                .userNo(userNo)
                .title(title)
                .content(content)
                .pushType(PushType.SYSTEM)
                .warnDiv(WarnDiv.INFO)
                .linkUrl(linkUrl)
                .build();
    }

    public static PushRequest warning(String serviceId, Long userNo, String content) {
        return builder()
                .serviceId(serviceId)
                .userNo(userNo)
                .title("경고")
                .content(content)
                .pushType(PushType.SYSTEM)
                .warnDiv(WarnDiv.WARNING)
                .build();
    }

    public static PushRequest warning(String serviceId, Long userNo, String content, String linkUrl) {
        return builder()
                .serviceId(serviceId)
                .userNo(userNo)
                .title("경고")
                .content(content)
                .pushType(PushType.SYSTEM)
                .warnDiv(WarnDiv.WARNING)
                .linkUrl(linkUrl)
                .build();
    }

    public static PushRequest danger(String serviceId, Long userNo, String content) {
        return builder()
                .serviceId(serviceId)
                .userNo(userNo)
                .title("위험")
                .content(content)
                .pushType(PushType.SYSTEM)
                .warnDiv(WarnDiv.DANGER)
                .build();
    }

    public static PushRequest danger(String serviceId, Long userNo, String content, String linkUrl) {
        return builder()
                .serviceId(serviceId)
                .userNo(userNo)
                .title("위험")
                .content(content)
                .pushType(PushType.SYSTEM)
                .warnDiv(WarnDiv.DANGER)
                .linkUrl(linkUrl)
                .build();
    }

    public static PushRequest system(String serviceId, Long userNo, String title, String content) {
        return builder()
                .serviceId(serviceId)
                .userNo(userNo)
                .title(title)
                .content(content)
                .pushType(PushType.SYSTEM)
                .warnDiv(WarnDiv.INFO)
                .build();
    }

    public static PushRequest system(String serviceId, Long userNo, String title, String content, String linkUrl) {
        return builder()
                .serviceId(serviceId)
                .userNo(userNo)
                .title(title)
                .content(content)
                .pushType(PushType.SYSTEM)
                .warnDiv(WarnDiv.INFO)
                .linkUrl(linkUrl)
                .build();
    }

    public static PushRequest transaction(String serviceId, Long userNo, String title, String content) {
        return builder()
                .serviceId(serviceId)
                .userNo(userNo)
                .title(title)
                .content(content)
                .pushType(PushType.TRANSACTION)
                .warnDiv(WarnDiv.INFO)
                .build();
    }

    public static PushRequest transaction(String serviceId, Long userNo, String title, String content, String linkUrl) {
        return builder()
                .serviceId(serviceId)
                .userNo(userNo)
                .title(title)
                .content(content)
                .pushType(PushType.TRANSACTION)
                .warnDiv(WarnDiv.INFO)
                .linkUrl(linkUrl)
                .build();
    }

    /**
     * 인증 푸시 (content만)
     * @deprecated title이 필수이므로 verify(serviceId, userNo, title, content)를 사용하세요
     */
    @Deprecated
    public static PushRequest verify(String serviceId, Long userNo, String content) {
        return builder()
                .serviceId(serviceId)
                .userNo(userNo)
                .content(content)
                .pushType(PushType.VERIFY)
                .warnDiv(WarnDiv.INFO)
                .build();
    }

    /**
     * 인증 푸시 (title + content)
     */
    public static PushRequest verify(String serviceId, Long userNo, String title, String content) {
        return builder()
                .serviceId(serviceId)
                .userNo(userNo)
                .title(title)
                .content(content)
                .pushType(PushType.VERIFY)
                .warnDiv(WarnDiv.INFO)
                .build();
    }

    /**
     * 인증 푸시 (간편 메서드 - title 자동 설정)
     */
    public static PushRequest verifyCode(String serviceId, Long userNo, String verifyCode) {
        return builder()
                .serviceId(serviceId)
                .userNo(userNo)
                .title("본인인증")
                .content("인증번호: " + verifyCode)
                .pushType(PushType.VERIFY)
                .warnDiv(WarnDiv.INFO)
                .build();
    }

    /**
     * 인증 푸시 (간편 메서드 - 유효시간 포함)
     */
    public static PushRequest verifyCode(String serviceId, Long userNo, String verifyCode, int validMinutes) {
        return builder()
                .serviceId(serviceId)
                .userNo(userNo)
                .title("본인인증")
                .content(String.format("인증번호: %s (유효시간: %d분)", verifyCode, validMinutes))
                .pushType(PushType.VERIFY)
                .warnDiv(WarnDiv.INFO)
                .build();
    }

    public static PushRequest marketing(String serviceId, Long userNo, String title, String content) {
        return builder()
                .serviceId(serviceId)
                .userNo(userNo)
                .title(title)
                .content(content)
                .pushType(PushType.MARKETING)
                .consentType(ConsentType.MARKETING_PUSH)
                .warnDiv(WarnDiv.INFO)
                .build();
    }

    public static PushRequest marketing(String serviceId, Long userNo, String title, String content,
                                        String linkUrl, String imageUrl) {
        return builder()
                .serviceId(serviceId)
                .userNo(userNo)
                .title(title)
                .content(content)
                .pushType(PushType.MARKETING)
                .consentType(ConsentType.MARKETING_PUSH)
                .warnDiv(WarnDiv.INFO)
                .linkUrl(linkUrl)
                .imageUrl(imageUrl)
                .build();
    }

    public static TemplatePushRequest template(String serviceId, String templateCode,
                                               Long userNo, Map<String, String> variables) {
        return templateBuilder()
                .serviceId(serviceId)
                .templateCode(templateCode)
                .userNo(userNo)
                .variables(variables)
                .build();
    }

    public static TemplatePushRequest templateMultiple(String serviceId, String templateCode,
                                                       List<Long> userNos, Map<String, String> variables) {
        return templateBuilder()
                .serviceId(serviceId)
                .templateCode(templateCode)
                .userNos(userNos)
                .variables(variables)
                .build();
    }

    public static TemplatePushRequest templateAll(String serviceId, String templateCode,
                                                  Map<String, String> variables) {
        return templateBuilder()
                .serviceId(serviceId)
                .templateCode(templateCode)
                .sendAll()
                .variables(variables)
                .build();
    }

    public static TokenRequest token(String serviceId, Long userNo, String pushToken,
                                     String deviceId, String os) {
        return tokenBuilder()
                .serviceId(serviceId)
                .userNo(userNo)
                .pushToken(pushToken)
                .deviceId(deviceId)
                .os(os)
                .build();
    }
}