package org.softwiz.platform.iot.common.lib.util;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 날짜/시간 포맷 변환 종합 유틸리티
 * 
 * 기능:
 * 1. String 날짜 → 다른 포맷 String 변환
 * 2. LocalDateTime → 포맷 String 변환
 * 3. LocalDate → 포맷 String 변환
 * 4. String → LocalDateTime 파싱
 * 5. 날짜 부분 추출 (년월일, 시분초 등)
 * 
 * 사용 예시:
 * - formatString("2025-01-15 10:30:00", "yyyy-MM-dd HH:mm:ss", "yyyyMMdd") → "20250115"
 * - formatDateTime(LocalDateTime.now(), "yyyy/MM/dd") → "2025/01/15"
 * - extractDate("2025-01-15 10:30:00") → "2025-01-15"
 */
@Slf4j
public class DateFormatUtil {

    // 자주 사용하는 포맷 상수
    public static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String DATE_FORMAT = "yyyy-MM-dd";
    public static final String COMPACT_DATETIME_FORMAT = "yyyyMMddHHmmss";
    public static final String COMPACT_DATE_FORMAT = "yyyyMMdd";
    public static final String KOREAN_DATETIME_FORMAT = "yyyy년 MM월 dd일 HH:mm:ss";
    public static final String KOREAN_DATE_FORMAT = "yyyy년 MM월 dd일";
    public static final String SLASH_DATE_FORMAT = "yyyy/MM/dd";
    public static final String SLASH_DATETIME_FORMAT = "yyyy/MM/dd HH:mm:ss";
    public static final String TIME_FORMAT = "HH:mm:ss";
    public static final String SHORT_TIME_FORMAT = "HH:mm";
    public static final String ISO_DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";

