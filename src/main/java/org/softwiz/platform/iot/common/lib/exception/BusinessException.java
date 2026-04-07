package org.softwiz.platform.iot.common.lib.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 비즈니스 예외
 *
 * HTTP 상태 코드를 포함하여 API 규격서에 맞는 응답 생성 가능
 * 기본값은 500 INTERNAL_SERVER_ERROR
 */
@Getter
public class BusinessException extends RuntimeException {
    private final String code;
    private final HttpStatus httpStatus;

    /**
     * 기본 생성자 (HTTP 500)
     */
    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
        this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /**
     * HTTP 상태 코드를 지정하는 생성자
     */
    public BusinessException(String code, String message, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    /**
     * 기본 생성자 with cause (HTTP 500)
     */
    public BusinessException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /**
     * HTTP 상태 코드를 지정하는 생성자 with cause
     */
    public BusinessException(String code, String message, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
    }
}