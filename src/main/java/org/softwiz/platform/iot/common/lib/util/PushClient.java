package org.softwiz.platform.iot.common.lib.util;

import lombok.extern.slf4j.Slf4j;
import org.softwiz.platform.iot.common.lib.dto.ApiResponse;
import org.softwiz.platform.iot.common.lib.dto.ErrorResponse;
import org.softwiz.platform.iot.common.lib.validator.GatewaySignatureValidator;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 푸시 서비스 클라이언트
 *
 * <p>다른 마이크로서비스에서 WizMessage 푸시 서비스를 호출할 때 사용합니다.</p>
 * <p>내부 서비스 간 통신을 위해 항상 새로운 Gateway 서명을 생성합니다.</p>
 *
 * <h3>헤더 처리:</h3>
 * <p>실제 호출할 method와 uri로 서명을 생성하여 전달합니다.</p>
 * <p>다른 서비스의 Gateway 헤더를 복사하지 않습니다.</p>
 *
 * <h3>보안 참고:</h3>
 * <p>K8s NetworkPolicy로 같은 네임스페이스 내 Pod 간 통신만 허용되므로,
 * 외부에서 직접 마이크로서비스 API 호출은 불가능합니다.</p>
 *
 * <h3>Bean 설정:</h3>
 * <pre>{@code
 * @Configuration
 * public class PushClientConfig {
 *     @Value("${microservice.message.url:http://wizmessage:8098}")
 *     private String messageServiceUrl;
 *
 *     @Bean
 *     public PushClient pushClient(RestTemplate restTemplate,
 *                                   GatewaySignatureValidator signatureValidator) {
 *         return new PushClient(restTemplate, messageServiceUrl, signatureValidator);
 *     }
 * }
 * }</pre>
 *
 * <h3>1. 일반 푸시 발송:</h3>
 * <pre>{@code
 * // 기본 푸시 발송
 * PushUtil.PushRequest request = PushUtil.builder()
 *     .serviceId("NEST")
 *     .userNo(1001L)
 *     .title("새 알림")
 *     .content("새로운 알림이 있습니다.")
 *     .warnDiv(PushUtil.WarnDiv.INFO)
 *     .build();
 *
 * PushResult result = pushClient.send(request);
 * if (result.isSuccess()) {
 *     Long pushId = result.getPushId();
 * }
 *
 * // 상세 옵션 포함
 * PushUtil.PushRequest request = PushUtil.builder()
 *     .serviceId("NEST")
 *     .userNo(1001L)
 *     .title("경고")
 *     .content("긴급 상황이 발생했습니다.")
 *     .warnDiv(PushUtil.WarnDiv.WARNING)
 *     .pushValue("EMERGENCY_ALERT")
 *     .linkUrl("https://app.example.com/alert/123")
 *     .imageUrl("https://cdn.example.com/warning.png")
 *     .dataField("orderId", 12345)
 *     .dataField("status", "URGENT")
 *     .build();
 *
 * // 시스템 알림 (동의 확인 스킵)
 * PushUtil.PushRequest request = PushUtil.builder()
 *     .serviceId("NEST")
 *     .userNo(1001L)
 *     .title("시스템 점검")
 *     .content("서버 점검이 예정되어 있습니다.")
 *     .skipConsentCheck()
 *     .build();
 *
 * // 마케팅 푸시 (동의 확인 필요)
 * PushUtil.PushRequest request = PushUtil.builder()
 *     .serviceId("NEST")
 *     .userNo(1001L)
 *     .title("특별 할인!")
 *     .content("최대 50% 할인 이벤트")
 *     .marketingConsent()
 *     .build();
 * }</pre>
 *
 * <h3>2. 템플릿 푸시 발송:</h3>
 * <pre>{@code
 * // 개인 발송
 * PushUtil.TemplatePushRequest request = PushUtil.templateBuilder()
 *     .serviceId("NEST")
 *     .templateCode("ORDER_COMPLETE")
 *     .userNo(1001L)
 *     .variable("orderNo", "12345")
 *     .variable("deliveryDate", "2025-12-20")
 *     .build();
 *
 * TemplatePushResult result = pushClient.sendTemplate(request);
 * if (result.isSuccess()) {
 *     int successCount = result.getSuccessCount();
 * }
 *
 * // 다중 발송
 * PushUtil.TemplatePushRequest request = PushUtil.templateBuilder()
 *     .serviceId("NEST")
 *     .templateCode("MARKETING_EVENT")
 *     .userNos(1001L, 1002L, 1003L)
 *     .variable("eventName", "연말 할인")
 *     .build();
 *
 * // 전체 발송
 * PushUtil.TemplatePushRequest request = PushUtil.templateBuilder()
 *     .serviceId("NEST")
 *     .templateCode("SYSTEM_NOTICE")
 *     .sendAll()
 *     .variable("noticeTitle", "서버 점검 안내")
 *     .skipConsentCheck()
 *     .build();
 * }</pre>
 *
 * <h3>3. 토큰 저장:</h3>
 * <pre>{@code
 * PushUtil.TokenRequest request = PushUtil.tokenBuilder()
 *     .serviceId("NEST")
 *     .userNo(1001L)
 *     .pushToken("fCm_token_xxx...")
 *     .deviceId("abc123def456")
 *     .android()                    // 또는 .ios(), .web()
 *     .deviceModel("Galaxy S24")
 *     .osVersion("14")
 *     .appVersion("1.0.0")
 *     .build();
 *
 * TokenSaveResult result = pushClient.saveToken(request);
 * }</pre>
 *
 * <h3>4. 커스텀 API 호출 (Map 기반):</h3>
 * <p>새로운 API가 추가되거나 커스텀 필드가 필요한 경우 사용합니다.</p>
 * <pre>{@code
 * Map<String, Object> customRequest = new LinkedHashMap<>();
 * customRequest.put("serviceId", "NEST");
 * customRequest.put("userNo", 1001L);
 * customRequest.put("customField", "customValue");
 *
 * GenericResult result = pushClient.sendCustom("/api/v2/push/custom-endpoint", customRequest);
 * if (result.isSuccess()) {
 *     Long pushId = result.getLong("pushId");
 *     String status = result.getString("status");
 * }
 * }</pre>
 *
 * <h3>5. 외부 URL 직접 호출:</h3>
 * <pre>{@code
 * GenericResult result = pushClient.sendToUrl(
 *     "http://other-service:8080/api/v2/custom",
 *     customRequest,
 *     accessToken
 * );
 * }</pre>
 *
 * <h3>6. 편의 메서드 (정적 메서드):</h3>
 * <pre>{@code
 * // 간단한 정보 알림
 * PushUtil.PushRequest request = PushUtil.info("NEST", 1001L, "새 메시지가 도착했습니다.");
 *
 * // 경고 알림
 * PushUtil.PushRequest request = PushUtil.warning("NEST", 1001L, "주의가 필요합니다.");
 *
 * // 위험 알림
 * PushUtil.PushRequest request = PushUtil.danger("NEST", 1001L, "긴급 상황입니다!");
 *
 * // 시스템 알림
 * PushUtil.PushRequest request = PushUtil.system("NEST", 1001L, "점검 안내", "서버 점검 예정");
 *
 * // 마케팅 알림
 * PushUtil.PushRequest request = PushUtil.marketing("NEST", 1001L, "이벤트", "특별 할인!");
 * }</pre>
 */
