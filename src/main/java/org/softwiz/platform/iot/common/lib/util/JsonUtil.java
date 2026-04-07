package org.softwiz.platform.iot.common.lib.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * JSON 직렬화/역직렬화 유틸리티
 *
 * <p>ObjectMapper를 중앙에서 관리하여 메모리 효율성과 설정 일관성을 보장합니다.</p>
 *
 * <h3>설계 원칙</h3>
 * <ul>
 *   <li>ObjectMapper 인스턴스를 직접 노출하지 않음 (설정 변경 방지)</li>
 *   <li>필요한 기능만 메서드로 제공</li>
 *   <li>마이크로서비스 간 호환성을 위한 기본 설정 적용</li>
 * </ul>
 *
 * <h3>기본 설정</h3>
 * <ul>
 *   <li>JavaTimeModule: LocalDateTime 등 Java 8 날짜/시간 지원</li>
 *   <li>NON_NULL: null 필드 제외</li>
 *   <li>FAIL_ON_UNKNOWN_PROPERTIES=false: 알 수 없는 필드 무시 (버전 호환성)</li>
 *   <li>WRITE_DATES_AS_TIMESTAMPS=false: 날짜를 ISO 문자열로 직렬화</li>
 * </ul>
 *
 * <pre>
 * 사용 예시:
 * {@code
 * // 객체 → JSON
 * String json = JsonUtil.toJson(myObject);
 *
 * // JSON → 객체
 * MyClass obj = JsonUtil.fromJson(json, MyClass.class);
 *
 * // JSON → Map
 * Map<String, Object> map = JsonUtil.toMap(json);
 *
 * // JSON → List
 * List<MyClass> list = JsonUtil.toList(json, MyClass.class);
 *
 * // Pretty Print
 * String prettyJson = JsonUtil.toPrettyJson(myObject);
 * }
 * </pre>
 *
 * <h3>주의사항</h3>
 * <p>ObjectMapper 인스턴스를 직접 얻을 수 없습니다.
 * 특수한 설정이 필요한 경우 별도의 ObjectMapper를 생성하세요.</p>
 */
@Slf4j
public final class JsonUtil {

    private JsonUtil() {
        // Utility class - 인스턴스 생성 방지
    }

    /**
     * 내부 ObjectMapper (외부 노출 금지)
     *
     * <p>설정이 한 번 적용된 후 변경되지 않도록 private으로 유지합니다.</p>
     */
    private static final ObjectMapper MAPPER = createObjectMapper();

    /**
     * ObjectMapper 생성 및 설정
     */
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Java 8 날짜/시간 지원 (LocalDateTime, LocalDate 등)
        mapper.registerModule(new JavaTimeModule());

