package org.softwiz.platform.iot.common.lib.config;

import lombok.extern.slf4j.Slf4j;
import org.softwiz.platform.iot.common.lib.email.EmailSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;

/**
 * WIZ Common Library Auto Configuration
 *
 * 이 설정 클래스는 Spring Boot의 Auto Configuration 메커니즘을 통해
 * 자동으로 로드되어 라이브러리의 모든 컴포넌트를 스캔합니다.
 *
 * 포함되는 컴포넌트:
 * - Filters: MdcFilter
 * - Interceptors: GatewayHeaderInterceptor, LoggingInterceptor
 * - Utils: CryptoUtil, JwtUtil, MaskingUtil, ClientIpExtractor, DeviceDetector, ValidationUtil
 * - Validators: GatewaySignatureValidator
 * - Services: LoggingService
 * - Exception Handlers: GlobalExceptionHandler
 * - Config: PublicPathConfig
 * - Email: EmailSender (email.enabled=true 일 때만)
 *
 * 사용법:
 * 1. 마이크로서비스의 pom.xml에 의존성 추가:
 *    <dependency>
 *        <groupId>com.github.사용자이름</groupId>
 *        <artifactId>wiz-common-lib</artifactId>
 *        <version>1.0.0</version>
 *    </dependency>
 *
 * 2. application.yml에 필수 설정 추가 (아래 JavaDoc 참조)
 *
 * 3. 자동으로 모든 컴포넌트가 Bean으로 등록됨
 *
 * 필수 설정:
 * <pre>
 * # CryptoUtil (AES-256 암호화)
 * crypto:
 *   secret-key: "your-32-byte-secret-key-here!"  # 정확히 32 bytes
 *   iv: "your-16-byte-iv!!"                       # 정확히 16 bytes
 *
 * # JwtUtil (JWT 토큰) - jwt.enabled=true 일 때만
 * jwt:
 *   enabled: true
 *   secret: "your-jwt-secret-key-at-least-256-bits-long-for-hs256-algorithm"
 *   expiration: 3600000              # Access Token: 1시간 (밀리초)
 *   refresh-expiration: 86400000     # Refresh Token: 24시간 (밀리초)
 *   issuer: "WIZ-PLATFORM"
 *
 * # GatewaySignatureValidator (Gateway 서명 검증)
 * gateway:
 *   signature:
 *     secret: "your-gateway-signature-secret-key"
 *     enabled: true                  # 운영: true, 개발: false (선택)
 *     mock-enabled: false            # 로컬 테스트: true (선택)
 *
 * # PublicPathConfig (인증 제외 경로)
 * security:
 *   publicPaths:
 *     - /actuator/**
 *     - /swagger-ui/**
 *     - /v3/api-docs/**
 *     - /health
 *     - /favicon.ico
 * </pre>
 *
 * 선택적 설정 (이메일):
 * <pre>
 * # EmailSender (email.enabled=true + spring-boot-starter-mail 의존성 필요)
 * email:
 *   enabled: true
 *   smtp:
 *     host: smtp.gmail.com
 *     port: 587
 *     username: ${SMTP_USERNAME}
 *     password: ${SMTP_PASSWORD}
 *     auth: true
 *     starttls: true
 *   default-sender: info@softwiz.co.kr
 *   default-sender-name: WIZ Platform
 * </pre>
 *
 * 주의사항:
 * - crypto.secret-key는 반드시 32바이트여야 합니다 (AES-256)
 * - crypto.iv는 반드시 16바이트여야 합니다 (AES 블록 크기)
 * - jwt.secret는 HS256 알고리즘을 위해 최소 256비트 이상 권장
 * - 운영 환경에서는 모든 secret 값을 환경변수 또는 암호화된 설정으로 관리하세요
 * - 모든 서비스의 기본 타임존은 Asia/Seoul로 자동 설정됩니다
 * - EmailSender는 email.enabled=true이고 spring-boot-starter-mail 의존성이 있을 때만 활성화됩니다
 */
@Slf4j
@Configuration
@ComponentScan(basePackages = {
        "org.softwiz.platform.iot.common.lib.config",
        "org.softwiz.platform.iot.common.lib.context",
        "org.softwiz.platform.iot.common.lib.filter",
        "org.softwiz.platform.iot.common.lib.interceptor",
        "org.softwiz.platform.iot.common.lib.service",
        "org.softwiz.platform.iot.common.lib.util",
        "org.softwiz.platform.iot.common.lib.validator",
        "org.softwiz.platform.iot.common.lib.exception",
        "org.softwiz.platform.iot.common.lib.advice",
        "org.softwiz.platform.iot.common.lib.mybatis"
        // email 패키지는 ComponentScan에서 제외 (아래 inner class에서 조건부 등록)
})
@EnableConfigurationProperties
public class CommonLibAutoConfiguration {