@Slf4j
public class PushClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final GatewaySignatureValidator signatureValidator;

    private static final String PUSH_SEND_PATH = "/api/v2/push/send";
    private static final String TOKEN_SAVE_PATH = "/api/v2/push/token/save";
    private static final String TEMPLATE_SEND_PATH = "/api/v2/push/template/send";

    /**
     * 생성자 (GatewaySignatureValidator 포함)
     *
     * @param restTemplate RestTemplate 인스턴스
     * @param baseUrl 푸시 서비스 기본 URL (예: http://wizmessage:8098)
     * @param signatureValidator Gateway 서명 검증/생성기
     */
    public PushClient(RestTemplate restTemplate, String baseUrl, GatewaySignatureValidator signatureValidator) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.signatureValidator = signatureValidator;
    }

    /**
     * 생성자 (하위 호환성 유지)
     *
     * @param restTemplate RestTemplate 인스턴스
     * @param baseUrl 푸시 서비스 기본 URL
     * @deprecated signatureValidator를 포함하는 생성자 사용 권장
     */
    @Deprecated
    public PushClient(RestTemplate restTemplate, String baseUrl) {
        this(restTemplate, baseUrl, null);
    }

    /**
     * 기본 URL 반환
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    // ========================================
    // 일반 푸시 발송
    // ========================================

    /**
     * 푸시 발송
     *
     * @param request 푸시 요청
     * @return 발송 결과
     */
    public PushResult send(PushUtil.PushRequest request) {
        return send(request, null);
    }

    /**
     * 푸시 발송 (인증 토큰 포함)
     *
     * @param request 푸시 요청
     * @param accessToken 접근 토큰 (Gateway 인증용)
     * @return 발송 결과
     */
    public PushResult send(PushUtil.PushRequest request, String accessToken) {
        String url = baseUrl + PUSH_SEND_PATH;

        try {
            HttpHeaders headers = createHeaders(accessToken, "POST", PUSH_SEND_PATH);
            HttpEntity<PushUtil.PushRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    ApiResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ApiResponse body = response.getBody();
                Object data = body.getData();

                Long pushId = null;
                String status = null;

                if (data instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> dataMap = (java.util.Map<String, Object>) data;
                    pushId = dataMap.get("pushId") != null ? ((Number) dataMap.get("pushId")).longValue() : null;
                    status = (String) dataMap.get("status");
                }

                return PushResult.success(pushId, status, body.getMessage());
            }

            return PushResult.failure("PUSH_SEND_FAILED", "Unexpected response: " + response.getStatusCode());

        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            log.error("푸시 발송 HTTP 오류 - Status: {}, Body: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return handleHttpError(ex, "푸시 발송");

        } catch (ResourceAccessException ex) {
            log.error("푸시 서버 연결 실패 - url: {}", url, ex);
            return PushResult.failure("PUSH_SERVICE_UNAVAILABLE", "푸시 서버에 연결할 수 없습니다.");

        } catch (RestClientException ex) {
            log.error("푸시 발송 중 RestClient 예외 - url: {}", url, ex);
            return PushResult.failure("PUSH_SEND_ERROR", "푸시 발송 중 통신 오류: " + ex.getMessage());

        } catch (Exception ex) {
            log.error("푸시 발송 중 예상치 못한 예외 - url: {}", url, ex);
            return PushResult.failure("PUSH_SEND_ERROR", "푸시 발송 중 오류가 발생했습니다.");
        }
    }

    // ========================================
    // 템플릿 푸시 발송
    // ========================================

    /**
     * 템플릿 기반 푸시 발송
     *
     * @param request 템플릿 푸시 요청
     * @return 발송 결과
     */
    public TemplatePushResult sendTemplate(PushUtil.TemplatePushRequest request) {
        return sendTemplate(request, null);
    }

    /**
     * 템플릿 기반 푸시 발송 (인증 토큰 포함)
     *
     * @param request 템플릿 푸시 요청
     * @param accessToken 접근 토큰 (Gateway 인증용)
     * @return 발송 결과
     */
    public TemplatePushResult sendTemplate(PushUtil.TemplatePushRequest request, String accessToken) {
        String url = baseUrl + TEMPLATE_SEND_PATH;

        try {
            HttpHeaders headers = createHeaders(accessToken, "POST", TEMPLATE_SEND_PATH);
            HttpEntity<PushUtil.TemplatePushRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    ApiResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ApiResponse body = response.getBody();
                Object data = body.getData();

                if (data instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> dataMap = (java.util.Map<String, Object>) data;

                    String templateCode = (String) dataMap.get("templateCode");
                    String resolvedTitle = (String) dataMap.get("resolvedTitle");
                    String resolvedContent = (String) dataMap.get("resolvedContent");

                    Integer totalCount = dataMap.get("totalCount") != null ?
                            ((Number) dataMap.get("totalCount")).intValue() : 0;
                    Integer successCount = dataMap.get("successCount") != null ?
                            ((Number) dataMap.get("successCount")).intValue() : 0;
                    Integer failedCount = dataMap.get("failedCount") != null ?
                            ((Number) dataMap.get("failedCount")).intValue() : 0;
                    Integer skippedCount = dataMap.get("skippedCount") != null ?
                            ((Number) dataMap.get("skippedCount")).intValue() : 0;

                    List<PushResultItem> results = parseResults(dataMap.get("results"));

                    Long pushId = null;
                    String status = null;
                    if (!results.isEmpty()) {
                        PushResultItem firstItem = results.get(0);
                        pushId = firstItem.getPushId();
                        status = firstItem.getStatus();
                    }

                    return TemplatePushResult.success(
                            templateCode, resolvedTitle, resolvedContent,
                            totalCount, successCount, failedCount, skippedCount,
                            pushId, status, results, body.getMessage()
                    );
                }

                return TemplatePushResult.success(
                        null, null, null, 0, 0, 0, 0,
                        null, null, Collections.emptyList(), body.getMessage()
                );
            }

            return TemplatePushResult.failure("TEMPLATE_PUSH_SEND_FAILED", "Unexpected response: " + response.getStatusCode());

        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            log.error("템플릿 푸시 발송 HTTP 오류 - Status: {}, Body: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return handleHttpErrorForTemplate(ex, "템플릿 푸시 발송");

        } catch (ResourceAccessException ex) {
            log.error("푸시 서버 연결 실패 - url: {}", url, ex);
            return TemplatePushResult.failure("PUSH_SERVICE_UNAVAILABLE", "푸시 서버에 연결할 수 없습니다.");

        } catch (RestClientException ex) {
            log.error("템플릿 푸시 발송 중 RestClient 예외 - url: {}", url, ex);
            return TemplatePushResult.failure("TEMPLATE_PUSH_SEND_ERROR", "템플릿 푸시 발송 중 통신 오류: " + ex.getMessage());

        } catch (Exception ex) {
            log.error("템플릿 푸시 발송 중 예상치 못한 예외 - url: {}", url, ex);
            return TemplatePushResult.failure("TEMPLATE_PUSH_SEND_ERROR", "템플릿 푸시 발송 중 오류가 발생했습니다.");
        }
    }

    @SuppressWarnings("unchecked")
    private List<PushResultItem> parseResults(Object resultsObj) {
        if (resultsObj == null) {
            return Collections.emptyList();
        }

        if (!(resultsObj instanceof List)) {
            return Collections.emptyList();
        }

        List<?> resultsList = (List<?>) resultsObj;
        List<PushResultItem> items = new ArrayList<>();

        for (Object item : resultsList) {
            if (item instanceof java.util.Map) {
                java.util.Map<?, ?> itemMap = (java.util.Map<?, ?>) item;

                Long pushId = itemMap.get("pushId") != null ?
                        ((Number) itemMap.get("pushId")).longValue() : null;
                Long userNo = itemMap.get("userNo") != null ?
                        ((Number) itemMap.get("userNo")).longValue() : null;
                String status = (String) itemMap.get("status");
                String errorMessage = (String) itemMap.get("errorMessage");

                items.add(new PushResultItem(pushId, userNo, status, errorMessage));
            }
        }

        return items;
    }

    // ========================================
    // 토큰 저장
    // ========================================

    /**
     * 토큰 저장
     *
     * @param request 토큰 저장 요청
     * @return 저장 결과
     */
    public TokenSaveResult saveToken(PushUtil.TokenRequest request) {
        return saveToken(request, null);
    }

    /**
     * 토큰 저장 (인증 토큰 포함)
     *
     * @param request 토큰 저장 요청
     * @param accessToken 접근 토큰
     * @return 저장 결과
     */
    public TokenSaveResult saveToken(PushUtil.TokenRequest request, String accessToken) {
        String url = baseUrl + TOKEN_SAVE_PATH;

        try {
            HttpHeaders headers = createHeaders(accessToken, "POST", TOKEN_SAVE_PATH);
            HttpEntity<PushUtil.TokenRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    ApiResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                return TokenSaveResult.success("토큰이 성공적으로 저장되었습니다");
            }

            return TokenSaveResult.failure("TOKEN_SAVE_FAILED", "Unexpected response: " + response.getStatusCode());

        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            log.error("토큰 저장 HTTP 오류 - Status: {}, Body: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return handleHttpErrorForToken(ex, "토큰 저장");

        } catch (ResourceAccessException ex) {
            log.error("푸시 서버 연결 실패 - url: {}", url, ex);
            return TokenSaveResult.failure("PUSH_SERVICE_UNAVAILABLE", "푸시 서버에 연결할 수 없습니다.");

        } catch (RestClientException ex) {
            log.error("토큰 저장 중 RestClient 예외 - url: {}", url, ex);
            return TokenSaveResult.failure("TOKEN_SAVE_ERROR", "토큰 저장 중 통신 오류: " + ex.getMessage());

        } catch (Exception ex) {
            log.error("토큰 저장 중 예상치 못한 예외 - url: {}", url, ex);
            return TokenSaveResult.failure("TOKEN_SAVE_ERROR", "토큰 저장 중 오류가 발생했습니다.");
        }
    }

    // ========================================
    // 커스텀 API 호출 (범용)
    // ========================================

    /**
     * 커스텀 API 호출 (Map 기반 요청)
     *
     * <p>새로운 API 엔드포인트나 커스텀 필드가 필요한 경우 사용합니다.</p>
     *
     * <pre>{@code
     * Map<String, Object> request = new LinkedHashMap<>();
     * request.put("serviceId", "NEST");
     * request.put("userNo", 1001L);
     * request.put("content", "테스트 메시지");
     * request.put("customField", "customValue");
     *
     * GenericResult result = pushClient.sendCustom("/api/v2/push/send", request);
     * }</pre>
     *
     * @param apiPath API 경로 (예: /api/v2/push/send)
     * @param request 요청 데이터 (Map)
     * @return 범용 결과
     */
    public GenericResult sendCustom(String apiPath, Map<String, Object> request) {
        return sendCustom(apiPath, request, null);
    }

    /**
     * 커스텀 API 호출 (Map 기반 요청 + 인증 토큰)
     *
     * @param apiPath API 경로 (예: /api/v2/push/send)
     * @param request 요청 데이터 (Map)
     * @param accessToken 접근 토큰 (Gateway 인증용)
     * @return 범용 결과
     */
    public GenericResult sendCustom(String apiPath, Map<String, Object> request, String accessToken) {
        String url = baseUrl + apiPath;

        try {
            HttpHeaders headers = createHeaders(accessToken, "POST", apiPath);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            log.debug("커스텀 API 호출 - url: {}, request: {}", url, request);

            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    ApiResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ApiResponse body = response.getBody();
                Object data = body.getData();

                @SuppressWarnings("unchecked")
                Map<String, Object> dataMap = data instanceof Map ? (Map<String, Object>) data : null;

                return GenericResult.success(dataMap, body.getMessage());
            }

            return GenericResult.failure("CUSTOM_API_FAILED", "Unexpected response: " + response.getStatusCode());

        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            log.error("커스텀 API 호출 HTTP 오류 - url: {}, Status: {}, Body: {}",
                    url, ex.getStatusCode(), ex.getResponseBodyAsString());
            return handleHttpErrorForGeneric(ex, "커스텀 API 호출");

        } catch (ResourceAccessException ex) {
            log.error("푸시 서버 연결 실패 - url: {}", url, ex);
            return GenericResult.failure("PUSH_SERVICE_UNAVAILABLE", "푸시 서버에 연결할 수 없습니다.");

        } catch (RestClientException ex) {
            log.error("커스텀 API 호출 중 RestClient 예외 - url: {}", url, ex);
            return GenericResult.failure("CUSTOM_API_ERROR", "커스텀 API 호출 중 통신 오류: " + ex.getMessage());

        } catch (Exception ex) {
            log.error("커스텀 API 호출 중 예상치 못한 예외 - url: {}", url, ex);
            return GenericResult.failure("CUSTOM_API_ERROR", "커스텀 API 호출 중 오류가 발생했습니다.");
        }
    }

    /**
     * 커스텀 API 호출 (Object 기반 요청)
     *
     * @param apiPath API 경로 (예: /api/v2/push/custom)
     * @param request 요청 객체 (DTO)
     * @return 범용 결과
     */
    public GenericResult sendCustom(String apiPath, Object request) {
        return sendCustom(apiPath, request, null);
    }

    /**
     * 커스텀 API 호출 (Object 기반 요청 + 인증 토큰)
     *
     * @param apiPath API 경로 (예: /api/v2/push/custom)
     * @param request 요청 객체 (DTO)
     * @param accessToken 접근 토큰 (Gateway 인증용)
     * @return 범용 결과
     */
    public GenericResult sendCustom(String apiPath, Object request, String accessToken) {
        String url = baseUrl + apiPath;

        try {
            HttpHeaders headers = createHeaders(accessToken, "POST", apiPath);
            HttpEntity<Object> entity = new HttpEntity<>(request, headers);

            log.debug("커스텀 API 호출 - url: {}, requestType: {}", url, request.getClass().getSimpleName());

            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    ApiResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ApiResponse body = response.getBody();
                Object data = body.getData();

                @SuppressWarnings("unchecked")
                Map<String, Object> dataMap = data instanceof Map ? (Map<String, Object>) data : null;

                return GenericResult.success(dataMap, body.getMessage());
            }

            return GenericResult.failure("CUSTOM_API_FAILED", "Unexpected response: " + response.getStatusCode());

        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            log.error("커스텀 API 호출 HTTP 오류 - url: {}, Status: {}, Body: {}",
                    url, ex.getStatusCode(), ex.getResponseBodyAsString());
            return handleHttpErrorForGeneric(ex, "커스텀 API 호출");

        } catch (ResourceAccessException ex) {
            log.error("푸시 서버 연결 실패 - url: {}", url, ex);
            return GenericResult.failure("PUSH_SERVICE_UNAVAILABLE", "푸시 서버에 연결할 수 없습니다.");

        } catch (RestClientException ex) {
            log.error("커스텀 API 호출 중 RestClient 예외 - url: {}", url, ex);
            return GenericResult.failure("CUSTOM_API_ERROR", "커스텀 API 호출 중 통신 오류: " + ex.getMessage());

        } catch (Exception ex) {
            log.error("커스텀 API 호출 중 예상치 못한 예외 - url: {}", url, ex);
            return GenericResult.failure("CUSTOM_API_ERROR", "커스텀 API 호출 중 오류가 발생했습니다.");
        }
    }

    /**
     * 완전한 URL로 커스텀 API 호출 (외부 서비스 호출 가능)
     *
     * @param fullUrl 전체 URL (예: http://other-service:8080/api/v2/custom)
     * @param request 요청 데이터 (Map)
     * @param accessToken 접근 토큰 (Gateway 인증용)
     * @return 범용 결과
     */
    public GenericResult sendToUrl(String fullUrl, Map<String, Object> request, String accessToken) {
        try {
            String apiPath = extractPath(fullUrl);

            HttpHeaders headers = createHeaders(accessToken, "POST", apiPath);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            log.debug("외부 URL API 호출 - url: {}, request: {}", fullUrl, request);

            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    fullUrl,
                    HttpMethod.POST,
                    entity,
                    ApiResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ApiResponse body = response.getBody();
                Object data = body.getData();

                @SuppressWarnings("unchecked")
                Map<String, Object> dataMap = data instanceof Map ? (Map<String, Object>) data : null;

                return GenericResult.success(dataMap, body.getMessage());
            }

            return GenericResult.failure("CUSTOM_API_FAILED", "Unexpected response: " + response.getStatusCode());

        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            log.error("외부 URL API 호출 HTTP 오류 - url: {}, Status: {}, Body: {}",
                    fullUrl, ex.getStatusCode(), ex.getResponseBodyAsString());
            return handleHttpErrorForGeneric(ex, "외부 URL API 호출");

        } catch (ResourceAccessException ex) {
            log.error("서버 연결 실패 - url: {}", fullUrl, ex);
            return GenericResult.failure("SERVICE_UNAVAILABLE", "서버에 연결할 수 없습니다.");

        } catch (RestClientException ex) {
            log.error("외부 URL API 호출 중 RestClient 예외 - url: {}", fullUrl, ex);
            return GenericResult.failure("CUSTOM_API_ERROR", "API 호출 중 통신 오류: " + ex.getMessage());

        } catch (Exception ex) {
            log.error("외부 URL API 호출 중 예상치 못한 예외 - url: {}", fullUrl, ex);
            return GenericResult.failure("CUSTOM_API_ERROR", "API 호출 중 오류가 발생했습니다.");
        }
    }

    private String extractPath(String fullUrl) {
        try {
            java.net.URL url = new java.net.URL(fullUrl);
            return url.getPath();
        } catch (Exception e) {
            return fullUrl;
        }
    }

    // ========================================
    // 에러 응답 처리 헬퍼 메서드
    // ========================================

    private PushResult handleHttpError(Exception ex, String operation) {
        String responseBody = "";
        if (ex instanceof HttpClientErrorException) {
            responseBody = ((HttpClientErrorException) ex).getResponseBodyAsString();
        } else if (ex instanceof HttpServerErrorException) {
            responseBody = ((HttpServerErrorException) ex).getResponseBodyAsString();
        }

        try {
            ErrorResponse errorResponse = parseErrorResponse(responseBody);
            String code = errorResponse.getCode() != null ? errorResponse.getCode() : "PUSH_SEND_FAILED";
            String message = errorResponse.getMessage() != null ? errorResponse.getMessage() : operation + " 실패";
            return PushResult.failure(code, message);
        } catch (Exception parseEx) {
            log.warn("에러 응답 파싱 실패", parseEx);
            return PushResult.failure("PUSH_SEND_FAILED", operation + " 실패");
        }
    }

    private TemplatePushResult handleHttpErrorForTemplate(Exception ex, String operation) {
        String responseBody = "";
        if (ex instanceof HttpClientErrorException) {
            responseBody = ((HttpClientErrorException) ex).getResponseBodyAsString();
        } else if (ex instanceof HttpServerErrorException) {
            responseBody = ((HttpServerErrorException) ex).getResponseBodyAsString();
        }

        try {
            ErrorResponse errorResponse = parseErrorResponse(responseBody);
            String code = errorResponse.getCode() != null ? errorResponse.getCode() : "TEMPLATE_PUSH_SEND_FAILED";
            String message = errorResponse.getMessage() != null ? errorResponse.getMessage() : operation + " 실패";
            return TemplatePushResult.failure(code, message);
        } catch (Exception parseEx) {
            log.warn("에러 응답 파싱 실패", parseEx);
            return TemplatePushResult.failure("TEMPLATE_PUSH_SEND_FAILED", operation + " 실패");
        }
    }

    private TokenSaveResult handleHttpErrorForToken(Exception ex, String operation) {
        String responseBody = "";
        if (ex instanceof HttpClientErrorException) {
            responseBody = ((HttpClientErrorException) ex).getResponseBodyAsString();
        } else if (ex instanceof HttpServerErrorException) {
            responseBody = ((HttpServerErrorException) ex).getResponseBodyAsString();
        }

        try {
            ErrorResponse errorResponse = parseErrorResponse(responseBody);
            String code = errorResponse.getCode() != null ? errorResponse.getCode() : "TOKEN_SAVE_FAILED";
            String message = errorResponse.getMessage() != null ? errorResponse.getMessage() : operation + " 실패";
            return TokenSaveResult.failure(code, message);
        } catch (Exception parseEx) {
            log.warn("에러 응답 파싱 실패", parseEx);
            return TokenSaveResult.failure("TOKEN_SAVE_FAILED", operation + " 실패");
        }
    }

    private GenericResult handleHttpErrorForGeneric(Exception ex, String operation) {
        String responseBody = "";
        if (ex instanceof HttpClientErrorException) {
            responseBody = ((HttpClientErrorException) ex).getResponseBodyAsString();
        } else if (ex instanceof HttpServerErrorException) {
            responseBody = ((HttpServerErrorException) ex).getResponseBodyAsString();
        }

        try {
            ErrorResponse errorResponse = parseErrorResponse(responseBody);
            String code = errorResponse.getCode() != null ? errorResponse.getCode() : "API_CALL_FAILED";
            String message = errorResponse.getMessage() != null ? errorResponse.getMessage() : operation + " 실패";
            return GenericResult.failure(code, message);
        } catch (Exception parseEx) {
            log.warn("에러 응답 파싱 실패", parseEx);
            return GenericResult.failure("API_CALL_FAILED", operation + " 실패");
        }
    }

    private ErrorResponse parseErrorResponse(String responseBody) {
        try {
            return JsonUtil.fromJson(responseBody, ErrorResponse.class);
        } catch (Exception e) {
            log.debug("ErrorResponse 파싱 실패, 원본 응답: {}", responseBody);
            return ErrorResponse.builder()
                    .code("PARSE_ERROR")
                    .message(responseBody)
                    .build();
        }
    }

    // ========================================
    // 헤더 생성
    // ========================================

    private HttpHeaders createHeaders(String accessToken, String method, String uri) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        if (signatureValidator != null) {
            try {
                String timestamp = signatureValidator.generateTimestamp();
                String signature = signatureValidator.generateSignature(method, uri, timestamp);

                headers.set("X-Gateway-Signature", signature);
                headers.set("X-Gateway-Timestamp", timestamp);
                // 서명 생성에 사용한 URI를 함께 전달 (Validator에서 동일한 URI로 검증하도록)
                headers.set("X-Gateway-Request-URI", uri);

                if (log.isDebugEnabled()) {
                    log.debug("[PushClient] 내부 서비스 호출용 서명 생성 완료 | method={} | uri={} | timestamp={} | signature={}...",
                            method, uri, timestamp, signature.substring(0, Math.min(10, signature.length())));
                }
            } catch (Exception e) {
                log.error("[PushClient] Gateway 헤더 생성 실패 | method={} | uri={}", method, uri, e);
            }
        } else {
            log.warn("[PushClient] Gateway 헤더 생성 불가 - signatureValidator가 null입니다. " +
                    "PushClient 생성 시 GatewaySignatureValidator를 주입하세요.");
        }

        if (accessToken != null && !accessToken.isEmpty()) {
            headers.setBearerAuth(accessToken);
        }

        return headers;
    }

    // ========================================
    // 결과 클래스 - 일반 푸시
    // ========================================

    public static class PushResult {
        private final boolean success;
        private final Long pushId;
        private final String status;
        private final String message;
        private final String code;
        private final String errorMessage;

        private PushResult(boolean success, Long pushId, String status, String message,
                           String code, String errorMessage) {
            this.success = success;
            this.pushId = pushId;
            this.status = status;
            this.message = message;
            this.code = code;
            this.errorMessage = errorMessage;
        }

        public static PushResult success(Long pushId, String status, String message) {
            return new PushResult(true, pushId, status, message, null, null);
        }

        public static PushResult failure(String errorCode, String errorMessage) {
            return new PushResult(false, null, null, null, errorCode, errorMessage);
        }

        public boolean isSuccess() { return success; }
        public Long getPushId() { return pushId; }
        public String getStatus() { return status; }
        public String getMessage() { return message; }
        public String getCode() { return code; }
        public String getErrorMessage() { return errorMessage; }

        @Override
        public String toString() {
            if (success) {
                return String.format("PushResult{success=true, pushId=%d, status='%s'}", pushId, status);
            } else {
                return String.format("PushResult{success=false, errorCode='%s', error='%s'}",
                        code, errorMessage);
            }
        }
    }

    // ========================================
    // 결과 클래스 - 개별 푸시 결과 항목
    // ========================================

    public static class PushResultItem {
        private final Long pushId;
        private final Long userNo;
        private final String status;
        private final String errorMessage;

        public PushResultItem(Long pushId, Long userNo, String status, String errorMessage) {
            this.pushId = pushId;
            this.userNo = userNo;
            this.status = status;
            this.errorMessage = errorMessage;
        }

        public Long getPushId() { return pushId; }
        public Long getUserNo() { return userNo; }
        public String getStatus() { return status; }
        public String getErrorMessage() { return errorMessage; }

        @Override
        public String toString() {
            return String.format("PushResultItem{pushId=%d, userNo=%d, status='%s'}",
                    pushId, userNo, status);
        }
    }

    // ========================================
    // 결과 클래스 - 템플릿 푸시
    // ========================================

    public static class TemplatePushResult {
        private final boolean success;
        private final String templateCode;
        private final String resolvedTitle;
        private final String resolvedContent;
        private final int totalCount;
        private final int successCount;
        private final int failedCount;
        private final int skippedCount;
        private final Long pushId;
        private final String status;
        private final List<PushResultItem> results;
        private final String message;
        private final String code;
        private final String errorMessage;

        private TemplatePushResult(boolean success, String templateCode, String resolvedTitle,
                                   String resolvedContent, int totalCount, int successCount,
                                   int failedCount, int skippedCount, Long pushId, String status,
                                   List<PushResultItem> results, String message,
                                   String errorCode, String errorMessage) {
            this.success = success;
            this.templateCode = templateCode;
            this.resolvedTitle = resolvedTitle;
            this.resolvedContent = resolvedContent;
            this.totalCount = totalCount;
            this.successCount = successCount;
            this.failedCount = failedCount;
            this.skippedCount = skippedCount;
            this.pushId = pushId;
            this.status = status;
            this.results = results != null ? results : Collections.emptyList();
            this.message = message;
            this.code = errorCode;
            this.errorMessage = errorMessage;
        }

        public static TemplatePushResult success(String templateCode, String resolvedTitle,
                                                 String resolvedContent, int totalCount,
                                                 int successCount, int failedCount, int skippedCount,
                                                 Long pushId, String status,
                                                 List<PushResultItem> results, String message) {
            return new TemplatePushResult(true, templateCode, resolvedTitle, resolvedContent,
                    totalCount, successCount, failedCount, skippedCount, pushId, status,
                    results, message, null, null);
        }

        public static TemplatePushResult failure(String errorCode, String errorMessage) {
            return new TemplatePushResult(false, null, null, null, 0, 0, 0, 0,
                    null, null, Collections.emptyList(), null, errorCode, errorMessage);
        }

        public boolean isSuccess() { return success; }
        public String getTemplateCode() { return templateCode; }
        public String getResolvedTitle() { return resolvedTitle; }
        public String getResolvedContent() { return resolvedContent; }
        public int getTotalCount() { return totalCount; }
        public int getSuccessCount() { return successCount; }
        public int getFailedCount() { return failedCount; }
        public int getSkippedCount() { return skippedCount; }
        public Long getPushId() { return pushId; }
        public String getStatus() { return status; }
        public List<PushResultItem> getResults() { return results; }
        public String getMessage() { return message; }
        public String getCode() { return code; }
        public String getErrorMessage() { return errorMessage; }

        @Override
        public String toString() {
            if (success) {
                return String.format(
                        "TemplatePushResult{success=true, template='%s', pushId=%d, status='%s', total=%d, success=%d, failed=%d, skipped=%d}",
                        templateCode, pushId, status, totalCount, successCount, failedCount, skippedCount
                );
            } else {
                return String.format("TemplatePushResult{success=false, errorCode='%s', error='%s'}",
                        code, errorMessage);
            }
        }
    }

    // ========================================
    // 결과 클래스 - 토큰 저장
    // ========================================

    public static class TokenSaveResult {
        private final boolean success;
        private final String message;
        private final String code;
        private final String errorMessage;

        private TokenSaveResult(boolean success, String message, String errorCode, String errorMessage) {
            this.success = success;
            this.message = message;
            this.code = errorCode;
            this.errorMessage = errorMessage;
        }

        public static TokenSaveResult success(String message) {
            return new TokenSaveResult(true, message, null, null);
        }

        public static TokenSaveResult failure(String errorCode, String errorMessage) {
            return new TokenSaveResult(false, null, errorCode, errorMessage);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getCode() { return code; }
        public String getErrorMessage() { return errorMessage; }

        @Override
        public String toString() {
            if (success) {
                return String.format("TokenSaveResult{success=true, message='%s'}", message);
            } else {
                return String.format("TokenSaveResult{success=false, errorCode='%s', error='%s'}",
                        code, errorMessage);
            }
        }
    }

    // ========================================
    // 결과 클래스 - 범용
    // ========================================

    /**
     * 범용 API 호출 결과
     */
    public static class GenericResult {
        private final boolean success;
        private final Map<String, Object> data;
        private final String message;
        private final String code;
        private final String errorMessage;

        private GenericResult(boolean success, Map<String, Object> data, String message,
                              String errorCode, String errorMessage) {
            this.success = success;
            this.data = data;
            this.message = message;
            this.code = errorCode;
            this.errorMessage = errorMessage;
        }

        public static GenericResult success(Map<String, Object> data, String message) {
            return new GenericResult(true, data, message, null, null);
        }

        public static GenericResult failure(String errorCode, String errorMessage) {
            return new GenericResult(false, null, null, errorCode, errorMessage);
        }

        public boolean isSuccess() { return success; }
        public Map<String, Object> getData() { return data; }
        public String getMessage() { return message; }
        public String getCode() { return code; }
        public String getErrorMessage() { return errorMessage; }

        public Long getLong(String key) {
            if (data == null || !data.containsKey(key)) return null;
            Object value = data.get(key);
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return null;
        }

        public String getString(String key) {
            if (data == null || !data.containsKey(key)) return null;
            Object value = data.get(key);
            return value != null ? value.toString() : null;
        }

        public Integer getInt(String key) {
            if (data == null || !data.containsKey(key)) return null;
            Object value = data.get(key);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return null;
        }

        @Override
        public String toString() {
            if (success) {
                return String.format("GenericResult{success=true, data=%s}", data);
            } else {
                return String.format("GenericResult{success=false, errorCode='%s', error='%s'}",
                        code, errorMessage);
            }
        }
    }
}