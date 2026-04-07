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

import java.util.Map;

/**
 * 이메일 서비스 클라이언트
 *
 * <p>다른 마이크로서비스에서 WizMessage 이메일 서비스를 호출할 때 사용합니다.</p>
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
 * public class EmailClientConfig {
 *     @Value("${microservice.message.url:http://wizmessage:8098}")
 *     private String messageServiceUrl;
 *
 *     @Bean
 *     public EmailClient emailClient(RestTemplate restTemplate,
 *                                     GatewaySignatureValidator signatureValidator) {
 *         return new EmailClient(restTemplate, messageServiceUrl, signatureValidator);
 *     }
 * }
 * }</pre>
 *
 * <h3>1. 일반 이메일 발송:</h3>
 * <pre>{@code
 * // HTML 이메일 발송
 * EmailUtil.EmailRequest request = EmailUtil.builder()
 *     .serviceId("NEST")
 *     .recipient("user@example.com")
 *     .subject("환영합니다")
 *     .content("<h1>회원가입을 환영합니다!</h1>")
 *     .html()
 *     .build();
 *
 * EmailResult result = emailClient.send(request);
 * if (result.isSuccess()) {
 *     Long emailId = result.getEmailId();
 * }
 * }</pre>
 *
 * <h3>2. 템플릿 이메일 발송:</h3>
 * <pre>{@code
 * EmailUtil.TemplateEmailRequest request = EmailUtil.templateBuilder()
 *     .serviceId("NEST")
 *     .templateCode("WELCOME")
 *     .recipient("user@example.com")
 *     .variable("userName", "홍길동")
 *     .variable("serviceName", "WIZ Platform")
 *     .build();
 *
 * EmailResult result = emailClient.sendTemplate(request);
 * }</pre>
 *
 * <h3>3. 인증 이메일 발송:</h3>
 * <pre>{@code
 * // 기본 인증 이메일
 * EmailUtil.VerifyEmailRequest request = EmailUtil.verifyBuilder()
 *     .serviceId("NEST")
 *     .recipient("user@example.com")
 *     .verifyPurpose("EMAIL_VERIFY")
 *     .templateCode("EMAIL_VERIFICATION")
 *     .variable("nickname", "홍길동")
 *     .build();
 *
 * VerifyEmailResult result = emailClient.sendVerify(request);
 * if (result.isSuccess()) {
 *     Long verifyId = result.getVerifyId();
 * }
 *
 * // USER 회원 인증 이메일 (tb_verification에 memberType, memberNo 저장)
 * EmailUtil.VerifyEmailRequest request = EmailUtil.verifyBuilder()
 *     .serviceId("NEST")
 *     .recipient("user@example.com")
 *     .verifyPurpose("EMAIL_VERIFY")
 *     .templateCode("EMAIL_VERIFICATION")
 *     .memberUser(1001L)          // memberType=USER, memberNo=1001
 *     .customCode("123456")       // 인증 코드 직접 지정 (선택)
 *     .expireMinutes(5)           // 만료 시간 5분 (선택)
 *     .build();
 *
 * // ADMIN 회원 인증 이메일
 * EmailUtil.VerifyEmailRequest request = EmailUtil.verifyBuilder()
 *     .serviceId("NEST")
 *     .recipient("admin@example.com")
 *     .verifyPurpose("ADMIN_TEMP_PASSWORD")
 *     .templateCode("ADMIN_TEMP_PASSWORD")
 *     .memberAdmin(100L)          // memberType=ADMIN, memberNo=100
 *     .createdBy(1L)              // 생성자 관리자 번호
 *     .build();
 * }</pre>
 *
 * <h3>4. 커스텀 API 호출 (Map 기반):</h3>
 * <p>새로운 API가 추가되거나 커스텀 필드가 필요한 경우 사용합니다.</p>
 * <pre>{@code
 * Map<String, Object> customRequest = new LinkedHashMap<>();
 * customRequest.put("serviceId", "NEST");
 * customRequest.put("recipient", "user@example.com");
 * customRequest.put("customField", "customValue");
 *
 * GenericResult result = emailClient.sendCustom("/api/v2/email/custom-endpoint", customRequest);
 * if (result.isSuccess()) {
 *     Long id = result.getLong("someId");
 *     String status = result.getString("status");
 * }
 * }</pre>
 *
 * <h3>5. 외부 URL 직접 호출:</h3>
 * <pre>{@code
 * GenericResult result = emailClient.sendToUrl(
 *     "http://other-service:8080/api/v2/custom",
 *     customRequest,
 *     accessToken
 * );
 * }</pre>
 */
