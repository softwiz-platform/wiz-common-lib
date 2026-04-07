package org.softwiz.platform.iot.common.lib.interceptor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.softwiz.platform.iot.common.lib.context.GatewayContext;
import org.softwiz.platform.iot.common.lib.dto.ErrorResponse;
import org.softwiz.platform.iot.common.lib.util.CryptoUtil;
import org.softwiz.platform.iot.common.lib.validator.GatewaySignatureValidator;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GatewayHeaderInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GatewayHeaderInterceptor.class);

    private final CryptoUtil cryptoUtil;
    private final GatewaySignatureValidator signatureValidator;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {

        // ★★★ 1. 서명 검증 (enabled=false면 Validator 내부에서 true 반환) ★★★
        if (!signatureValidator.validateSignature(request)) {
            log.warn("Gateway signature validation failed: {} {}", request.getMethod(), request.getRequestURI());
            sendUnauthorizedResponse(response, "Invalid gateway signature");
            return false;
        }

        // ★★★ 2. 사용자 헤더가 있으면 GatewayContext 설정 ★★★
        String encryptedUserId = request.getHeader("X-User-Id");

        if (encryptedUserId == null || encryptedUserId.isBlank()) {
            // 익명 요청: 빈 GatewayContext 설정 (null 대신 빈 객체로 접근 가능)
            GatewayContext.setContext(GatewayContext.builder().build());
            log.debug("Anonymous request passed with empty context: {} {}", request.getMethod(), request.getRequestURI());
            return true;
        }

        // ★★★ 3. 인증된 요청: GatewayContext 설정 ★★★
        String userNoHeader = request.getHeader("X-User-No");
        String serviceId = request.getHeader("X-Service-Id");
        String role = request.getHeader("X-Role");
        String authHeader = request.getHeader("X-Auth");
        String provider = request.getHeader("X-Provider");
        String nickNameHeader = request.getHeader("X-Nick-Name");
        String clientIp = request.getHeader("X-Client-IP");
        String deviceCd = request.getHeader("X-Device-Cd");
        String deviceStr = request.getHeader("X-Device-Str");

        // nickName URL 디코딩 (한글 헤더 수신용)
        String nickName = decodeNickName(nickNameHeader);

        // Authorization 헤더 추출
        String authorizationHeader = request.getHeader("Authorization");
        String accessToken = null;
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            accessToken = authorizationHeader.substring(7);
            log.debug("AccessToken extracted from Authorization header");
        }

        Long userNo = parseUserNo(userNoHeader);

        // userId 복호화
        String decryptedUserId;
        try {
            decryptedUserId = cryptoUtil.decrypt(encryptedUserId);
        } catch (Exception e) {
            log.error("Failed to decrypt userId: {}", e.getMessage());
            sendUnauthorizedResponse(response, "인증실패");
            return false;
        }

        List<String> auth = parseAuthHeader(authHeader);

        // MDC 설정
        if (serviceId != null && !serviceId.isBlank()) {
            MDC.put("serviceId", serviceId);
        }
        if (nickName != null && !nickName.isBlank()) {
            MDC.put("nickName", nickName);
        }

        // GatewayContext 설정
        GatewayContext context = GatewayContext.builder()
                .userNo(userNo)
                .userId(decryptedUserId)
                .serviceId(serviceId)
                .role(role)
                .auth(auth)
                .provider(provider)
                .nickName(nickName)
                .clientIp(clientIp)
                .deviceCd(deviceCd)
                .deviceStr(deviceStr)
                .accessToken(accessToken)
                .build();

        GatewayContext.setContext(context);

        if (log.isDebugEnabled()) {
            log.debug("Gateway context initialized - UserNo: {}, Service: {}, Role: {}, HasToken: {}",
                    userNo, serviceId, role, accessToken != null);
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        MDC.remove("serviceId");
        MDC.remove("nickName");
        GatewayContext.clear();
    }

    /**
     * 닉네임 URL 디코딩 (한글 헤더 수신용)
     */
    private String decodeNickName(String nickNameHeader) {
        if (nickNameHeader == null || nickNameHeader.isBlank()) {
            return null;
        }
        try {
            return URLDecoder.decode(nickNameHeader, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to decode nickName: {}", e.getMessage());
            return nickNameHeader;
        }
    }

    private Long parseUserNo(String userNoHeader) {
        if (userNoHeader != null && !userNoHeader.isBlank()) {
            try {
                return Long.parseLong(userNoHeader);
            } catch (NumberFormatException e) {
                log.warn("Failed to parse userNo header: {}", userNoHeader);
                return null;
            }
        }
        return null;
    }

    private List<String> parseAuthHeader(String authHeader) {
        if (authHeader != null && !authHeader.isBlank()) {
            try {
                List<String> auth = objectMapper.readValue(authHeader, new TypeReference<List<String>>() {});
                return auth != null ? auth : List.of();
            } catch (Exception e) {
                log.warn("Failed to parse auth header: {}", e.getMessage());
                return List.of();
            }
        }
        return List.of();
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(401);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("Unauthorized")
                .message(message)
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}