package org.softwiz.platform.iot.common.lib.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 이메일 발송 요청 유틸리티
 *
 * <p>다른 서비스에서 WizMessage 이메일 서비스로 요청을 보낼 때 사용합니다.</p>
 *
 * <pre>
 * 사용 예시:
 * {@code
 * // 1. 간단한 HTML 이메일
 * EmailRequest request = EmailUtil.builder()
 *     .serviceId("NEST")
 *     .recipient("user@example.com")
 *     .subject("환영합니다")
 *     .content("<h1>회원가입을 환영합니다!</h1>")
 *     .html()
 *     .system()
 *     .build();
 *
 * // 2. 텍스트 이메일
 * EmailRequest request = EmailUtil.builder()
 *     .serviceId("NEST")
 *     .recipient("user@example.com")
 *     .subject("알림")
 *     .content("시스템 점검 안내입니다.")
 *     .text()
 *     .system()
 *     .build();
 *
 * // 3. 거래 이메일
 * EmailRequest request = EmailUtil.builder()
 *     .serviceId("NEST")
 *     .recipient("user@example.com")
 *     .recipientName("홍길동")
 *     .subject("주문 완료")
 *     .content("<p>주문이 완료되었습니다.</p>")
 *     .transaction()
 *     .senderName("주문팀")
 *     .build();
 *
 * // 4. 마케팅 이메일 (동의 체크 필요)
 * EmailRequest request = EmailUtil.builder()
 *     .serviceId("NEST")
 *     .recipient("user@example.com")
 *     .subject("특별 할인 이벤트")
 *     .content("<h2>최대 50% 할인!</h2>")
 *     .marketing()
 *     .build();
 *
 * // 5. 참조/숨은참조 포함
 * EmailRequest request = EmailUtil.builder()
 *     .serviceId("NEST")
 *     .recipient("user@example.com")
 *     .subject("회의 초대")
 *     .content("<p>회의에 초대합니다</p>")
 *     .system()
 *     .cc("manager@example.com")
 *     .bcc("admin@example.com")
 *     .build();
 *
 * // 6. 템플릿 기반 발송
 * TemplateEmailRequest request = EmailUtil.templateBuilder()
 *     .serviceId("NEST")
 *     .templateCode("WELCOME")
 *     .recipient("user@example.com")
 *     .recipientName("홍길동")
 *     .variable("userName", "홍길동")
 *     .variable("serviceName", "NEST")
 *     .build();
 *
 * // 7. 템플릿 + 추가 정보
 * TemplateEmailRequest request = EmailUtil.templateBuilder()
 *     .serviceId("NEST")
 *     .templateCode("ORDER_COMPLETE")
 *     .recipient("user@example.com")
 *     .variable("orderNo", "12345")
 *     .variable("orderDate", "2025-01-15")
 *     .variable("amount", "50,000원")
 *     .senderName("주문팀")
 *     .build();
 *
 * // 8. 인증 이메일 발송 (회원가입)
 * VerifyEmailRequest request = EmailUtil.verifyBuilder()
 *     .serviceId("NEST")
 *     .recipient("user@example.com")
 *     .recipientName("홍길동")
 *     .verifyPurpose(EmailUtil.VerifyPurpose.SIGNUP)
 *     .templateCode("VERIFY_SIGNUP")
 *     .variable("userName", "홍길동")
 *     .build();
 *
 * // 9. 인증 이메일 발송 (비밀번호 재설정)
 * VerifyEmailRequest request = EmailUtil.verifyBuilder()
 *     .serviceId("NEST")
 *     .recipient("user@example.com")
 *     .verifyPurpose(EmailUtil.VerifyPurpose.PASSWORD_RESET)
 *     .templateCode("VERIFY_PASSWORD_RESET")
 *     .build();
 *
 * // 10. 인증 이메일 발송 (커스텀 코드 지정)
 * VerifyEmailRequest request = EmailUtil.verifyBuilder()
 *     .serviceId("NEST")
 *     .recipient("user@example.com")
 *     .verifyPurpose(EmailUtil.VerifyPurpose.EMAIL_VERIFY)
 *     .templateCode("EMAIL_VERIFICATION")
 *     .customCode("123456")       // 직접 인증 코드 지정
 *     .expireMinutes(5)           // 만료 시간 5분
 *     .variable("userName", "홍길동")
 *     .build();
 *
 * // 11. 인증 이메일 발송 (USER 회원 정보 포함)
 * VerifyEmailRequest request = EmailUtil.verifyBuilder()
 *     .serviceId("NEST")
 *     .recipient("user@example.com")
 *     .verifyPurpose(EmailUtil.VerifyPurpose.EMAIL_VERIFY)
 *     .templateCode("EMAIL_VERIFICATION")
 *     .memberUser(1001L)          // memberType=USER, memberNo=1001
 *     .variable("userName", "홍길동")
 *     .build();
 *
 * // 12. 인증 이메일 발송 (ADMIN 회원 정보 포함)
 * VerifyEmailRequest request = EmailUtil.verifyBuilder()
 *     .serviceId("NEST")
 *     .recipient("admin@example.com")
 *     .verifyPurpose(EmailUtil.VerifyPurpose.ADMIN_TEMP_PASSWORD)
 *     .templateCode("ADMIN_TEMP_PASSWORD")
 *     .memberAdmin(100L)          // memberType=ADMIN, memberNo=100
 *     .createdBy(1L)              // 생성자 (관리자 번호)
 *     .build();
 *
 * // 13. RestTemplate으로 발송
 * String emailServiceUrl = "http://wizmessage:8098/api/v2/email/send";
 * ApiResponse response = restTemplate.postForObject(emailServiceUrl, request, ApiResponse.class);
 *
 * // 14. 템플릿 발송
 * String templateEmailUrl = "http://wizmessage:8098/api/v2/email/template/send";
 * ApiResponse response = restTemplate.postForObject(templateEmailUrl, request, ApiResponse.class);
 *
 * // 15. 인증 이메일 발송
 * String verifyEmailUrl = "http://wizmessage:8098/api/v2/email/verify/send";
 * ApiResponse response = restTemplate.postForObject(verifyEmailUrl, request, ApiResponse.class);
 * }
 * </pre>
 */
