package org.softwiz.platform.iot.common.lib.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 클라이언트 IP 추출 유틸리티
 *
 * 역할:
 * - 게이트웨이가 전달한 X-Client-IP 헤더 우선 사용
 * - 없을 경우 다양한 프록시 헤더에서 추출 (fallback)
 *
 * 헤더 우선순위:
 * 1. X-Client-IP (Gateway에서 설정)
 * 2. X-Original-Forwarded-For
 * 3. X-Forwarded-For
 * 4. X-Real-IP
 * 5. Proxy-Client-IP
 * 6. WL-Proxy-Client-IP
 * 7. HTTP_CLIENT_IP
 * 8. HTTP_X_FORWARDED_FOR
 * 9. RemoteAddr (최종 fallback)
 */
@Slf4j
@Component
public class ClientIpExtractor {

    private static final String[] IP_HEADERS = {
            "X-Client-IP",
            "X-Original-Forwarded-For",
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR"
    };

    private static final String UNKNOWN = "unknown";

    /**
     * 클라이언트 IP 추출
     *
     * @param request HTTP 요청
     * @return 클라이언트 IP 주소
     */
    public String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }

        for (String header : IP_HEADERS) {
            String ip = request.getHeader(header);
            if (isValidIp(ip)) {
                String extractedIp = extractFirstIp(ip);
                log.trace("Client IP from {}: {}", header, extractedIp);
                return extractedIp;
            }
        }

        String remoteAddr = request.getRemoteAddr();
        log.trace("Client IP from RemoteAddr: {}", remoteAddr);
        return remoteAddr != null ? remoteAddr : UNKNOWN;
    }

    /**
     * X-Forwarded-For 등 여러 IP가 포함된 경우 첫 번째 IP 추출
     */
    private String extractFirstIp(String ip) {
        if (ip == null) {
            return UNKNOWN;
        }
        if (ip.contains(",")) {
            return ip.split(",")[0].trim();
        }
        return ip.trim();
    }

    /**
     * 유효한 IP인지 검증
     */
    private boolean isValidIp(String ip) {
        return ip != null && !ip.isEmpty() && !UNKNOWN.equalsIgnoreCase(ip);
    }
}