package org.softwiz.platform.iot.common.lib.service;

import lombok.extern.slf4j.Slf4j;
import org.softwiz.platform.iot.common.lib.context.GatewayContext;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class LoggingService {

    public void logRequest(String method, String url, String clientIp, String userAgent,
                           String contentType, int contentLength,
                           Map<String, String> headers, Map<String, String[]> params) {

        GatewayContext ctx = GatewayContext.getContext();
        String serviceId = (ctx != null && ctx.getServiceId() != null) ? ctx.getServiceId() : "SERVICE";
        String nickName  = (ctx != null && ctx.getNickName()  != null) ? ctx.getNickName()  : "Guest";

        log.info("Request: {} {} | IP: {} | UA: {} | Service: {} | User: {}",
                method, url, clientIp, userAgent, serviceId, nickName);
        if (log.isDebugEnabled()) {
            log.debug("Headers: {}", headers);
            log.debug("Params: {}", params);
        }
    }

    public void logResponse(String method, String url, int status, String contentType, long duration) {

        GatewayContext ctx = GatewayContext.getContext();
        String serviceId = (ctx != null && ctx.getServiceId() != null) ? ctx.getServiceId() : "SERVICE";
        String nickName  = (ctx != null && ctx.getNickName()  != null) ? ctx.getNickName()  : "Guest";

        log.info("Response: {} {} | Status: {} | {}ms | Service: {} | User: {}",
                method, url, status, duration, serviceId, nickName);
    }

    public void logError(String url, String errorCode, String message, Exception ex) {
        log.error("Error at {}: {} - {}", url, errorCode, message, ex);
    }
}