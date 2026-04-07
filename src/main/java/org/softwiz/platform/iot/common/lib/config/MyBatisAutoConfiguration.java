package org.softwiz.platform.iot.common.lib.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.Configuration;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.softwiz.platform.iot.common.lib.mybatis.DateFormatTypeHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * MyBatis 공통 설정
 * - TypeHandler 자동 등록
 *
 * MyBatis가 클래스패스에 있을 때만 활성화됩니다.
 * MyBatis를 사용하지 않는 서비스에서는 이 설정이 로드되지 않습니다.
 *
 * ⚠️ TimeZone 설정 방법:
 * 각 마이크로서비스의 application.yml에 다음을 추가하세요:
 *
 * mybatis:
 *   configuration:
 *     default-time-zone: Asia/Seoul
 *
 * 이 방식이 멀티 DataSource 환경에서 가장 안전하고 명확합니다.
 */
@Slf4j
@org.springframework.context.annotation.Configuration
@ConditionalOnClass({ConfigurationCustomizer.class, Configuration.class})
public class MyBatisAutoConfiguration {

    @Bean
    @ConditionalOnClass(name = "org.apache.ibatis.session.SqlSessionFactory")
    public ConfigurationCustomizer mybatisConfigurationCustomizer() {
        return configuration -> {
            // 공통 TypeHandler 자동 등록
            configuration.getTypeHandlerRegistry()
                    .register(DateFormatTypeHandler.class);

            log.debug("✅ MyBatis TypeHandler registered: DateFormatTypeHandler");
            log.debug("⚠️ TimeZone 설정은 application.yml에서 필요:");
            log.debug("   mybatis.configuration.default-time-zone: Asia/Seoul");
        };
    }
}