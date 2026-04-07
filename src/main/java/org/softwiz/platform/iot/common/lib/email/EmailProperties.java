package org.softwiz.platform.iot.common.lib.email;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 이메일 설정 프로퍼티
 * 
 * <p>CommonLibAutoConfiguration.EmailConfiguration에서 조건부로 등록됩니다.</p>
 * <p>email.enabled=true 일 때만 이 설정이 사용됩니다.</p>
 */
@Data
@ConfigurationProperties(prefix = "email")
public class EmailProperties {

    /**
     * 이메일 발송 기능 활성화 여부
     */
    private boolean enabled = false;

    /**
     * SMTP 설정
     */
    private Smtp smtp = new Smtp();

    /**
     * 기본 발신자 이메일
     */
    private String defaultSender = "noreply@example.com";

    /**
     * 기본 발신자 이름
     */
    private String defaultSenderName = "System";

    /**
     * 재시도 설정
     */
    private Retry retry = new Retry();

    @Data
    public static class Smtp {
        private String host = "localhost";
        private int port = 25;
        private String username;
        private String password;
        private boolean auth = false;
        private boolean starttls = false;
        private int connectionTimeout = 10000;
        private int timeout = 10000;
        private int writeTimeout = 10000;
    }

    @Data
    public static class Retry {
        private int maxAttempts = 3;
        private long delay = 1000;
    }
}