    /**
     * String 날짜를 다른 포맷의 String으로 변환
     * 
     * @param dateStr 원본 날짜 문자열
     * @param inputFormat 원본 포맷 패턴
     * @param outputFormat 변환할 포맷 패턴
     * @return 변환된 날짜 문자열
     * 
     * @example
     * formatString("2025-01-15 10:30:00", "yyyy-MM-dd HH:mm:ss", "yyyyMMdd")
     * → "20250115"
     */
    public static String formatString(String dateStr, String inputFormat, String outputFormat) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        try {
            DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern(inputFormat);
            DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern(outputFormat);
            
            // 시간 정보가 있는지 확인
            if (inputFormat.contains("HH") || inputFormat.contains("mm") || inputFormat.contains("ss")) {
                LocalDateTime dateTime = LocalDateTime.parse(dateStr, inputFormatter);
                return dateTime.format(outputFormatter);
            } else {
                LocalDate date = LocalDate.parse(dateStr, inputFormatter);
                return date.format(outputFormatter);
            }
        } catch (DateTimeParseException e) {
            log.error("Failed to parse date string: {} with format: {}", dateStr, inputFormat, e);
            return null;
        }
    }

    /**
     * String 날짜를 다른 포맷의 String으로 변환 (자동 포맷 감지)
     * 일반적인 날짜 포맷을 자동으로 감지하여 변환
     * 
     * @param dateStr 원본 날짜 문자열
     * @param outputFormat 변환할 포맷 패턴
     * @return 변환된 날짜 문자열
     * 
     * @example
     * formatStringAuto("2025-01-15 10:30:00", "yyyyMMdd") → "20250115"
     * formatStringAuto("2025/01/15", "yyyy-MM-dd") → "2025-01-15"
     */
    public static String formatStringAuto(String dateStr, String outputFormat) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        // 자주 사용하는 포맷들을 순서대로 시도
        String[] commonFormats = {
            DATETIME_FORMAT,              // yyyy-MM-dd HH:mm:ss
            DATE_FORMAT,                  // yyyy-MM-dd
            COMPACT_DATETIME_FORMAT,      // yyyyMMddHHmmss
            COMPACT_DATE_FORMAT,          // yyyyMMdd
            SLASH_DATETIME_FORMAT,        // yyyy/MM/dd HH:mm:ss
            SLASH_DATE_FORMAT,            // yyyy/MM/dd
            ISO_DATETIME_FORMAT           // yyyy-MM-dd'T'HH:mm:ss
        };

        for (String format : commonFormats) {
            String result = formatString(dateStr, format, outputFormat);
            if (result != null) {
                return result;
            }
        }

        log.warn("Unable to auto-detect format for date string: {}", dateStr);
        return null;
    }

    /**
     * LocalDateTime을 지정된 포맷의 String으로 변환
     * 
     * @param dateTime LocalDateTime 객체
     * @param format 변환할 포맷 패턴
     * @return 변환된 날짜 문자열
     * 
     * @example
     * formatDateTime(LocalDateTime.now(), "yyyy/MM/dd HH:mm") → "2025/01/15 10:30"
     */
    public static String formatDateTime(LocalDateTime dateTime, String format) {
        if (dateTime == null) {
            return null;
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
            return dateTime.format(formatter);
        } catch (Exception e) {
            log.error("Failed to format LocalDateTime: {} with format: {}", dateTime, format, e);
            return null;
        }
    }

    /**
     * LocalDate를 지정된 포맷의 String으로 변환
     * 
     * @param date LocalDate 객체
     * @param format 변환할 포맷 패턴
     * @return 변환된 날짜 문자열
     * 
     * @example
     * formatDate(LocalDate.now(), "yyyyMMdd") → "20250115"
     */
    public static String formatDate(LocalDate date, String format) {
        if (date == null) {
            return null;
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
            return date.format(formatter);
        } catch (Exception e) {
            log.error("Failed to format LocalDate: {} with format: {}", date, format, e);
            return null;
        }
    }

    /**
     * String을 LocalDateTime으로 파싱
     * 
     * @param dateStr 날짜 문자열
     * @param format 파싱할 포맷 패턴
     * @return LocalDateTime 객체
     * 
     * @example
     * parseDateTime("20250115103000", "yyyyMMddHHmmss") → LocalDateTime
     */
    public static LocalDateTime parseDateTime(String dateStr, String format) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
            return LocalDateTime.parse(dateStr, formatter);
        } catch (DateTimeParseException e) {
            log.error("Failed to parse date string: {} with format: {}", dateStr, format, e);
            return null;
        }
    }

    /**
     * String을 LocalDate로 파싱
     * 
     * @param dateStr 날짜 문자열
     * @param format 파싱할 포맷 패턴
     * @return LocalDate 객체
     * 
     * @example
     * parseDate("20250115", "yyyyMMdd") → LocalDate
     */
    public static LocalDate parseDate(String dateStr, String format) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
            return LocalDate.parse(dateStr, formatter);
        } catch (DateTimeParseException e) {
            log.error("Failed to parse date string: {} with format: {}", dateStr, format, e);
            return null;
        }
    }

    /**
     * 날짜 문자열에서 날짜 부분만 추출 (시간 제거)
     * 
     * @param dateTimeStr 날짜시간 문자열
     * @return 날짜 부분만 포함된 문자열 (yyyy-MM-dd 형식)
     * 
     * @example
     * extractDate("2025-01-15 10:30:00") → "2025-01-15"
     * extractDate("20250115103000") → "2025-01-15"
     */
    public static String extractDate(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            return null;
        }

        // 공백으로 분리 시도 (yyyy-MM-dd HH:mm:ss 형식)
        if (dateTimeStr.contains(" ")) {
            return dateTimeStr.split(" ")[0];
        }

        // 컴팩트 형식 (yyyyMMddHHmmss)인 경우
        if (dateTimeStr.matches("\\d{14}")) {
            String year = dateTimeStr.substring(0, 4);
            String month = dateTimeStr.substring(4, 6);
            String day = dateTimeStr.substring(6, 8);
            return year + "-" + month + "-" + day;
        }

        // 컴팩트 날짜만 (yyyyMMdd)
        if (dateTimeStr.matches("\\d{8}")) {
            String year = dateTimeStr.substring(0, 4);
            String month = dateTimeStr.substring(4, 6);
            String day = dateTimeStr.substring(6, 8);
            return year + "-" + month + "-" + day;
        }

        // 이미 날짜 형식인 경우
        if (dateTimeStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return dateTimeStr;
        }

        return null;
    }

    /**
     * 날짜 문자열에서 시간 부분만 추출
     * 
     * @param dateTimeStr 날짜시간 문자열
     * @return 시간 부분만 포함된 문자열 (HH:mm:ss 형식)
     * 
     * @example
     * extractTime("2025-01-15 10:30:00") → "10:30:00"
     * extractTime("20250115103000") → "10:30:00"
     */
    public static String extractTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            return null;
        }

        // 공백으로 분리 시도 (yyyy-MM-dd HH:mm:ss 형식)
        if (dateTimeStr.contains(" ")) {
            String[] parts = dateTimeStr.split(" ");
            if (parts.length > 1) {
                return parts[1];
            }
        }

        // 컴팩트 형식 (yyyyMMddHHmmss)인 경우
        if (dateTimeStr.matches("\\d{14}")) {
            String hour = dateTimeStr.substring(8, 10);
            String minute = dateTimeStr.substring(10, 12);
            String second = dateTimeStr.substring(12, 14);
            return hour + ":" + minute + ":" + second;
        }

        return null;
    }

    /**
     * 년도 추출
     * 
     * @param dateStr 날짜 문자열
     * @return 년도 (yyyy)
     * 
     * @example
     * extractYear("2025-01-15") → "2025"
     */
    public static String extractYear(String dateStr) {
        String date = extractDate(dateStr);
        if (date == null) return null;
        
        return date.split("-")[0];
    }

    /**
     * 월 추출
     * 
     * @param dateStr 날짜 문자열
     * @return 월 (MM)
     * 
     * @example
     * extractMonth("2025-01-15") → "01"
     */
    public static String extractMonth(String dateStr) {
        String date = extractDate(dateStr);
        if (date == null) return null;
        
        String[] parts = date.split("-");
        return parts.length > 1 ? parts[1] : null;
    }

    /**
     * 일 추출
     * 
     * @param dateStr 날짜 문자열
     * @return 일 (dd)
     * 
     * @example
     * extractDay("2025-01-15") → "15"
     */
    public static String extractDay(String dateStr) {
        String date = extractDate(dateStr);
        if (date == null) return null;
        
        String[] parts = date.split("-");
        return parts.length > 2 ? parts[2] : null;
    }

    /**
     * 현재 날짜시간을 지정된 포맷으로 반환
     * 
     * @param format 포맷 패턴
     * @return 현재 날짜시간 문자열
     * 
     * @example
     * now("yyyyMMdd") → "20250115"
     */
    public static String now(String format) {
        return formatDateTime(LocalDateTime.now(), format);
    }

    /**
     * 현재 날짜를 지정된 포맷으로 반환
     * 
     * @param format 포맷 패턴
     * @return 현재 날짜 문자열
     * 
     * @example
     * today("yyyy-MM-dd") → "2025-01-15"
     */
    public static String today(String format) {
        return formatDate(LocalDate.now(), format);
    }

    /**
     * 날짜 포맷이 유효한지 검증
     * 
     * @param dateStr 날짜 문자열
     * @param format 포맷 패턴
     * @return 유효 여부
     * 
     * @example
     * isValidFormat("2025-01-15", "yyyy-MM-dd") → true
     * isValidFormat("2025/01/15", "yyyy-MM-dd") → false
     */
    public static boolean isValidFormat(String dateStr, String format) {
        if (dateStr == null || format == null) {
            return false;
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
            if (format.contains("HH") || format.contains("mm") || format.contains("ss")) {
                LocalDateTime.parse(dateStr, formatter);
            } else {
                LocalDate.parse(dateStr, formatter);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}