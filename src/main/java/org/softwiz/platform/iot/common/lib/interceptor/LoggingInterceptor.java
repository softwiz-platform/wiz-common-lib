package org.softwiz.platform.iot.common.lib.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.softwiz.platform.iot.common.lib.service.LoggingService;
import org.softwiz.platform.iot.common.lib.util.ClientIpExtractor;
import org.softwiz.platform.iot.common.lib.util.MaskingUtil;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoggingInterceptor implements HandlerInterceptor {

    private final LoggingService loggingService;
    private final MaskingUtil maskingUtil;
    private final ClientIpExtractor clientIpExtractor;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        long startTime = System.currentTimeMillis();

        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String fullUrl = queryString != null ? uri + "?" + queryString : uri;

        String clientIp = clientIpExtractor.extractClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        String contentType = request.getContentType();
        int contentLength = request.getContentLength();

        Map<String, String> headers = maskingUtil.maskHeaders(extractHeaders(request));
        Map<String, String[]> params = maskingUtil.maskParams(request.getParameterMap());

        loggingService.logRequest(
                method,
                fullUrl,
                maskingUtil.maskIpAddress(clientIp),
                userAgent,
                contentType,
                contentLength,
                headers,
                params
        );

        request.setAttribute("startTime", startTime);
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, ModelAndView modelAndView) {
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Long startTime = (Long) request.getAttribute("startTime");
        long duration = startTime != null ? System.currentTimeMillis() - startTime : 0;

        String method = request.getMethod();
        String uri = request.getRequestURI();
        int status = response.getStatus();
        String contentType = response.getContentType();

        loggingService.logResponse(method, uri, status, contentType, duration);

        if (ex != null) {
            loggingService.logError(uri, "INTERCEPTOR_ERROR", ex.getMessage(), ex);
        }
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();

        while (headerNames != null && headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            headers.put(headerName.toLowerCase(), headerValue);
        }

        return headers;
    }
}