@Slf4j
public class EmailUtil {

    private EmailUtil() {
        // Utility class
    }

    // ========================================
    // 상수
    // ========================================

    /**
     * 이메일 타입 (통일)
     */
    public enum EmailType {
        SYSTEM("SYSTEM"),           // 시스템 이메일
        TRANSACTION("TRANSACTION"), // 거래 이메일
        VERIFY("VERIFY"),           // 인증 이메일
        MARKETING("MARKETING");     // 마케팅 이메일

        private final String code;

        EmailType(String code) {
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
        EMAIL("EMAIL"),
        MARKETING_EMAIL("MARKETING_EMAIL"),
        NEWSLETTER("NEWSLETTER");

        private final String code;

        ConsentType(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    /**
     * 인증 목적 (verifyPurpose)
     */
    public enum VerifyPurpose {
        SIGNUP("SIGNUP"),
        EMAIL_VERIFY("EMAIL_VERIFY"),
        PASSWORD_RESET("PASSWORD_RESET"),
        PARENT_VERIFY("PARENT_VERIFY"),
        ADMIN_TEMP_PASSWORD("ADMIN_TEMP_PASSWORD");

        private final String code;

        VerifyPurpose(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    /**
     * 회원 타입
     */
    public enum MemberType {
        USER("USER"),
        ADMIN("ADMIN");

        private final String code;

        MemberType(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    // ========================================
    // 이메일 요청 DTO
    // ========================================

    /**
     * 이메일 발송 요청 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EmailRequest {
        private String serviceId;
        private String recipient;
        private String subject;
        private String content;
        private String contentType;
        private String emailType;
        private String consentType;
        private String senderName;
        private String cc;
        private String bcc;
        private String recipientName;
        private Long userNo;
        private Long createdBy;
    }

    /**
     * 템플릿 이메일 발송 요청 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TemplateEmailRequest {
        private String serviceId;
        private String templateCode;
        private String recipient;
        private Map<String, String> variables;
        private String senderName;
        private String cc;
        private String bcc;
        private String recipientName;
        private Long userNo;
        private Long createdBy;
    }

    /**
     * 인증 이메일 발송 요청 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VerifyEmailRequest {
        private String serviceId;
        private String recipient;
        private String verifyPurpose;
        private String templateCode;
        private Map<String, String> variables;
        private String senderName;
        private String recipientName;
        private String customCode;
        private Integer expireMinutes;
        private String memberType;
        private Long memberNo;
        private Long createdBy;
    }

    // ========================================
    // Builder 팩토리 메서드
    // ========================================

    public static EmailRequestBuilder builder() {
        return new EmailRequestBuilder();
    }

    public static TemplateEmailRequestBuilder templateBuilder() {
        return new TemplateEmailRequestBuilder();
    }

    public static VerifyEmailRequestBuilder verifyBuilder() {
        return new VerifyEmailRequestBuilder();
    }

    // ========================================
    // 이메일 요청 빌더
    // ========================================

    public static class EmailRequestBuilder {
        private String serviceId;
        private String recipient;
        private String subject;
        private String content;
        private String contentType = "HTML";
        private String emailType;
        private String consentType;
        private String senderName;
        private String cc;
        private String bcc;
        private String recipientName;
        private Long userNo;
        private Long createdBy;

        public EmailRequestBuilder serviceId(String serviceId) {
            this.serviceId = serviceId;
            return this;
        }

        public EmailRequestBuilder recipient(String recipient) {
            this.recipient = recipient;
            return this;
        }

        public EmailRequestBuilder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public EmailRequestBuilder content(String content) {
            this.content = content;
            return this;
        }

        public EmailRequestBuilder html() {
            this.contentType = "HTML";
            return this;
        }

        public EmailRequestBuilder text() {
            this.contentType = "TEXT";
            return this;
        }

        public EmailRequestBuilder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public EmailRequestBuilder emailType(String emailType) {
            this.emailType = emailType;
            return this;
        }

        public EmailRequestBuilder emailType(EmailType emailType) {
            this.emailType = emailType.getCode();
            return this;
        }

        public EmailRequestBuilder system() {
            this.emailType = EmailType.SYSTEM.getCode();
            return this;
        }

        public EmailRequestBuilder transaction() {
            this.emailType = EmailType.TRANSACTION.getCode();
            return this;
        }

        public EmailRequestBuilder verify() {
            this.emailType = EmailType.VERIFY.getCode();
            return this;
        }

        public EmailRequestBuilder marketing() {
            this.emailType = EmailType.MARKETING.getCode();
            this.consentType = ConsentType.MARKETING_EMAIL.getCode();
            return this;
        }

        public EmailRequestBuilder consentType(String consentType) {
            this.consentType = consentType;
            return this;
        }

        public EmailRequestBuilder consentType(ConsentType consentType) {
            this.consentType = consentType.getCode();
            return this;
        }

        public EmailRequestBuilder senderName(String senderName) {
            this.senderName = senderName;
            return this;
        }

        public EmailRequestBuilder cc(String cc) {
            this.cc = cc;
            return this;
        }

        public EmailRequestBuilder bcc(String bcc) {
            this.bcc = bcc;
            return this;
        }

        public EmailRequestBuilder recipientName(String recipientName) {
            this.recipientName = recipientName;
            return this;
        }

        public EmailRequestBuilder userNo(Long userNo) {
            this.userNo = userNo;
            return this;
        }

        public EmailRequestBuilder createdBy(Long createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public EmailRequest build() {
            return EmailRequest.builder()
                    .serviceId(serviceId)
                    .recipient(recipient)
                    .subject(subject)
                    .content(content)
                    .contentType(contentType)
                    .emailType(emailType)
                    .consentType(consentType)
                    .senderName(senderName)
                    .cc(cc)
                    .bcc(bcc)
                    .recipientName(recipientName)
                    .userNo(userNo)
                    .createdBy(createdBy)
                    .build();
        }
    }

    // ========================================
    // 템플릿 이메일 요청 빌더
    // ========================================

    public static class TemplateEmailRequestBuilder {
        private String serviceId;
        private String templateCode;
        private String recipient;
        private final Map<String, String> variables = new HashMap<>();
        private String senderName;
        private String cc;
        private String bcc;
        private String recipientName;
        private Long userNo;
        private Long createdBy;

        public TemplateEmailRequestBuilder serviceId(String serviceId) {
            this.serviceId = serviceId;
            return this;
        }

        public TemplateEmailRequestBuilder templateCode(String templateCode) {
            this.templateCode = templateCode;
            return this;
        }

        public TemplateEmailRequestBuilder recipient(String recipient) {
            this.recipient = recipient;
            return this;
        }

        public TemplateEmailRequestBuilder variable(String key, String value) {
            this.variables.put(key, value);
            return this;
        }

        public TemplateEmailRequestBuilder variable(String key, Number value) {
            this.variables.put(key, value != null ? value.toString() : "");
            return this;
        }

        public TemplateEmailRequestBuilder variables(Map<String, String> variables) {
            this.variables.putAll(variables);
            return this;
        }

        public TemplateEmailRequestBuilder senderName(String senderName) {
            this.senderName = senderName;
            return this;
        }

        public TemplateEmailRequestBuilder cc(String cc) {
            this.cc = cc;
            return this;
        }

        public TemplateEmailRequestBuilder bcc(String bcc) {
            this.bcc = bcc;
            return this;
        }

        public TemplateEmailRequestBuilder recipientName(String recipientName) {
            this.recipientName = recipientName;
            return this;
        }

        public TemplateEmailRequestBuilder userNo(Long userNo) {
            this.userNo = userNo;
            return this;
        }

        public TemplateEmailRequestBuilder createdBy(Long createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public TemplateEmailRequest build() {
            return TemplateEmailRequest.builder()
                    .serviceId(serviceId)
                    .templateCode(templateCode)
                    .recipient(recipient)
                    .variables(variables.isEmpty() ? null : variables)
                    .senderName(senderName)
                    .cc(cc)
                    .bcc(bcc)
                    .recipientName(recipientName)
                    .userNo(userNo)
                    .createdBy(createdBy)
                    .build();
        }
    }

    // ========================================
    // 인증 이메일 요청 빌더
    // ========================================

    public static class VerifyEmailRequestBuilder {
        private String serviceId;
        private String recipient;
        private String verifyPurpose;
        private String templateCode;
        private final Map<String, String> variables = new HashMap<>();
        private String senderName;
        private String recipientName;
        private String customCode;
        private Integer expireMinutes;
        private String memberType;
        private Long memberNo;
        private Long createdBy;

        public VerifyEmailRequestBuilder serviceId(String serviceId) {
            this.serviceId = serviceId;
            return this;
        }

        public VerifyEmailRequestBuilder recipient(String recipient) {
            this.recipient = recipient;
            return this;
        }

        public VerifyEmailRequestBuilder verifyPurpose(String verifyPurpose) {
            this.verifyPurpose = verifyPurpose;
            return this;
        }

        public VerifyEmailRequestBuilder verifyPurpose(VerifyPurpose verifyPurpose) {
            this.verifyPurpose = verifyPurpose.getCode();
            return this;
        }

        public VerifyEmailRequestBuilder templateCode(String templateCode) {
            this.templateCode = templateCode;
            return this;
        }

        public VerifyEmailRequestBuilder variable(String key, String value) {
            this.variables.put(key, value);
            return this;
        }

        public VerifyEmailRequestBuilder variable(String key, Number value) {
            this.variables.put(key, value != null ? value.toString() : "");
            return this;
        }

        public VerifyEmailRequestBuilder variables(Map<String, String> variables) {
            this.variables.putAll(variables);
            return this;
        }

        public VerifyEmailRequestBuilder senderName(String senderName) {
            this.senderName = senderName;
            return this;
        }

        public VerifyEmailRequestBuilder recipientName(String recipientName) {
            this.recipientName = recipientName;
            return this;
        }

        public VerifyEmailRequestBuilder customCode(String customCode) {
            this.customCode = customCode;
            return this;
        }

        public VerifyEmailRequestBuilder expireMinutes(Integer expireMinutes) {
            this.expireMinutes = expireMinutes;
            return this;
        }

        public VerifyEmailRequestBuilder memberType(String memberType) {
            this.memberType = memberType;
            return this;
        }

        public VerifyEmailRequestBuilder memberType(MemberType memberType) {
            this.memberType = memberType.getCode();
            return this;
        }

        public VerifyEmailRequestBuilder memberNo(Long memberNo) {
            this.memberNo = memberNo;
            return this;
        }

        public VerifyEmailRequestBuilder memberUser(Long userNo) {
            this.memberType = MemberType.USER.getCode();
            this.memberNo = userNo;
            return this;
        }

        public VerifyEmailRequestBuilder memberAdmin(Long adminNo) {
            this.memberType = MemberType.ADMIN.getCode();
            this.memberNo = adminNo;
            return this;
        }

        public VerifyEmailRequestBuilder createdBy(Long createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public VerifyEmailRequest build() {
            return VerifyEmailRequest.builder()
                    .serviceId(serviceId)
                    .recipient(recipient)
                    .verifyPurpose(verifyPurpose)
                    .templateCode(templateCode)
                    .variables(variables.isEmpty() ? null : variables)
                    .senderName(senderName)
                    .recipientName(recipientName)
                    .customCode(customCode)
                    .expireMinutes(expireMinutes)
                    .memberType(memberType)
                    .memberNo(memberNo)
                    .createdBy(createdBy)
                    .build();
        }
    }

    // ========================================
    // 편의 메서드
    // ========================================

    public static EmailRequest text(String serviceId, String recipient, String subject, String content) {
        return builder()
                .serviceId(serviceId)
                .recipient(recipient)
                .subject(subject)
                .content(content)
                .text()
                .system()
                .build();
    }

    public static EmailRequest html(String serviceId, String recipient, String subject, String content) {
        return builder()
                .serviceId(serviceId)
                .recipient(recipient)
                .subject(subject)
                .content(content)
                .html()
                .system()
                .build();
    }

    public static EmailRequest system(String serviceId, String recipient, String subject, String content) {
        return builder()
                .serviceId(serviceId)
                .recipient(recipient)
                .subject(subject)
                .content(content)
                .system()
                .senderName("시스템")
                .html()
                .build();
    }

    public static EmailRequest transaction(String serviceId, String recipient, String subject, String content) {
        return builder()
                .serviceId(serviceId)
                .recipient(recipient)
                .subject(subject)
                .content(content)
                .transaction()
                .html()
                .build();
    }

    public static EmailRequest marketing(String serviceId, String recipient, String subject, String content) {
        return builder()
                .serviceId(serviceId)
                .recipient(recipient)
                .subject(subject)
                .content(content)
                .marketing()
                .html()
                .build();
    }

    public static TemplateEmailRequest template(String serviceId, String templateCode,
                                                String recipient, Map<String, String> variables) {
        return templateBuilder()
                .serviceId(serviceId)
                .templateCode(templateCode)
                .recipient(recipient)
                .variables(variables)
                .build();
    }

    public static VerifyEmailRequest signup(String serviceId, String recipient,
                                            String templateCode, String userName) {
        return verifyBuilder()
                .serviceId(serviceId)
                .recipient(recipient)
                .verifyPurpose(VerifyPurpose.SIGNUP)
                .templateCode(templateCode)
                .variable("userName", userName)
                .build();
    }

    public static VerifyEmailRequest passwordReset(String serviceId, String recipient,
                                                   String templateCode) {
        return verifyBuilder()
                .serviceId(serviceId)
                .recipient(recipient)
                .verifyPurpose(VerifyPurpose.PASSWORD_RESET)
                .templateCode(templateCode)
                .build();
    }

    public static VerifyEmailRequest emailVerify(String serviceId, String recipient,
                                                 String templateCode) {
        return verifyBuilder()
                .serviceId(serviceId)
                .recipient(recipient)
                .verifyPurpose(VerifyPurpose.EMAIL_VERIFY)
                .templateCode(templateCode)
                .build();
    }

    public static VerifyEmailRequest emailVerifyWithCode(String serviceId, String recipient,
                                                         String templateCode, String customCode) {
        return verifyBuilder()
                .serviceId(serviceId)
                .recipient(recipient)
                .verifyPurpose(VerifyPurpose.EMAIL_VERIFY)
                .templateCode(templateCode)
                .customCode(customCode)
                .build();
    }

    public static VerifyEmailRequest emailVerifyForUser(String serviceId, String recipient,
                                                        String templateCode, Long userNo) {
        return verifyBuilder()
                .serviceId(serviceId)
                .recipient(recipient)
                .verifyPurpose(VerifyPurpose.EMAIL_VERIFY)
                .templateCode(templateCode)
                .memberUser(userNo)
                .build();
    }

    public static VerifyEmailRequest emailVerifyForAdmin(String serviceId, String recipient,
                                                         String templateCode, Long adminNo) {
        return verifyBuilder()
                .serviceId(serviceId)
                .recipient(recipient)
                .verifyPurpose(VerifyPurpose.EMAIL_VERIFY)
                .templateCode(templateCode)
                .memberAdmin(adminNo)
                .build();
    }
}