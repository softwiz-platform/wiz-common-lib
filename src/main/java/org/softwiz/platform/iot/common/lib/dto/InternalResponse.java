package org.softwiz.platform.iot.common.lib.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 서버 간 통신용 Response 래퍼 클래스
 * 
 * <p>ApiResponse와 달리 고정된 'data' 필드가 아닌,
 * 어떤 필드명으로 반환되어도 받을 수 있는 유연한 구조입니다.</p>
 * 
 * <p><b>마이크로서비스 간 통신 시 직렬화 이슈를 방지하기 위해
 * timestamp는 String 타입으로 관리합니다.</b></p>
 * 
 * <pre>
 * 사용 예시 (응답 생성):
 * {@code
 * // 단순 성공 응답
 * return InternalResponse.success();
 * 
 * // 데이터 포함 응답
 * return InternalResponse.success("result", myData);
 * 
 * // 여러 필드 응답
 * return InternalResponse.builder()
 *     .code("SUCCESS")
 *     .message("처리 완료")
 *     .field("userId", 123L)
 *     .field("userName", "홍길동")
 *     .build();
 * }
 * </pre>
 * 
 * <pre>
 * 사용 예시 (응답 수신):
 * {@code
 * InternalResponse response = restTemplate.postForObject(url, request, InternalResponse.class);
 * 
 * // 특정 필드 조회
 * Long userId = response.getField("userId", Long.class);
 * String userName = response.getField("userName", String.class);
 * 
 * // 성공 여부 확인
 * if (response.isSuccess()) {
 *     // 처리
 * }
 * }
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InternalResponse {

    /**
     * Timestamp 포맷 (마이크로서비스 간 통일)
     */
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 응답 코드 (SUCCESS, ERROR 등)
     */
    private String code;

    /**
     * 응답 메시지
     */
    private String message;

    /**
     * 요청 ID (추적용)
     */
    private String requestId;

    /**
     * 응답 시간 (String으로 관리 - 마이크로서비스 간 직렬화 이슈 방지)
     * 형식: yyyy-MM-dd HH:mm:ss
     */
    private String timestamp;

    /**
     * 동적 필드 저장소
     * 어떤 필드명이든 받을 수 있음
     */
    @JsonIgnore
    private Map<String, Object> additionalFields = new HashMap<>();

    // ========================================
    // JSON 동적 필드 처리
    // ========================================

    /**
     * 알려지지 않은 JSON 필드를 동적으로 저장
     */
    @JsonAnySetter
    public void setAdditionalField(String key, Object value) {
        if (additionalFields == null) {
            additionalFields = new HashMap<>();
        }
        additionalFields.put(key, value);
    }

    /**
     * 동적 필드를 JSON으로 직렬화
     */
    @JsonAnyGetter
    public Map<String, Object> getAdditionalFields() {
        return additionalFields;
    }

    // ========================================
    // 필드 접근 메서드
    // ========================================

    /**
     * 특정 필드 값 조회
     * 
     * @param key 필드명
     * @return 필드 값 (없으면 null)
     */
    public Object getField(String key) {
        return additionalFields != null ? additionalFields.get(key) : null;
    }

    /**
     * 특정 필드 값을 지정된 타입으로 조회
     * 
     * @param key 필드명
     * @param clazz 반환 타입
     * @return 필드 값 (타입 변환, 없으면 null)
     */
    @SuppressWarnings("unchecked")
    public <T> T getField(String key, Class<T> clazz) {
        Object value = getField(key);
        if (value == null) {
            return null;
        }
        
        // Number 타입 변환
        if (value instanceof Number && Number.class.isAssignableFrom(clazz)) {
            Number num = (Number) value;
            if (clazz == Long.class) {
                return (T) Long.valueOf(num.longValue());
            } else if (clazz == Integer.class) {
                return (T) Integer.valueOf(num.intValue());
            } else if (clazz == Double.class) {
                return (T) Double.valueOf(num.doubleValue());
            }
        }
        
        return clazz.cast(value);
    }

    /**
     * 필드 존재 여부 확인
     */
    public boolean hasField(String key) {
        return additionalFields != null && additionalFields.containsKey(key);
    }

    // ========================================
    // Timestamp 유틸리티
    // ========================================

    /**
     * 현재 시간을 timestamp 형식 문자열로 반환
     */
    private static String nowTimestamp() {
        return LocalDateTime.now().format(TIMESTAMP_FORMATTER);
    }

    /**
     * timestamp를 LocalDateTime으로 변환
     * @return LocalDateTime (파싱 실패 시 null)
     */
    @JsonIgnore
    public LocalDateTime getTimestampAsLocalDateTime() {
        if (timestamp == null || timestamp.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(timestamp, TIMESTAMP_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }

    // ========================================
    // 상태 확인 메서드
    // ========================================

    /**
     * 성공 여부 확인
     */
    @JsonIgnore
    public boolean isSuccess() {
        return "SUCCESS".equalsIgnoreCase(code) || "OK".equalsIgnoreCase(code);
    }

    /**
     * 에러 여부 확인
     */
    @JsonIgnore
    public boolean isError() {
        return !isSuccess();
    }

    // ========================================
    // Static Factory Methods
    // ========================================

    /**
     * 성공 응답 (메시지 없음)
     */
    public static InternalResponse success() {
        InternalResponse response = new InternalResponse();
        response.setCode("SUCCESS");
        response.setTimestamp(nowTimestamp());
        response.setAdditionalFields(new HashMap<>());
        return response;
    }

    /**
     * 성공 응답 (메시지 포함)
     */
    public static InternalResponse success(String message) {
        InternalResponse response = success();
        response.setMessage(message);
        return response;
    }

    /**
     * 성공 응답 (단일 필드 데이터)
     */
    public static InternalResponse success(String fieldName, Object data) {
        InternalResponse response = success();
        response.setAdditionalField(fieldName, data);
        return response;
    }

    /**
     * 성공 응답 (메시지 + 단일 필드 데이터)
     */
    public static InternalResponse success(String message, String fieldName, Object data) {
        InternalResponse response = success(message);
        response.setAdditionalField(fieldName, data);
        return response;
    }

    /**
     * 에러 응답
     */
    public static InternalResponse error(String code, String message) {
        InternalResponse response = new InternalResponse();
        response.setCode(code);
        response.setMessage(message);
        response.setTimestamp(nowTimestamp());
        response.setAdditionalFields(new HashMap<>());
        return response;
    }

    /**
     * 에러 응답 (기본 코드)
     */
    public static InternalResponse error(String message) {
        return error("ERROR", message);
    }

    // ========================================
    // Builder 패턴
    // ========================================

    public static InternalResponseBuilder builder() {
        return new InternalResponseBuilder();
    }

    /**
     * 빌더 클래스
     */
    public static class InternalResponseBuilder {
        private String code = "SUCCESS";
        private String message;
        private String requestId;
        private String timestamp;
        private final Map<String, Object> fields = new HashMap<>();

        public InternalResponseBuilder code(String code) {
            this.code = code;
            return this;
        }

        public InternalResponseBuilder message(String message) {
            this.message = message;
            return this;
        }

        public InternalResponseBuilder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public InternalResponseBuilder timestamp(String timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * 동적 필드 추가
         */
        public InternalResponseBuilder field(String key, Object value) {
            this.fields.put(key, value);
            return this;
        }

        /**
         * 여러 필드 추가
         */
        public InternalResponseBuilder fields(Map<String, Object> fields) {
            this.fields.putAll(fields);
            return this;
        }

        public InternalResponse build() {
            InternalResponse response = new InternalResponse();
            response.setCode(this.code);
            response.setMessage(this.message);
            response.setRequestId(this.requestId);
            response.setTimestamp(this.timestamp != null ? this.timestamp : nowTimestamp());
            response.setAdditionalFields(new HashMap<>(this.fields));
            return response;
        }
    }
}