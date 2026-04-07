package org.softwiz.platform.iot.common.lib.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 마스킹 유틸리티
 *
 * 역할:
 * - 로그에 출력되는 민감한 정보 마스킹
 * - 개인정보 보호 및 보안 강화
 */
@Slf4j
@Component
public class MaskingUtil {

    private static final Set<String> FULLY_MASKED_HEADERS = Set.of(
            "authorization",
            "x-auth-token",
            "x-api-key",
            "api-key",
            "x-access-token",
            "x-refresh-token",
            "x-gateway-signature",
            "x-gateway-timestamp",
            "cookie",
            "set-cookie",
            "session-id",
            "jsessionid",
            "x-user-id",
            "x-csrf-token",
            "x-xsrf-token"
    );

    private static final Set<String> PARTIALLY_MASKED_HEADERS = Set.of(
            "x-request-id",
            "x-forwarded-for",
            "x-real-ip",
            "x-client-ip"
    );

    private static final Set<String> MASKED_PARAM_KEYWORDS = Set.of(
            "password",
            "passwd",
            "pwd",
            "secret",
            "token",
            "key",
            "credential",
            "auth",
            "ssn",
            "jumin",
            "card",
            "account",
            "pin",
            "otp",
            "verification",
            "userid",
            "userno"
    );

    // JWT 토큰 패턴 (정확하게)
    private static final Pattern JWT_PATTERN = Pattern.compile(
            "eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}"
    );

    // 숫자로만 된 ID 패턴 (8~12자리)
    private static final Pattern NUMERIC_ID_PATTERN = Pattern.compile(
            "(?<![\\d])(\\d{8,12})(?![\\d])"
    );

    /**
     * 로그 메시지 전체 마스킹 (Logback에서 사용)
     */
    public String maskLogMessage(String logMessage) {
        if (logMessage == null || logMessage.isEmpty()) {
            return logMessage;
        }

        try {
            // 1. 헤더 패턴 마스킹
            logMessage = maskHeadersInLog(logMessage);

            // 2. URI 파라미터 마스킹
            logMessage = maskUriInLog(logMessage);

            // 3. JWT 토큰 마스킹
            logMessage = maskJwtTokens(logMessage);

            // 4. 긴 숫자 ID 마스킹 (선택적)
            logMessage = maskNumericIds(logMessage);

            return logMessage;
        } catch (Exception e) {
            // 마스킹 중 예외 발생 시 원본 반환 (로깅 실패 방지)
            log.warn("Failed to mask log message: {}", e.getMessage());
            return logMessage;
        }
    }

    /**
     * 헤더 패턴 마스킹
     */
    private String maskHeadersInLog(String log) {
        // Authorization: Bearer xxx
        log = log.replaceAll(
                "(?i)(Authorization|authorization)\\s*[:=]\\s*[\"']?Bearer\\s+[A-Za-z0-9._-]+[\"']?",
                "$1: \"Bearer ***\""
        );

        // Authorization: Basic xxx
        log = log.replaceAll(
                "(?i)(Authorization|authorization)\\s*[:=]\\s*[\"']?Basic\\s+[A-Za-z0-9+/=]+[\"']?",
                "$1: \"Basic ***\""
        );

        // Cookie 관련
        log = log.replaceAll(
                "(?i)(Cookie|Set-Cookie|cookie|set-cookie)\\s*[:=]\\s*[\"'][^\"']+[\"']",
                "$1: \"***\""
        );

        // X-로 시작하는 인증 관련 헤더
        log = log.replaceAll(
                "(?i)(X-User-Id|X-Auth-Token|X-API-Key|X-Gateway-Signature|X-Access-Token|X-Refresh-Token)\\s*[:=]\\s*[\"'][^\"']+[\"']",
                "$1: \"***\""
        );

        return log;
    }

    /**
     * URI 파라미터 마스킹
     */
    private String maskUriInLog(String log) {
        for (String keyword : MASKED_PARAM_KEYWORDS) {
            // URL 파라미터: ?password=xxx, &password=xxx
            log = log.replaceAll(
                    "(?i)([?&]" + keyword + "=)[^&\\s\"'\\]]+",
                    "$1***"
            );
            // JSON 스타일: "password":"xxx"
            log = log.replaceAll(
                    "(?i)(\"" + keyword + "\"\\s*:\\s*\")[^\"]+\"",
                    "$1***\""
            );
            // 일반 키-값: password=xxx
            log = log.replaceAll(
                    "(?i)(" + keyword + "\\s*=\\s*)[^,\\s}&\\]]+",
                    "$1***"
            );
        }
        return log;
    }

    /**
     * JWT 토큰 마스킹
     */
    private String maskJwtTokens(String log) {
        return JWT_PATTERN.matcher(log).replaceAll("eyJ***.[MASKED].[MASKED]");
    }

    /**
     * 긴 숫자 ID 마스킹 (8~12자리)
     * 타임스탬프(13자리 이상)나 날짜는 제외
     */
    private String maskNumericIds(String log) {
        return NUMERIC_ID_PATTERN.matcher(log).replaceAll(match -> {
            String matched = match.group(1);
            if (matched.length() < 8) {
                return matched;
            }
            return matched.substring(0, 3) + "***" + matched.substring(matched.length() - 2);
        });
    }