@Slf4j
public class EmailClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final GatewaySignatureValidator signatureValidator;

    private static final String EMAIL_SEND_PATH = "/api/v2/email/send";
    private static final String EMAIL_TEMPLATE_SEND_PATH = "/api/v2/email/template/send";
    private static final String EMAIL_VERIFY_SEND_PATH = "/api/v2/email/verify/send";

    /**
     * 생성자 (GatewaySignatureValidator 포함)
     *
     * @param restTemplate RestTemplate 인스턴스
     * @param baseUrl 이메일 서비스 기본 URL (예: http://wizmessage:8098)
     * @param signatureValidator Gateway 서명 검증/생성기
     */
    public EmailClient(RestTemplate restTemplate, String baseUrl, GatewaySignatureValidator signatureValidator) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.signatureValidator = signatureValidator;
    }

    /**
     * 생성자 (하위 호환성 유지)
     *
     * @param restTemplate RestTemplate 인스턴스
     * @param baseUrl 이메일 서비스 기본 URL
     * @deprecated signatureValidator를 포함하는 생성자 사용 권장
     */
    @Deprecated
    public EmailClient(RestTemplate restTemplate, String baseUrl) {
        this(restTemplate, baseUrl, null);
    }

    /**
     * 기본 URL 반환
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    // ========================================
    // 일반 이메일 발송
    // ========================================

    /**
     * 이메일 발송
     *
     * @param request 이메일 요청
     * @return 발송 결과
     */
    public EmailResult send(EmailUtil.EmailRequest request) {
        return send(request, null);
    }

    /**
     * 이메일 발송 (인증 토큰 포함)
     *
     * @param request 이메일 요청
     * @param accessToken 접근 토큰 (Gateway 인증용)
     * @return 발송 결과
     */
    public EmailResult send(EmailUtil.EmailRequest request, String accessToken) {
        String url = baseUrl + EMAIL_SEND_PATH;

        try {
            HttpHeaders headers = createHeaders(accessToken, "POST", EMAIL_SEND_PATH);
            HttpEntity<EmailUtil.EmailRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    ApiResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ApiResponse body = response.getBody();
                Object data = body.getData();

                Long emailId = null;
                String status = null;

                if (data instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> dataMap = (java.util.Map<String, Object>) data;
                    emailId = dataMap.get("emailId") != null ? ((Number) dataMap.get("emailId")).longValue() : null;
                    status = (String) dataMap.get("status");
                }

                return EmailResult.success(emailId, status, body.getMessage());
            }

            return EmailResult.failure("EMAIL_SEND_FAILED", "Unexpected response: " + response.getStatusCode());

        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            log.error("이메일 발송 HTTP 오류 - Status: {}, Body: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return handleHttpError(ex, "이메일 발송");

        } catch (ResourceAccessException ex) {
            log.error("이메일 서버 연결 실패 - url: {}", url, ex);
            return EmailResult.failure("EMAIL_SERVICE_UNAVAILABLE", "이메일 서버에 연결할 수 없습니다.");

        } catch (RestClientException ex) {
            log.error("이메일 발송 중 RestClient 예외 - url: {}", url, ex);
            return EmailResult.failure("EMAIL_SEND_ERROR", "이메일 발송 중 통신 오류: " + ex.getMessage());

        } catch (Exception ex) {
            log.error("이메일 발송 중 예상치 못한 예외 - url: {}", url, ex);
            return EmailResult.failure("EMAIL_SEND_ERROR", "이메일 발송 중 오류가 발생했습니다.");
        }
    }

    // ========================================
    // 템플릿 이메일 발송
    // ========================================

    /**
     * 템플릿 기반 이메일 발송
     *
     * @param request 템플릿 이메일 요청
     * @return 발송 결과
     */
    public EmailResult sendTemplate(EmailUtil.TemplateEmailRequest request) {
        return sendTemplate(request, null);
    }

    /**
     * 템플릿 기반 이메일 발송 (인증 토큰 포함)
     *
     * @param request 템플릿 이메일 요청
     * @param accessToken 접근 토큰 (Gateway 인증용)
     * @return 발송 결과
     */
    public EmailResult sendTemplate(EmailUtil.TemplateEmailRequest request, String accessToken) {
        String url = baseUrl + EMAIL_TEMPLATE_SEND_PATH;

        try {
            HttpHeaders headers = createHeaders(accessToken, "POST", EMAIL_TEMPLATE_SEND_PATH);
            HttpEntity<EmailUtil.TemplateEmailRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    ApiResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ApiResponse body = response.getBody();
                Object data = body.getData();

                Long emailId = null;
                String status = null;

                if (data instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> dataMap = (java.util.Map<String, Object>) data;
                    emailId = dataMap.get("emailId") != null ? ((Number) dataMap.get("emailId")).longValue() : null;
                    status = (String) dataMap.get("status");
                }

                return EmailResult.success(emailId, status, body.getMessage());
            }

            return EmailResult.failure("TEMPLATE_EMAIL_SEND_FAILED", "Unexpected response: " + response.getStatusCode());

        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            log.error("템플릿 이메일 발송 HTTP 오류 - Status: {}, Body: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return handleHttpError(ex, "템플릿 이메일 발송");

        } catch (ResourceAccessException ex) {
            log.error("이메일 서버 연결 실패 - url: {}", url, ex);
            return EmailResult.failure("EMAIL_SERVICE_UNAVAILABLE", "이메일 서버에 연결할 수 없습니다.");

        } catch (RestClientException ex) {
            log.error("템플릿 이메일 발송 중 RestClient 예외 - url: {}", url, ex);
            return EmailResult.failure("TEMPLATE_EMAIL_SEND_ERROR", "템플릿 이메일 발송 중 통신 오류: " + ex.getMessage());

        } catch (Exception ex) {
            log.error("템플릿 이메일 발송 중 예상치 못한 예외 - url: {}", url, ex);
            return EmailResult.failure("TEMPLATE_EMAIL_SEND_ERROR", "템플릿 이메일 발송 중 오류가 발생했습니다.");
        }
    }

    // ========================================
    // 인증 이메일 발송
    // ========================================

    /**
     * 인증 이메일 발송
     *
     * @param request 인증 이메일 요청
     * @return 인증 발송 결과
     */
    public VerifyEmailResult sendVerify(EmailUtil.VerifyEmailRequest request) {
        return sendVerify(request, null);
    }

    /**
     * 인증 이메일 발송 (인증 토큰 포함)
     *
     * @param request 인증 이메일 요청
     * @param accessToken 접근 토큰 (Gateway 인증용)
     * @return 인증 발송 결과
     */
    public VerifyEmailResult sendVerify(EmailUtil.VerifyEmailRequest request, String accessToken) {
        String url = baseUrl + EMAIL_VERIFY_SEND_PATH;

        try {
            HttpHeaders headers = createHeaders(accessToken, "POST", EMAIL_VERIFY_SEND_PATH);
            HttpEntity<EmailUtil.VerifyEmailRequest> entity = new HttpEntity<>(request, headers);

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

                    Long verifyId = dataMap.get("verifyId") != null ?
                            ((Number) dataMap.get("verifyId")).longValue() : null;
                    Long emailId = dataMap.get("emailId") != null ?
                            ((Number) dataMap.get("emailId")).longValue() : null;
                    String recipient = (String) dataMap.get("recipient");
                    String verifyPurpose = (String) dataMap.get("verifyPurpose");

                    return VerifyEmailResult.success(verifyId, emailId, recipient, verifyPurpose, body.getMessage());
                }
            }

            return VerifyEmailResult.failure("VERIFY_EMAIL_SEND_FAILED", "Unexpected response: " + response.getStatusCode());

        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            log.error("인증 이메일 발송 HTTP 오류 - Status: {}, Body: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return handleHttpErrorForVerify(ex, "인증 이메일 발송");

        } catch (ResourceAccessException ex) {
            log.error("이메일 서버 연결 실패 - url: {}", url, ex);
            return VerifyEmailResult.failure("EMAIL_SERVICE_UNAVAILABLE", "이메일 서버에 연결할 수 없습니다.");

        } catch (RestClientException ex) {
            log.error("인증 이메일 발송 중 RestClient 예외 - url: {}", url, ex);
            return VerifyEmailResult.failure("VERIFY_EMAIL_SEND_ERROR", "인증 이메일 발송 중 통신 오류: " + ex.getMessage());

        } catch (Exception ex) {
            log.error("인증 이메일 발송 중 예상치 못한 예외 - url: {}", url, ex);
            return VerifyEmailResult.failure("VERIFY_EMAIL_SEND_ERROR", "인증 이메일 발송 중 오류가 발생했습니다.");
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
     * request.put("recipient", "user@example.com");
     * request.put("verifyPurpose", "EMAIL_VERIFY");
     * request.put("memberType", "USER");
     * request.put("memberNo", 1001L);
     * request.put("customField", "customValue");
     *
     * GenericResult result = emailClient.sendCustom("/api/v2/email/verify/send", request);
     * }</pre>
     *
     * @param apiPath API 경로 (예: /api/v2/email/verify/send)
     * @param request 요청 데이터 (Map)
     * @return 범용 결과
     */
    public GenericResult sendCustom(String apiPath, Map<String, Object> request) {
        return sendCustom(apiPath, request, null);
    }

    /**
     * 커스텀 API 호출 (Map 기반 요청 + 인증 토큰)
     *
     * @param apiPath API 경로 (예: /api/v2/email/verify/send)
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
            log.error("이메일 서버 연결 실패 - url: {}", url, ex);
            return GenericResult.failure("EMAIL_SERVICE_UNAVAILABLE", "이메일 서버에 연결할 수 없습니다.");

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
     * <p>직접 정의한 DTO 클래스를 사용하여 요청할 수 있습니다.</p>
     *
     * @param apiPath API 경로 (예: /api/v2/email/custom)
     * @param request 요청 객체 (DTO)
     * @return 범용 결과
     */
    public GenericResult sendCustom(String apiPath, Object request) {
        return sendCustom(apiPath, request, null);
    }

    /**
     * 커스텀 API 호출 (Object 기반 요청 + 인증 토큰)
     *
     * @param apiPath API 경로 (예: /api/v2/email/custom)
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
            log.error("이메일 서버 연결 실패 - url: {}", url, ex);
            return GenericResult.failure("EMAIL_SERVICE_UNAVAILABLE", "이메일 서버에 연결할 수 없습니다.");

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
     * <p>baseUrl을 사용하지 않고 직접 전체 URL을 지정합니다.</p>
     *
     * @param fullUrl 전체 URL (예: http://other-service:8080/api/v2/custom)
     * @param request 요청 데이터 (Map)
     * @param accessToken 접근 토큰 (Gateway 인증용)
     * @return 범용 결과
     */
    public GenericResult sendToUrl(String fullUrl, Map<String, Object> request, String accessToken) {
        try {
            // URL에서 경로 추출 (서명 생성용)
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

    /**
     * URL에서 경로 추출
     */
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

    /**
     * HTTP 에러 처리 (일반 이메일용)
     */
    private EmailResult handleHttpError(Exception ex, String operation) {
        String responseBody = "";
        if (ex instanceof HttpClientErrorException) {
            responseBody = ((HttpClientErrorException) ex).getResponseBodyAsString();
        } else if (ex instanceof HttpServerErrorException) {
            responseBody = ((HttpServerErrorException) ex).getResponseBodyAsString();
        }

        try {
            ErrorResponse errorResponse = parseErrorResponse(responseBody);
            String code = errorResponse.getCode() != null ? errorResponse.getCode() : "EMAIL_SEND_FAILED";
            String message = errorResponse.getMessage() != null ? errorResponse.getMessage() : operation + " 실패";
            return EmailResult.failure(code, message);
        } catch (Exception parseEx) {
            log.warn("에러 응답 파싱 실패", parseEx);
            return EmailResult.failure("EMAIL_SEND_FAILED", operation + " 실패");
        }
    }

    /**
     * HTTP 에러 처리 (인증 이메일용)
     */
    private VerifyEmailResult handleHttpErrorForVerify(Exception ex, String operation) {
        String responseBody = "";
        if (ex instanceof HttpClientErrorException) {
            responseBody = ((HttpClientErrorException) ex).getResponseBodyAsString();
        } else if (ex instanceof HttpServerErrorException) {
            responseBody = ((HttpServerErrorException) ex).getResponseBodyAsString();
        }

        try {
            ErrorResponse errorResponse = parseErrorResponse(responseBody);
            String code = errorResponse.getCode() != null ? errorResponse.getCode() : "VERIFY_EMAIL_SEND_FAILED";
            String message = errorResponse.getMessage() != null ? errorResponse.getMessage() : operation + " 실패";
            return VerifyEmailResult.failure(code, message);
        } catch (Exception parseEx) {
            log.warn("에러 응답 파싱 실패", parseEx);
            return VerifyEmailResult.failure("VERIFY_EMAIL_SEND_FAILED", operation + " 실패");
        }
    }

    /**
     * HTTP 에러 처리 (범용)
     */
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

    /**
     * 에러 응답 파싱
     */
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

    /**
     * HTTP 헤더 생성
     */
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
                    log.debug("[EmailClient] 내부 서비스 호출용 서명 생성 완료 | method={} | uri={} | timestamp={} | signature={}...",
                            method, uri, timestamp, signature.substring(0, Math.min(10, signature.length())));
                }
            } catch (Exception e) {
                log.error("[EmailClient] Gateway 헤더 생성 실패 | method={} | uri={}", method, uri, e);
            }
        } else {
            log.warn("[EmailClient] Gateway 헤더 생성 불가 - signatureValidator가 null입니다. " +
                    "EmailClient 생성 시 GatewaySignatureValidator를 주입하세요.");
        }

        if (accessToken != null && !accessToken.isEmpty()) {
            headers.setBearerAuth(accessToken);
        }

        return headers;
    }

    // ========================================
    // 결과 클래스 - 일반 이메일
    // ========================================

    /**
     * 이메일 발송 결과
     */
    public static class EmailResult {
        private final boolean success;
        private final Long emailId;
        private final String status;
        private final String message;
        private final String code;
        private final String errorMessage;

        private EmailResult(boolean success, Long emailId, String status, String message,
                            String errorCode, String errorMessage) {
            this.success = success;
            this.emailId = emailId;
            this.status = status;
            this.message = message;
            this.code = errorCode;
            this.errorMessage = errorMessage;
        }

        public static EmailResult success(Long emailId, String status, String message) {
            return new EmailResult(true, emailId, status, message, null, null);
        }

        public static EmailResult failure(String errorCode, String errorMessage) {
            return new EmailResult(false, null, null, null, errorCode, errorMessage);
        }

        public boolean isSuccess() { return success; }
        public Long getEmailId() { return emailId; }
        public String getStatus() { return status; }
        public String getMessage() { return message; }
        public String getCode() { return code; }
        public String getErrorMessage() { return errorMessage; }

        @Override
        public String toString() {
            if (success) {
                return String.format("EmailResult{success=true, emailId=%d, status='%s'}", emailId, status);
            } else {
                return String.format("EmailResult{success=false, errorCode='%s', error='%s'}",
                        code, errorMessage);
            }
        }
    }

    // ========================================
    // 결과 클래스 - 인증 이메일
    // ========================================

    /**
     * 인증 이메일 발송 결과
     */
    public static class VerifyEmailResult {
        private final boolean success;
        private final Long verifyId;
        private final Long emailId;
        private final String recipient;
        private final String verifyPurpose;
        private final String message;
        private final String code;
        private final String errorMessage;

        private VerifyEmailResult(boolean success, Long verifyId, Long emailId,
                                  String recipient, String verifyPurpose,
                                  String message, String errorCode, String errorMessage) {
            this.success = success;
            this.verifyId = verifyId;
            this.emailId = emailId;
            this.recipient = recipient;
            this.verifyPurpose = verifyPurpose;
            this.message = message;
            this.code = errorCode;
            this.errorMessage = errorMessage;
        }

        public static VerifyEmailResult success(Long verifyId, Long emailId,
                                                String recipient, String verifyPurpose,
                                                String message) {
            return new VerifyEmailResult(true, verifyId, emailId, recipient, verifyPurpose,
                    message, null, null);
        }

        public static VerifyEmailResult failure(String errorCode, String errorMessage) {
            return new VerifyEmailResult(false, null, null, null, null, null,
                    errorCode, errorMessage);
        }

        public boolean isSuccess() { return success; }
        public Long getVerifyId() { return verifyId; }
        public Long getEmailId() { return emailId; }
        public String getRecipient() { return recipient; }
        public String getVerifyPurpose() { return verifyPurpose; }
        public String getMessage() { return message; }
        public String getCode() { return code; }
        public String getErrorMessage() { return errorMessage; }

        @Override
        public String toString() {
            if (success) {
                return String.format(
                        "VerifyEmailResult{success=true, verifyId=%d, emailId=%d, recipient='%s', purpose='%s'}",
                        verifyId, emailId, recipient, verifyPurpose
                );
            } else {
                return String.format("VerifyEmailResult{success=false, errorCode='%s', error='%s'}",
                        code, errorMessage);
            }
        }
    }

    // ========================================
    // 결과 클래스 - 범용
    // ========================================

    /**
     * 범용 API 호출 결과
     *
     * <p>커스텀 API 호출 시 사용합니다.</p>
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

        /**
         * 데이터에서 특정 필드를 Long으로 가져오기
         */
        public Long getLong(String key) {
            if (data == null || !data.containsKey(key)) return null;
            Object value = data.get(key);
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return null;
        }

        /**
         * 데이터에서 특정 필드를 String으로 가져오기
         */
        public String getString(String key) {
            if (data == null || !data.containsKey(key)) return null;
            Object value = data.get(key);
            return value != null ? value.toString() : null;
        }

        /**
         * 데이터에서 특정 필드를 Integer로 가져오기
         */
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