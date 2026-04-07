package org.softwiz.platform.iot.common.lib.validator;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Gateway 서명 검증기
 *
 * Gateway에서 전송한 서명을 검증하여 직접 호출을 방지합니다.
 * Mock 환경에서는 특정 Mock 시그니처를 허용합니다.
 *
 * gateway.signature.enabled=false로 설정하면 검증을 완전히 비활성화합니다.
 * gateway.signature.mock-enabled=true로 설정하면 Mock 시그니처를 허용합니다.
 */
@Slf4j
@Component
public class GatewaySignatureValidator {

    @Value("${gateway.signature.secret:dummy-secret-for-disabled-mode}")
    private String signatureSecret;

    @Value("${gateway.signature.mock-enabled:false}")
    private boolean mockEnabled;

    @Value("${gateway.signature.enabled:true}")
    private boolean signatureEnabled;

    @Value("${gateway.signature.timeout:300}")
    private long signatureTimeoutSeconds;

    private static final String MOCK_SIGNATURE = "MOCK_GATEWAY_SIGNATURE_FOR_TESTING";

    /**
     * 내부 서비스 간 통신용 서명 데이터 형식
     * Gateway와 다른 형식을 사용하여 구분
     */
    private static final String INTERNAL_SERVICE_METHOD = "INTERNAL";
    private static final String INTERNAL_SERVICE_URI = "/internal/service-call";

    /**
     * Gateway 서명 검증
     *
     * @param request HTTP 요청
     * @return 서명이 유효하면 true, 그렇지 않으면 false
     */
    public boolean validateSignature(HttpServletRequest request) {
        // ★★★ 서명 검증이 비활성화된 경우 (개발 편의용) ★★★
        if (!signatureEnabled) {
            log.debug("Gateway signature validation is DISABLED");
            return true;
        }

        String signature = request.getHeader("X-Gateway-Signature");
        String timestamp = request.getHeader("X-Gateway-Timestamp");

        // 1. Mock 시그니처 체크 (로컬 테스트용)
        if (mockEnabled && MOCK_SIGNATURE.equals(signature)) {
            log.debug("Mock signature accepted (mock-enabled=true)");
            return true;
        }

        // 2. 헤더 존재 여부 확인
        if (signature == null || signature.isBlank()) {
            log.warn("Missing X-Gateway-Signature header");
            return false;
        }

        if (timestamp == null || timestamp.isBlank()) {
            log.warn("Missing X-Gateway-Timestamp header");
            return false;
        }

        // 3. 타임스탬프 유효성 확인
        try {
            long requestTime = Long.parseLong(timestamp);
            long currentTime = System.currentTimeMillis();
            long timeDiff = Math.abs(currentTime - requestTime);
            long maxDiff = signatureTimeoutSeconds * 1000;

            if (timeDiff > maxDiff) {
                log.warn("Timestamp expired - Diff: {}ms, Max: {}ms", timeDiff, maxDiff);
                return false;
            }
        } catch (NumberFormatException e) {
            log.error("Invalid timestamp format: {}", timestamp);
            return false;
        }

        // 4. 내부 서비스 호출 서명 검증 (먼저 체크)
        String internalSignature = generateInternalSignature(timestamp);
        if (internalSignature.equals(signature)) {
            log.debug("Internal service call signature validated");
            return true;
        }

        // 5. 전체 URI 구성
        // Gateway가 서명 생성에 사용한 원본 URI를 우선 사용 (인코딩 차이로 인한 불일치 방지)
        String gatewayUri = request.getHeader("X-Gateway-Request-URI");
        String requestUri = request.getRequestURI();
        String queryString = request.getQueryString();
        String serviceUri = queryString != null ? requestUri + "?" + queryString : requestUri;
        String method = request.getMethod();

        // Gateway 헤더가 있으면 해당 URI로 검증 (서명 생성 시 사용된 동일한 URI)
        String fullUri;
        if (gatewayUri != null && !gatewayUri.isBlank()) {
            fullUri = gatewayUri;
            if (!gatewayUri.equals(serviceUri)) {
                log.debug("URI differs - using Gateway header for validation | gateway='{}' | service='{}'",
                        gatewayUri, serviceUri);
            }
        } else {
            fullUri = serviceUri;
            log.debug("X-Gateway-Request-URI header not present, using service URI: {}", serviceUri);
        }

        // 6. Gateway 서명 검증
        String expectedSignature = generateSignature(method, fullUri, timestamp);
        boolean isValid = expectedSignature.equals(signature);

        if (isValid) {
            log.trace("Gateway signature validated successfully | method={} | uri={}", method, fullUri);
        } else {
            log.warn("Gateway signature mismatch | method={} | uri={}", method, fullUri);

            if (log.isDebugEnabled()) {
                log.debug("Signature detail | expected='{}' | actual='{}'", expectedSignature, signature);
                log.debug("URI detail | gatewayHeader='{}' | serviceUri='{}' | usedUri='{}'",
                        gatewayUri, serviceUri, fullUri);

                // Gateway 헤더 없이 serviceUri로도 재검증 시도하여 원인 파악
                if (gatewayUri != null && !gatewayUri.equals(serviceUri)) {
                    String altSignature = generateSignature(method, serviceUri, timestamp);
                    if (altSignature.equals(signature)) {
                        log.debug("Signature WOULD match with serviceUri - encoding difference confirmed");
                    }
                }
            }
        }

        return isValid;
    }

