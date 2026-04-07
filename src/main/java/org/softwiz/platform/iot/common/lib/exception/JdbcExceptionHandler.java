package org.softwiz.platform.iot.common.lib.exception;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.softwiz.platform.iot.common.lib.dto.ErrorResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * JDBC/Database 예외 처리기
 *
 * spring-jdbc가 클래스패스에 있을 때만 활성화됩니다.
 * DB를 사용하지 않는 서비스에서는 이 핸들러가 로드되지 않습니다.
 */
@Slf4j
@RestControllerAdvice
@ConditionalOnClass(name = "org.springframework.jdbc.CannotGetJdbcConnectionException")
public class JdbcExceptionHandler {

    /**
     * 데이터베이스 연결 실패 예외
     * MySQL 서버 연결 불가, 네트워크 오류 등
     */
    @ExceptionHandler({
            CannotGetJdbcConnectionException.class,
            TransientDataAccessResourceException.class
    })
    public ResponseEntity<ErrorResponse> handleDatabaseConnectionException(
            Exception ex,
            WebRequest request) {

        String path = extractPath(request);
        log.error("Database connection failed: {} - {}", path, ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.builder()
                        .requestId(getRequestId())
                        .code("SERVICE_UNAVAILABLE")
                        .message("데이터베이스 연결에 실패했습니다. 잠시 후 다시 시도해주세요.")
                        .path(path)
                        .build());
    }

    /**
     * 데이터베이스 작업 실패 예외
     * SQL 실행 오류, 트랜잭션 오류 등
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccessException(
            DataAccessException ex,
            WebRequest request) {

        String path = extractPath(request);
        log.error("Database operation failed: {} - {}", path, ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.builder()
                        .requestId(getRequestId())
                        .code("DATABASE_ERROR")
                        .message("데이터베이스 작업 중 오류가 발생했습니다.")
                        .path(path)
                        .build());
    }

    private String getRequestId() {
        String requestId = MDC.get("requestId");
        return requestId != null ? requestId : "NO_ID";
    }

    private String extractPath(WebRequest request) {
        String description = request.getDescription(false);
        if (description.startsWith("uri=")) {
            return description.substring(4);
        }
        return description;
    }
}