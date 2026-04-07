package org.softwiz.platform.iot.common.lib.exception;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.MyBatisSystemException;
import org.slf4j.MDC;
import org.softwiz.platform.iot.common.lib.dto.ErrorResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.util.stream.Collectors;

/**
 * 베이스 예외 처리기
 *
 * 각 서비스에서 상속받아 사용
 * 공통 로직 제공 + 확장 가능
 *
 * DB 관련 예외 핸들러 포함
 */
@Slf4j
public abstract class BaseExceptionHandler {

    /**
     * DB 연결 실패 처리 (503 Service Unavailable)
     * - CannotGetJdbcConnectionException
     * - Connection Pool exhausted
     * - DB 서버 다운 등
     */
    @ExceptionHandler(CannotGetJdbcConnectionException.class)
    public ResponseEntity<ErrorResponse> handleDatabaseConnectionException(
            CannotGetJdbcConnectionException ex,
            WebRequest request) {

        String path = extractPath(request);
        log.error("Database connection failed: {}", path, ex);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.builder()
                        .requestId(getRequestId())
                        .code("DATABASE_CONNECTION_ERROR")
                        .message("데이터베이스 연결 실패")
                        .path(path)
                        .build());
    }

    /**
     * SQL 문법 오류 처리 (500 Internal Server Error)
     * - SELECT, INSERT, UPDATE, DELETE 문법 오류
     * - 테이블/컬럼명 오타
     * - SQL 키워드 오류
     */
    @ExceptionHandler(BadSqlGrammarException.class)
    public ResponseEntity<ErrorResponse> handleBadSqlGrammarException(
            BadSqlGrammarException ex,
            WebRequest request) {

        String path = extractPath(request);
        String sqlError = ex.getSQLException() != null
                ? ex.getSQLException().getMessage()
                : ex.getMessage();

        // 상세 로그: 어떤 SQL이 문제인지 출력
        log.error("SQL syntax error at [{}]: {} | SQL: {}", path, sqlError, ex.getSql(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.builder()
                        .requestId(getRequestId())
                        .code("SQL_SYNTAX_ERROR")
                        .message("SQL 쿼리 문법 오류")
                        .path(path)
                        .build());
    }

    /**
     * MyBatis 시스템 예외 처리
     * - MyBatisSystemException은 DB 연결 문제 등을 여러 겹으로 감싸고 있음
     * - 예외 체인: MyBatisSystemException → PersistenceException → CannotGetJdbcConnectionException
     * - getCause()가 아닌 전체 예외 체인을 탐색하여 정확한 원인 파악
     */
    @ExceptionHandler(MyBatisSystemException.class)
    public ResponseEntity<ErrorResponse> handleMyBatisSystemException(
            MyBatisSystemException ex,
            WebRequest request) {

        String path = extractPath(request);
        Throwable rootCause = ex.getRootCause();
        String detailMessage = (rootCause != null) ? rootCause.getMessage() : ex.getMessage();

        // 1. DB 연결 문제 체크 (예외 체인 전체 탐색)
        if (containsCause(ex, CannotGetJdbcConnectionException.class)) {
            log.error("MyBatis DB connection failed: {} | Detail: {}", path, detailMessage, ex);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ErrorResponse.builder()
                            .requestId(getRequestId())
                            .code("DATABASE_CONNECTION_ERROR")
                            .message("데이터베이스 연결 실패")
                            .path(path)
                            .build());
        }

        // 2. SQL 문법 오류 (예외 체인 전체 탐색)
        if (containsCause(ex, BadSqlGrammarException.class) ||
                (detailMessage != null &&
                        (detailMessage.contains("syntax error") ||
                                detailMessage.contains("SQLSyntaxErrorException") ||
                                detailMessage.contains("You have an error in your SQL syntax")))) {

            log.error("SQL syntax error wrapped in MyBatis at [{}]: {} | Detail: {}",
                    path, ex.getMessage(), detailMessage, ex);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.builder()
                            .requestId(getRequestId())
                            .code("SQL_SYNTAX_ERROR")
                            .message("SQL 쿼리 문법 오류")
                            .path(path)
                            .build());
        }

        // 3. 테이블/컬럼 존재하지 않음
        if (detailMessage != null &&
                (detailMessage.contains("doesn't exist") ||
                        detailMessage.contains("Unknown column") ||
                        (detailMessage.contains("Table") && detailMessage.contains("not found")))) {

            log.error("SQL object not found at [{}]: {} | Detail: {}",
                    path, ex.getMessage(), detailMessage, ex);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.builder()
                            .requestId(getRequestId())
                            .code("SQL_OBJECT_NOT_FOUND")
                            .message("테이블 또는 컬럼을 찾을 수 없음")
                            .path(path)
                            .build());
        }

        // 4. 타입 변환 오류
        if (detailMessage != null &&
                (detailMessage.contains("Cannot convert") ||
                        detailMessage.contains("Type mismatch") ||
                        detailMessage.contains("cannot be cast"))) {

            log.error("MyBatis type mapping error at [{}]: {} | Root cause: {}",
                    path, ex.getMessage(), detailMessage, ex);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.builder()
                            .requestId(getRequestId())
                            .code("TYPE_MAPPING_ERROR")
                            .message("데이터 타입 변환 오류")
                            .path(path)
                            .build());
        }

        // 5. 기타 MyBatis 오류
        log.error("MyBatis system error at [{}]: {} | Cause: {}",
                path, ex.getMessage(), detailMessage, ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.builder()
                        .requestId(getRequestId())
                        .code("MYBATIS_SYSTEM_ERROR")
                        .message("데이터베이스 처리 중 오류")
                        .path(path)
                        .build());
    }

    /**
     * 일반 DB 접근 예외 처리 (500 Internal Server Error)
     * - DataIntegrityViolation (외래키, 유니크 제약 위반 등)
     * - DataAccessResourceFailure
     * - 기타 Spring DataAccessException
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccessException(
            DataAccessException ex,
            WebRequest request) {

        String path = extractPath(request);

        // 구체적인 오류 유형별 처리
        String errorCode = "DATABASE_ERROR";
        String errorMessage = "데이터베이스 작업 중 오류";

        if (ex instanceof DuplicateKeyException) {
            errorCode = "DUPLICATE_KEY_ERROR";
            errorMessage = "중복된 데이터가 존재합니다";
            log.warn("Duplicate key error at [{}]", path, ex);
        } else if (ex instanceof DataIntegrityViolationException) {
            String rootMsg = ex.getMostSpecificCause().getMessage();

            // Data too long: 컬럼 용량 초과 (이미지 base64 등)
            if (rootMsg != null && rootMsg.contains("Data too long for column")) {
                errorCode = "DATA_TOO_LONG";
                errorMessage = "데이터 길이 제한을 초과했습니다.";
                log.warn("Data too long at [{}]: {}", path, rootMsg);
                // NOT NULL 제약 위반
            } else if (rootMsg != null && rootMsg.contains("cannot be null")) {
                errorCode = "NOT_NULL_VIOLATION";
                errorMessage = "필수 항목 누락";
                log.warn("Not null violation at [{}]: {}", path, rootMsg);
                // FK 제약 위반
            } else if (rootMsg != null && rootMsg.contains("foreign key constraint")) {
                errorCode = "FOREIGN_KEY_VIOLATION";
                errorMessage = "참조 데이터가 존재하지 않습니다";
                log.warn("Foreign key violation at [{}]: {}", path, rootMsg);
                // 기타 무결성 위반
            } else {
                errorCode = "DATA_INTEGRITY_VIOLATION";
                errorMessage = "데이터 무결성 제약 위반";
                log.error("Data integrity violation at [{}]: {}", path, rootMsg, ex);
            }
        } else {
            log.error("Data access error at [{}]", path, ex);
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.builder()
                        .requestId(getRequestId())
                        .code(errorCode)
                        .message(errorMessage)
                        .path(path)
                        .build());
    }

    /**
     * Validation 예외 처리 (400 Bad Request)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            WebRequest request) {

        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        String path = extractPath(request);
        log.warn("Validation failed at [{}]: {}", path, errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .requestId(getRequestId())
                        .code("BAD_REQUEST")
                        .message("잘못된 요청")
                        .path(path)
                        .build());
    }

    /**
     * 401 Unauthorized 처리
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedException(
            UnauthorizedException ex,
            WebRequest request) {

        String path = extractPath(request);
        log.warn("Unauthorized access: {}", path);

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.builder()
                        .requestId(getRequestId())
                        .code("UNAUTHORIZED")
                        .message(ex.getMessage())
                        .path(path)
                        .build());
    }

    /**
     * 비즈니스 예외 처리
     * BusinessException에서 제공하는 HttpStatus 사용
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex,
            WebRequest request) {

        HttpStatus status = ex.getHttpStatus();
        String path = extractPath(request);

        if (status.is4xxClientError()) {
            log.warn("Business error: {} - Code: {}", path, ex.getCode());
        } else {
            log.error("Business error: {} - Code: {}", path, ex.getCode());
        }

        return ResponseEntity.status(status)
                .body(ErrorResponse.builder()
                        .requestId(getRequestId())
                        .code(ex.getCode())
                        .message(ex.getMessage())
                        .path(path)
                        .build());
    }

    /**
     * 파일 크기 초과 예외 처리 (400 Bad Request)
     *
     * MaxUploadSizeExceededException은 Tomcat/Spring이 서비스 레이어 진입 전
     * multipart 파싱 단계에서 던지므로 서비스 내부 검증 코드까지 도달하지 못함.
     * spring.servlet.multipart.max-file-size / max-request-size 설정값 초과 시 발생.
     */
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceededException(
            org.springframework.web.multipart.MaxUploadSizeExceededException ex,
            WebRequest request) {

        String path = extractPath(request);
        log.warn("File size exceeded at [{}]: maxSize={}, cause={}",
                path, ex.getMaxUploadSize(), ex.getMostSpecificCause().getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .requestId(getRequestId())
                        .code("FILE_SIZE_EXCEEDED")
                        .message("파일 크기가 초과 되었습니다.")
                        .path(path)
                        .build());
    }

    /**
     * 일반 예외 처리
     * 다른 핸들러가 먼저 처리하도록 마지막 순서로 배치
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception ex,
            WebRequest request) {

        String path = extractPath(request);
        log.error("Unexpected error at [{}]", path, ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.builder()
                        .requestId(getRequestId())
                        .code("INTERNAL_ERROR")
                        .message("서버 내부 오류")
                        .path(path)
                        .build());
    }

    @ExceptionHandler(HttpMessageNotWritableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotWritableException(
            HttpMessageNotWritableException ex,
            WebRequest request) {

        String path = extractPath(request);
        String contentType = ex.getMessage();

        log.warn("Response conversion failed at [{}]: {} - This is usually harmless for SSE/WebSocket",
                path, contentType);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.builder()
                        .requestId(getRequestId())
                        .code("RESPONSE_CONVERSION_ERROR")
                        .message("응답 변환 실패")
                        .path(path)
                        .build());
    }

    // ========================================
    // 유틸리티 메서드
    // ========================================

    /**
     * 예외 체인에서 특정 타입의 원인이 존재하는지 확인
     *
     * MyBatis 예외는 여러 겹으로 감싸져 있어 getCause()만으로는 부족함
     * 예: MyBatisSystemException → PersistenceException → CannotGetJdbcConnectionException
     *
     * @param ex 검사할 예외
     * @param causeType 찾으려는 원인 예외 타입
     * @return 해당 타입이 예외 체인에 존재하면 true
     */
    protected boolean containsCause(Throwable ex, Class<? extends Throwable> causeType) {
        Throwable current = ex;
        int depth = 0;
        while (current != null && depth < 20) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
            depth++;
        }
        return false;
    }

    /**
     * 유틸리티: Request ID 추출
     */
    protected String getRequestId() {
        String requestId = MDC.get("requestId");
        return requestId != null ? requestId : "NO_ID";
    }

    /**
     * 유틸리티: Request Path 추출
     */
    protected String extractPath(WebRequest request) {
        String description = request.getDescription(false);
        if (description.startsWith("uri=")) {
            return description.substring(4);
        }
        return description;
    }
}