    // ========================================
    // 서명 생성 메서드 (public으로 노출)
    // ========================================

    /**
     * 서명 생성 (HMAC-SHA256)
     *
     * ✅ PushClient 등 내부 서비스에서 실제 URI로 서명 생성 시 사용
     *
     * @param method HTTP 메서드 (예: POST, GET)
     * @param uri 요청 URI (예: /api/v2/push/send)
     * @param timestamp 타임스탬프
     * @return Base64 인코딩된 서명
     */
    public String generateSignature(String method, String uri, String timestamp) {
        try {
            String data = String.format("%s:%s:%s", method, uri, timestamp);

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    signatureSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            mac.init(secretKeySpec);

            byte[] hmacData = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacData);

        } catch (Exception e) {
            log.error("Failed to generate signature", e);
            return "";
        }
    }

    // ========================================
    // 내부 서비스 간 호출용 메서드
    // ========================================

    /**
     * 내부 서비스 호출용 타임스탬프 생성
     *
     * @return 현재 시간의 타임스탬프 (밀리초)
     */
    public String generateTimestamp() {
        return String.valueOf(System.currentTimeMillis());
    }

    /**
     * 내부 서비스 호출용 서명 생성 (INTERNAL 방식)
     *
     * <p>마이크로서비스 간 직접 통신 시 사용합니다.</p>
     * <p>K8s NetworkPolicy로 같은 네임스페이스 내 Pod 간 통신만 허용되므로,
     * 외부에서 이 서명을 생성해도 네트워크 레벨에서 차단됩니다.</p>
     *
     * @param timestamp 타임스탬프
     * @return Base64 인코딩된 서명
     */
    public String generateSignature(String timestamp) {
        return generateInternalSignature(timestamp);
    }

    /**
     * 내부 서비스 호출용 서명 생성 (내부 메서드)
     *
     * @param timestamp 타임스탬프
     * @return Base64 인코딩된 서명
     */
    private String generateInternalSignature(String timestamp) {
        return generateSignature(INTERNAL_SERVICE_METHOD, INTERNAL_SERVICE_URI, timestamp);
    }

    // ========================================
    // Mock 관련 메서드
    // ========================================

    /**
     * Mock 시그니처 반환
     *
     * @return Mock 시그니처 상수
     */
    public String getMockSignature() {
        return MOCK_SIGNATURE;
    }

    /**
     * Mock 시그니처 생성 (DevController에서 사용)
     *
     * @param method HTTP 메서드
     * @param uri 요청 URI (쿼리 파라미터 포함)
     * @return Mock 환경용 시그니처
     */
    public String generateMockSignature(String method, String uri) {
        if (mockEnabled) {
            return MOCK_SIGNATURE;
        }

        String timestamp = String.valueOf(System.currentTimeMillis());
        return generateSignature(method, uri, timestamp);
    }

    /**
     * Mock 타임스탬프 생성
     *
     * @return 현재 시간의 타임스탬프
     */
    public String generateMockTimestamp() {
        return String.valueOf(System.currentTimeMillis());
    }

    /**
     * Mock 모드 활성화 여부
     *
     * @return Mock 모드 여부
     */
    public boolean isMockEnabled() {
        return mockEnabled;
    }

    /**
     * 서명 검증 활성화 여부
     *
     * @return 서명 검증 활성화 여부
     */
    public boolean isSignatureEnabled() {
        return signatureEnabled;
    }
}