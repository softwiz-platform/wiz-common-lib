package org.softwiz.platform.iot.common.lib.exception;

import lombok.Getter;

/**
 * 401 Unauthorized 전용 예외
 *
 * 사용 예:
 * - JWT 토큰 만료
 * - JWT 토큰 위조/변조
 * - 인증 정보 없음
 */
@Getter
public class UnauthorizedException extends RuntimeException {
    private final String code;

    public UnauthorizedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public UnauthorizedException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}