        // null 필드 제외 (불필요한 데이터 전송 방지)
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // 알 수 없는 필드 무시 (마이크로서비스 버전 차이 대응)
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // 빈 객체 허용
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        // LocalDateTime을 ISO 문자열로 직렬화 (배열 아님)
        // 예: "2024-12-02T10:30:00" (O), [2024,12,2,10,30,0] (X)
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        return mapper;
    }

    // ========================================
    // 직렬화 (Object → JSON String)
    // ========================================

    /**
     * 객체를 JSON 문자열로 변환
     *
     * @param obj 변환할 객체
     * @return JSON 문자열 (실패 시 null)
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON 직렬화 실패 - class: {}, error: {}",
                    obj.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    /**
     * 객체를 JSON 문자열로 변환 (예외 발생)
     *
     * @param obj 변환할 객체
     * @return JSON 문자열
     * @throws RuntimeException 직렬화 실패 시
     */
    public static String toJsonOrThrow(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 직렬화 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 객체를 Pretty JSON 문자열로 변환 (가독성 좋은 포맷)
     *
     * @param obj 변환할 객체
     * @return Pretty JSON 문자열 (실패 시 null)
     */
    public static String toPrettyJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON Pretty 직렬화 실패 - class: {}, error: {}",
                    obj.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    /**
     * 객체를 byte 배열로 변환
     *
     * @param obj 변환할 객체
     * @return byte 배열 (실패 시 null)
     */
    public static byte[] toBytes(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsBytes(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON byte 변환 실패 - class: {}, error: {}",
                    obj.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    // ========================================
    // 역직렬화 (JSON String → Object)
    // ========================================

    /**
     * JSON 문자열을 객체로 변환
     *
     * @param json JSON 문자열
     * @param clazz 대상 클래스
     * @return 변환된 객체 (실패 시 null)
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("JSON 역직렬화 실패 - class: {}, error: {}",
                    clazz.getSimpleName(), e.getMessage());
            return null;
        }
    }

    /**
     * JSON 문자열을 객체로 변환 (예외 발생)
     *
     * @param json JSON 문자열
     * @param clazz 대상 클래스
     * @return 변환된 객체
     * @throws RuntimeException 역직렬화 실패 시
     */
    public static <T> T fromJsonOrThrow(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 역직렬화 실패: " + e.getMessage(), e);
        }
    }

    /**
     * JSON 문자열을 TypeReference로 변환 (Generic 타입용)
     *
     * <pre>
     * 사용 예시:
     * {@code
     * List<User> users = JsonUtil.fromJson(json, new TypeReference<List<User>>() {});
     * Map<String, List<Item>> map = JsonUtil.fromJson(json, new TypeReference<Map<String, List<Item>>>() {});
     * }
     * </pre>
     *
     * @param json JSON 문자열
     * @param typeRef TypeReference
     * @return 변환된 객체 (실패 시 null)
     */
    public static <T> T fromJson(String json, TypeReference<T> typeRef) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            log.error("JSON 역직렬화 실패 - typeRef: {}, error: {}",
                    typeRef.getType().getTypeName(), e.getMessage());
            return null;
        }
    }

    /**
     * byte 배열을 객체로 변환
     *
     * @param bytes byte 배열
     * @param clazz 대상 클래스
     * @return 변환된 객체 (실패 시 null)
     */
    public static <T> T fromBytes(byte[] bytes, Class<T> clazz) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return MAPPER.readValue(bytes, clazz);
        } catch (Exception e) {
            log.error("byte 역직렬화 실패 - class: {}, error: {}",
                    clazz.getSimpleName(), e.getMessage());
            return null;
        }
    }

    // ========================================
    // Map/List 변환
    // ========================================

    /**
     * JSON 문자열을 Map으로 변환
     *
     * @param json JSON 문자열
     * @return Map (실패 시 null)
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMap(String json) {
        return fromJson(json, Map.class);
    }

    /**
     * 객체를 Map으로 변환
     *
     * @param obj 변환할 객체
     * @return Map (실패 시 null)
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMap(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MAPPER.convertValue(obj, Map.class);
        } catch (Exception e) {
            log.error("Map 변환 실패 - class: {}, error: {}",
                    obj.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    /**
     * JSON 문자열을 List로 변환
     *
     * @param json JSON 문자열
     * @param elementClass 리스트 요소 클래스
     * @return List (실패 시 null)
     */
    public static <T> List<T> toList(String json, Class<T> elementClass) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(json,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, elementClass));
        } catch (JsonProcessingException e) {
            log.error("List 역직렬화 실패 - elementClass: {}, error: {}",
                    elementClass.getSimpleName(), e.getMessage());
            return null;
        }
    }

    // ========================================
    // JsonNode 변환
    // ========================================

    /**
     * JSON 문자열을 JsonNode로 변환
     *
     * @param json JSON 문자열
     * @return JsonNode (실패 시 null)
     */
    public static JsonNode toJsonNode(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            log.error("JsonNode 변환 실패 - error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 객체를 JsonNode로 변환
     *
     * @param obj 변환할 객체
     * @return JsonNode (실패 시 null)
     */
    public static JsonNode toJsonNode(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MAPPER.valueToTree(obj);
        } catch (Exception e) {
            log.error("JsonNode 변환 실패 - class: {}, error: {}",
                    obj.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    // ========================================
    // 유틸리티 메서드
    // ========================================

    /**
     * JSON 문자열 유효성 검사
     *
     * @param json 검사할 JSON 문자열
     * @return 유효하면 true
     */
    public static boolean isValidJson(String json) {
        if (json == null || json.isEmpty()) {
            return false;
        }
        try {
            MAPPER.readTree(json);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    /**
     * 객체 타입 변환 (Object → 특정 클래스)
     *
     * <p>Map이나 LinkedHashMap 등을 특정 DTO로 변환할 때 유용합니다.</p>
     *
     * @param fromValue 원본 객체
     * @param toValueType 대상 클래스
     * @return 변환된 객체 (실패 시 null)
     */
    public static <T> T convertValue(Object fromValue, Class<T> toValueType) {
        if (fromValue == null) {
            return null;
        }
        try {
            return MAPPER.convertValue(fromValue, toValueType);
        } catch (Exception e) {
            log.error("타입 변환 실패 - from: {}, to: {}, error: {}",
                    fromValue.getClass().getSimpleName(),
                    toValueType.getSimpleName(),
                    e.getMessage());
            return null;
        }
    }

    /**
     * 객체를 다른 타입으로 변환 (TypeReference 지원)
     *
     * <p>제네릭 타입(List, Map 등)으로 변환할 때 사용합니다.</p>
     *
     * <pre>{@code
     * List<UserDto> users = JsonUtil.convertValue(data, new TypeReference<List<UserDto>>() {});
     * Map<String, Object> map = JsonUtil.convertValue(obj, new TypeReference<Map<String, Object>>() {});
     * }</pre>
     *
     * @param fromValue 원본 객체
     * @param typeReference 대상 타입 레퍼런스
     * @return 변환된 객체 (실패 시 null)
     */
    public static <T> T convertValue(Object fromValue, TypeReference<T> typeReference) {
        if (fromValue == null) {
            return null;
        }
        try {
            return MAPPER.convertValue(fromValue, typeReference);
        } catch (Exception e) {
            log.error("타입 변환 실패 - from: {}, to: {}, error: {}",
                    fromValue.getClass().getSimpleName(),
                    typeReference.getType().getTypeName(),
                    e.getMessage());
            return null;
        }
    }
}