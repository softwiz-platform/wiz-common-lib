package org.softwiz.platform.iot.common.lib.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@ConfigurationProperties(prefix = "security")
@Getter
@Setter
public class PublicPathConfig {

    private List<String> publicPaths = new ArrayList<>();

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public boolean isPublic(String path) {
        if (path == null) return false;

        boolean match = publicPaths.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));

        if (match) {
            log.trace("Public path matched: {}", path);
        }

        return match;
    }
}