package org.softwiz.platform.iot.common.lib.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 이메일 자동 설정
 * 
 * <p>email.enabled=true 일 때만 EmailSender Bean이 생성됩니다.</p>
 * <p>설정이 없거나 false면 Bean이 생성되지 않아 다른 서비스에 영향 없습니다.</p>
 * 
 * <h3>활성화 조건</h3>
 * <ul>
 *   <li>email.enabled=true (application.yml)</li>
 *   <li>JavaMailSender 클래스가 classpath에 존재 (spring-boot-starter-mail 의존성)</li>
 * </ul>
 * 
 * <h3>사용법 (이메일 사용하는 서비스만)</h3>
 * <pre>
 * # application.yml
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
 * <h3>이메일 미사용 서비스</h3>
 * <p>email 관련 설정을 하지 않으면 Bean이 생성되지 않아 오류 없이 동작합니다.</p>
 */
@Slf4j
@Configuration
@ConditionalOnClass(JavaMailSender.class)  // mail 의존성 있을 때만
@ConditionalOnProperty(
        prefix = "email",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false  // 설정 없으면 Bean 생성 안함
)
@EnableConfigurationProperties(EmailProperties.class)
public class EmailAutoConfiguration {

    @Bean
    public EmailSender emailSender(EmailProperties emailProperties) {
        log.info("========================================");
        log.info("✅ EmailSender Bean 생성");
        log.info("   email.enabled: true");
        log.info("   smtp.host: {}", emailProperties.getSmtp().getHost());
        log.info("   smtp.port: {}", emailProperties.getSmtp().getPort());
        log.info("   default-sender: {}", emailProperties.getDefaultSender());
        log.info("========================================");
        return new EmailSender(emailProperties);
    }
}