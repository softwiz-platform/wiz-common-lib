package org.softwiz.platform.iot.common.lib.email;


import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 공통 이메일 발송 컴포넌트
 *
 * <p>CommonLibAutoConfiguration.EmailConfiguration에서 조건부로 Bean 등록됩니다.</p>
 * <p>email.enabled=true + spring-boot-starter-mail 의존성이 있을 때만 사용 가능합니다.</p>
 */
@Slf4j
public class EmailSender {

    private final EmailProperties emailProperties;
    private volatile JavaMailSender mailSender;
    private final Object lock = new Object();

    private static final Pattern TEMPLATE_VAR_PATTERN = Pattern.compile("#\\{(\\w+)\\}");

    public EmailSender(EmailProperties emailProperties) {
        this.emailProperties = emailProperties;
        log.debug("EmailSender 초기화 완료");
    }

    private JavaMailSender getMailSender() {
        if (mailSender == null) {
            synchronized (lock) {
                if (mailSender == null) {
                    mailSender = createMailSender();
                }
            }
        }
        return mailSender;
    }

    private JavaMailSender createMailSender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();

        EmailProperties.Smtp smtp = emailProperties.getSmtp();
        sender.setHost(smtp.getHost());
        sender.setPort(smtp.getPort());
        sender.setUsername(smtp.getUsername());
        sender.setPassword(smtp.getPassword());
        sender.setDefaultEncoding("UTF-8");

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", String.valueOf(smtp.isAuth()));
        props.put("mail.smtp.starttls.enable", String.valueOf(smtp.isStarttls()));
        props.put("mail.smtp.connectiontimeout", String.valueOf(smtp.getConnectionTimeout()));
        props.put("mail.smtp.timeout", String.valueOf(smtp.getTimeout()));
        props.put("mail.smtp.writetimeout", String.valueOf(smtp.getWriteTimeout()));
        props.put("mail.debug", "false");

        log.info("JavaMailSender 생성 - host: {}, port: {}", smtp.getHost(), smtp.getPort());
        return sender;
    }

    public boolean isEnabled() {
        return emailProperties.isEnabled()
                && StringUtils.hasText(emailProperties.getSmtp().getHost())
                && StringUtils.hasText(emailProperties.getSmtp().getUsername());
    }

    public EmailSendResult send(String to, String subject, String content, boolean isHtml) {
        return send(to, null, subject, content, isHtml, null, null);
    }

    public EmailSendResult send(String to, String toName, String subject, String content,
                                boolean isHtml, String cc, String bcc) {
        return sendWithCustomSender(
                to, toName,
                emailProperties.getDefaultSender(),
                emailProperties.getDefaultSenderName(),
                subject, content, isHtml, cc, bcc
        );
    }

    public EmailSendResult sendWithCustomSender(String to, String toName,
                                                String fromEmail, String fromName,
                                                String subject, String content,
                                                boolean isHtml, String cc, String bcc) {
        if (!emailProperties.isEnabled()) {
            log.debug("이메일 발송 기능이 비활성화되어 있습니다.");
            return EmailSendResult.disabled();
        }

        if (!StringUtils.hasText(emailProperties.getSmtp().getUsername())) {
            log.warn("SMTP username이 설정되지 않았습니다.");
            return EmailSendResult.failed("SMTP 설정이 완료되지 않았습니다");
        }

        try {
            MimeMessage message = getMailSender().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String senderEmail = StringUtils.hasText(fromEmail)
                    ? fromEmail : emailProperties.getDefaultSender();
            String senderName = StringUtils.hasText(fromName)
                    ? fromName : emailProperties.getDefaultSenderName();
            helper.setFrom(new InternetAddress(senderEmail, senderName, "UTF-8"));

            if (StringUtils.hasText(toName)) {
                helper.setTo(new InternetAddress(to, toName, "UTF-8"));
            } else {
                helper.setTo(to);
            }

            if (StringUtils.hasText(cc)) {
                helper.setCc(parseAddresses(cc));
            }
            if (StringUtils.hasText(bcc)) {
                helper.setBcc(parseAddresses(bcc));
            }

            helper.setSubject(subject);
            helper.setText(content, isHtml);

            getMailSender().send(message);

            log.info("✅ 이메일 발송 성공 - from: {}<{}>, to: {}, subject: {}",
                    senderName, senderEmail, to, subject);

            return EmailSendResult.success();

        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("❌ 이메일 발송 실패 (메시지 생성 오류) - to: {}, error: {}", to, e.getMessage());
            return EmailSendResult.failed("메시지 생성 오류: " + e.getMessage());
        } catch (MailException e) {
            log.error("❌ 이메일 발송 실패 (SMTP 오류) - to: {}, error: {}", to, e.getMessage());
            return EmailSendResult.failed("SMTP 오류: " + e.getMessage());
        } catch (Exception e) {
            log.error("❌ 이메일 발송 실패 - to: {}", to, e);
            return EmailSendResult.failed("알 수 없는 오류: " + e.getMessage());
        }
    }

    /**
     * 템플릿 변수 치환
     * #{변수명} 형식의 변수를 실제 값으로 치환합니다.
     *
     * <p>푸시 템플릿과 동일한 패턴을 사용합니다.</p>
     *
     * <h3>예시</h3>
     * <pre>
     * 템플릿: "#{userName}님, 인증코드는 #{verifyCode}입니다."
     * 변수: {"userName": "홍길동", "verifyCode": "123456"}
     * 결과: "홍길동님, 인증코드는 123456입니다."
     * </pre>
     *
     * @param template 템플릿 문자열
     * @param variables 변수 맵 (key: 변수명, value: 값)
     * @return 치환된 문자열
     */
    public String replaceVariables(String template, Map<String, String> variables) {
        if (template == null || variables == null || variables.isEmpty()) {
            return template;
        }

        Matcher matcher = TEMPLATE_VAR_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String varName = matcher.group(1);
            String replacement = variables.getOrDefault(varName, matcher.group(0));
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    public boolean testConnection() {
        if (!isEnabled()) {
            return false;
        }

        try {
            JavaMailSender sender = getMailSender();
            if (sender instanceof JavaMailSenderImpl impl) {
                impl.testConnection();
            }
            log.info("✅ SMTP 연결 테스트 성공");
            return true;
        } catch (Exception e) {
            log.error("❌ SMTP 연결 테스트 실패: {}", e.getMessage());
            return false;
        }
    }

    private String[] parseAddresses(String addresses) {
        if (!StringUtils.hasText(addresses)) {
            return new String[0];
        }
        return addresses.split("\\s*,\\s*");
    }

    public EmailProperties getProperties() {
        return emailProperties;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class EmailSendResult {
        private boolean success;
        private String errorMessage;
        private boolean disabled;

        public static EmailSendResult success() {
            return EmailSendResult.builder().success(true).build();
        }

        public static EmailSendResult failed(String errorMessage) {
            return EmailSendResult.builder().success(false).errorMessage(errorMessage).build();
        }

        public static EmailSendResult disabled() {
            return EmailSendResult.builder()
                    .success(false)
                    .disabled(true)
                    .errorMessage("이메일 발송 기능이 비활성화되어 있습니다")
                    .build();
        }
    }
}
