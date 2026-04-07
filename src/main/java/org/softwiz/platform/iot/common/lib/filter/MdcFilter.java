package org.softwiz.platform.iot.common.lib.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import org.softwiz.platform.iot.common.lib.util.ClientIpExtractor;
import org.softwiz.platform.iot.common.lib.util.MaskingUtil;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * MDC (Mapped Diagnostic Context) 필터
 *
 * 역할:
 * - 게이트웨이가 전달한 Request ID와 Client IP를 MDC에 설정
 * - 모든 로그에 [requestId] [clientIp] [serviceid] 형태로 표시
 *
 * 정상 흐름 (게이트웨이 경유):
 *   - X-Request-Id 헤더 존재 게이트웨이가 생성한 ID 사용
 *   - X-Client-Ip 헤더 존재 게이트웨이가 추출한 IP 사용
 *
 * 비정상 흐름 (직접 호출):
 *   - X-Request-Id 없음 새로 생성 (fallback)
 *   - X-Client-Ip 없음 자체 추출 (fallback)
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // 가장 먼저 실행
@RequiredArgsConstructor
public class MdcFilter implements Filter {

    private static final String REQUEST_ID = "requestId";
    private static final String CLIENT_IP = "clientIp";

    private final ClientIpExtractor clientIpExtractor;
    private final MaskingUtil maskingUtil;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        try {
            // 1. Request ID 추출 (게이트웨이가 전달한 값 우선)
            String requestId = extractRequestId(httpRequest);
            MDC.put(REQUEST_ID, requestId);

            // 2. Client IP 추출 (게이트웨이가 전달한 값 우선)
            String clientIp = clientIpExtractor.extractClientIp(httpRequest);
            String maskedIp = maskingUtil.maskIpAddress(clientIp);

            MDC.put(CLIENT_IP, maskedIp);

            // 3. Response Header에도 추가 (클라이언트가 추적 가능)
            httpResponse.setHeader("X-Request-Id", requestId);


            // 4. 다음 필터 체인 실행
            chain.doFilter(request, response);

        } finally {
            // 5. MDC 정리 (메모리 누수 방지)
            // Note: GatewayHeaderInterceptor에서 추가한 nickName도
            //       여기서 함께 정리됨 (afterCompletion에서도 정리하지만 안전장치)
            MDC.clear();
        }
    }

    private String extractRequestId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        String uri = request.getRequestURI();

        if (requestId == null || requestId.isEmpty()) {
            requestId = generateShortUuid();

            if (isSilentRequest(uri)) {
                log.debug("Generated Request ID for static/system call: {} - {}", requestId, uri);
            } else {
                log.warn("X-Request-Id missing: {} - {} (Direct call?)", requestId, uri);
            }
        }

        return requestId;
    }

    private boolean isSilentRequest(String uri) {
        if (uri == null) return false;

        // 정적 리소스 (확장자 기반)
        if (uri.matches(".*\\.(css|js|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot|map)$")) {
            return true;
        }

        // 내부/시스템 경로 (prefix 기반)
        return uri.startsWith("/actuator")
                || uri.contains("/actuator/")   // ← 추가: /admin/actuator/** 대응
                || uri.startsWith("/health")
                || uri.startsWith("/.well-known");
    }
    /**
     * 짧은 UUID 생성 (8자리)
     */
    private String generateShortUuid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}