    @PostConstruct
    public void init() {
        // ★★★ 타임존 설정 (가장 먼저 실행) ★★★
        initializeTimeZone();

        log.info("========================================");
        log.info("WIZ Common Library Initialized");
        log.info("========================================");
        log.info("✓ Core Components:");
        log.info("  - GatewayContext (ThreadLocal)");
        log.info("  - MdcFilter (Request ID, Client IP)");
        log.info("  - GatewayHeaderInterceptor");
        log.info("  - GlobalExceptionHandler");
        log.info("✓ Security Components:");
        log.info("  - CryptoUtil (AES-256)");
        log.info("  - JwtUtil (JWT) - jwt.enabled=true 시");
        log.info("  - GatewaySignatureValidator");
        log.info("✓ Utility Components:");
        log.info("  - MaskingUtil (Log Masking)");
        log.info("  - ClientIpExtractor");
        log.info("  - DeviceDetector");
        log.info("  - ValidationUtil");
        log.info("✓ Optional Components:");
        log.info("  - EmailSender - email.enabled=true 시");
        log.info("========================================");
        log.info("⚠️  Required Configuration:");
        log.info("   crypto.secret-key, crypto.iv");
        log.info("   jwt.secret (jwt.enabled=true 시)");
        log.info("   gateway.signature.secret");
        log.info("   security.publicPaths");
        log.info("========================================");
    }

    /**
     * 타임존 초기화
     */
    private void initializeTimeZone() {
        TimeZone seoulTimeZone = TimeZone.getTimeZone("Asia/Seoul");
        TimeZone.setDefault(seoulTimeZone);

        log.info("========================================");
        log.info("✅ TimeZone Configuration");
        log.info("   Default TimeZone: {}", TimeZone.getDefault().getID());
        log.info("   Display Name: {}", TimeZone.getDefault().getDisplayName());
        log.info("   Offset: UTC{}", getOffsetString(seoulTimeZone));
        log.info("========================================");
    }

    private String getOffsetString(TimeZone timeZone) {
        int offsetMillis = timeZone.getRawOffset();
        int hours = offsetMillis / (1000 * 60 * 60);
        int minutes = Math.abs((offsetMillis / (1000 * 60)) % 60);
        return String.format("%+03d:%02d", hours, minutes);
    }

    @PostConstruct
    public void validateConfiguration() {
        // 실제 검증은 각 컴포넌트의 생성자에서 수행
    }

    // ========================================
// Email Configuration (조건부 로드)
// ========================================

    /**
     * 이메일 설정 (조건부)
     *
     * <p>다음 조건을 모두 만족할 때만 EmailSender Bean이 생성됩니다:</p>
     * <ul>
     *   <li>JavaMailSender 클래스가 classpath에 존재 (spring-boot-starter-mail 의존성)</li>
     *   <li>email.enabled=true (application.yml)</li>
     *   <li>EmailSender Bean이 다른 곳에서 정의되지 않음</li>
     * </ul>
     *
     * <p>서비스에서 직접 EmailSender Bean을 정의하면 이 자동 설정은 무시됩니다.</p>
     */
    @Configuration
    @ConditionalOnClass(name = "org.springframework.mail.javamail.JavaMailSender")
    @ConditionalOnProperty(
            prefix = "email",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = false
    )
    @EnableConfigurationProperties(org.softwiz.platform.iot.common.lib.email.EmailProperties.class)
    static class EmailConfiguration {

        @Bean
        @ConditionalOnMissingBean(org.softwiz.platform.iot.common.lib.email.EmailSender.class)
        public org.softwiz.platform.iot.common.lib.email.EmailSender emailSender(
                org.softwiz.platform.iot.common.lib.email.EmailProperties emailProperties) {
            log.info("========================================");
            log.info("✅ EmailSender Bean 생성 (common-lib auto)");
            log.info("   email.enabled: true");
            log.info("   smtp.host: {}", emailProperties.getSmtp().getHost());
            log.info("   smtp.port: {}", emailProperties.getSmtp().getPort());
            log.info("   default-sender: {}", emailProperties.getDefaultSender());
            log.info("========================================");
            return new org.softwiz.platform.iot.common.lib.email.EmailSender(emailProperties);
        }
    }
}