    /**
     * 헤더 맵 마스킹 (로깅용)
     */
    public Map<String, String> maskHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return headers;
        }

        Map<String, String> masked = new HashMap<>();

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey().toLowerCase();
            String value = entry.getValue();

            if (value == null) {
                masked.put(entry.getKey(), null);
                continue;
            }

            if (FULLY_MASKED_HEADERS.contains(key)) {
                masked.put(entry.getKey(), "***");
                continue;
            }

            if (PARTIALLY_MASKED_HEADERS.contains(key)) {
                masked.put(entry.getKey(), maskPartially(value, 8));
                continue;
            }

            if ("x-gateway-request-uri".equals(key)) {
                masked.put(entry.getKey(), maskUri(value));
                continue;
            }

            masked.put(entry.getKey(), value);
        }

        return masked;
    }

    /**
     * 파라미터 맵 마스킹 (로깅용)
     */
    public Map<String, String[]> maskParams(Map<String, String[]> params) {
        if (params == null || params.isEmpty()) {
            return params;
        }

        Map<String, String[]> masked = new HashMap<>();

        for (Map.Entry<String, String[]> entry : params.entrySet()) {
            String key = normalizeKey(entry.getKey());
            String[] values = entry.getValue();

            boolean shouldMask = MASKED_PARAM_KEYWORDS.stream()
                    .anyMatch(key::contains);

            if (shouldMask) {
                String[] maskedValues = new String[values.length];
                Arrays.fill(maskedValues, "***");
                masked.put(entry.getKey(), maskedValues);
            } else {
                masked.put(entry.getKey(), values);
            }
        }

        return masked;
    }

    /**
     * 이메일 마스킹
     */
    public String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }

        String[] parts = email.split("@");
        if (parts.length != 2) {
            return email;
        }

        String localPart = parts[0];
        String domain = parts[1];

        if (localPart.length() <= 3) {
            return localPart.charAt(0) + "***@" + domain;
        }

        return localPart.substring(0, 3) + "***@" + domain;
    }

    /**
     * 사용자 ID 마스킹
     * - 이메일 형식이면 maskEmail() 위임
     * - 3자 이하: 전체 마스킹
     * - 4~8자: 앞 3자 + ***  (예: user123 → use***)
     * - 9자 이상: *** + 끝 5자 (예: administrator → ***rator)
     */
    public String maskUserId(String userId) {
        if (userId == null || userId.isEmpty()) {
            return "UNKNOWN";
        }

        if (userId.contains("@")) {
            return maskEmail(userId);
        }

        int len = userId.length();

        if (len <= 3) {
            return "***";
        }

        if (len <= 8) {
            return userId.substring(0, 3) + "***";
        }

        return "***" + userId.substring(len - 5);
    }

    /**
     * 이름 마스킹
     */
    public String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }

        if (name.matches("^[가-힣]{2,4}$")) {
            if (name.length() == 2) {
                return name.charAt(0) + "*";
            }
            return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
        }

        String[] parts = name.split("\\s+");
        if (parts.length > 1) {
            StringBuilder masked = new StringBuilder();
            for (String part : parts) {
                if (masked.length() > 0) {
                    masked.append(" ");
                }
                masked.append(part.charAt(0)).append("***");
            }
            return masked.toString();
        }

        return name.charAt(0) + "***";
    }

    /**
     * IP 주소 마스킹
     */
    public String maskIpAddress(String ip) {
        if (ip == null || ip.isEmpty()) {
            return ip;
        }

        // IPv4
        if (ip.contains(".")) {
            String[] parts = ip.split("\\.");
            if (parts.length == 4) {
                return parts[0] + "." + parts[1] + ".***." + "***";
            }
        }

        // IPv6
        if (ip.contains(":")) {
            String[] parts = ip.split(":");
            if (parts.length >= 2) {
                return parts[0] + ":" + parts[1] + ":***:***:***:***:***:***";
            }
        }

        return ip;
    }

    /**
     * 디바이스 ID 마스킹
     */
    public String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) {
            return deviceId;
        }

        if (deviceId.length() <= 6) {
            return "***";
        }

        return deviceId.substring(0, 3) + "***" + deviceId.substring(deviceId.length() - 3);
    }

    /**
     * 전화번호 마스킹
     */
    public String maskPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return phone;
        }

        if (phone.contains("-")) {
            String[] parts = phone.split("-");
            if (parts.length == 3) {
                return parts[0] + "-****-" + parts[2];
            }
        }

        if (phone.length() == 11) {
            return phone.substring(0, 3) + "****" + phone.substring(7);
        } else if (phone.length() == 10) {
            return phone.substring(0, 3) + "***" + phone.substring(6);
        }

        return "***";
    }

    private String normalizeKey(String key) {
        return key.toLowerCase()
                .replace("_", "")
                .replace("-", "");
    }

    private String maskPartially(String value, int visibleLength) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        if (value.length() <= visibleLength) {
            return value;
        }

        return value.substring(0, visibleLength) + "...";
    }

    /**
     * URI 마스킹
     */
    public String maskUri(String uri) {
        if (uri == null || !uri.contains("?")) {
            return uri;
        }

        String[] parts = uri.split("\\?");
        if (parts.length != 2) {
            return uri;
        }

        String path = parts[0];
        String queryString = parts[1];

        String[] params = queryString.split("&");
        StringBuilder maskedQuery = new StringBuilder();

        for (String param : params) {
            if (maskedQuery.length() > 0) {
                maskedQuery.append("&");
            }

            String[] kv = param.split("=");
            if (kv.length == 2) {
                String key = normalizeKey(kv[0]);
                boolean shouldMask = MASKED_PARAM_KEYWORDS.stream()
                        .anyMatch(key::contains);

                if (shouldMask) {
                    maskedQuery.append(kv[0]).append("=***");
                } else {
                    maskedQuery.append(param);
                }
            } else {
                maskedQuery.append(param);
            }
        }

        return path + "?" + maskedQuery;
    }
}