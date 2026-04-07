package org.softwiz.platform.iot.common.lib.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 생년월일 및 나이 관련 유틸리티
 */
@Slf4j
@Component
public class BirthDateUtil {

    private static final DateTimeFormatter BIRTH_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int ADULT_AGE = 18;

    /**
     * 생년월일 문자열을 yyyyMMdd 형식으로 정규화
     *
     * @param birth 생년월일 (yyyyMMdd, yyyy-MM-dd 등)
     * @return yyyyMMdd 형식의 생년월일, 실패 시 null
     */
    public String normalizeBirthDate(String birth) {
        if (birth == null || birth.isBlank()) {
            return null;
        }

        String normalized = birth.trim();

        // 하이픈 제거
        if (normalized.contains("-")) {
            normalized = normalized.replace("-", "");
        }

        // yyyyMMdd 형식 검증
        if (normalized.matches("\\d{8}")) {
            return normalized;
        }

        log.warn("Invalid birth format: {}", birth);
        return null;
    }

    /**
     * 나이를 생년월일(yyyyMMdd)로 변환
     *
     * @param ageStr 나이 문자열
     * @return yyyyMMdd 형식의 생년월일 (1월 1일 기준), 실패 시 null
     */
    public String convertAgeToBirth(String ageStr) {
        if (ageStr == null || ageStr.isBlank()) {
            return null;
        }

        try {
            int age = Integer.parseInt(ageStr.trim());
            if (age < 0 || age > 150) {
                log.warn("Invalid age range: {}", age);
                return null;
            }

            int birthYear = LocalDate.now().getYear() - age;
            return String.format("%d0101", birthYear);
        } catch (NumberFormatException e) {
            log.warn("Invalid age format: {}", ageStr);
            return null;
        }
    }

    /**
     * 생년월일 또는 나이를 yyyyMMdd 형식으로 변환
     *
     * @param birth 생년월일 (우선순위 높음)
     * @param age 나이 (birth가 없을 때 사용)
     * @return yyyyMMdd 형식의 생년월일, 둘 다 없으면 null
     */
    public String parseBirthDate(String birth, String age) {
        // 생년월일 우선 처리
        String normalized = normalizeBirthDate(birth);
        if (normalized != null) {
            return normalized;
        }

        // 나이로 변환 시도
        return convertAgeToBirth(age);
    }

    /**
     * 생년월일(yyyyMMdd)로 미성년자 여부 판단
     *
     * @param birthDate yyyyMMdd 형식의 생년월일
     * @return "Y" (미성년자) 또는 "N" (성인)
     */
    public String isMinor(String birthDate) {
        if (birthDate == null || birthDate.isBlank()) {
            return "N";
        }

        try {
            int age = calculateAge(birthDate);
            return age < ADULT_AGE ? "Y" : "N";
        } catch (Exception e) {
            log.warn("Failed to determine minor status for birthDate: {}", birthDate, e);
            return "N";
        }
    }

    /**
     * 생년월일(yyyyMMdd)로 현재 나이 계산
     *
     * @param birthDate yyyyMMdd 형식의 생년월일
     * @return 만 나이
     */
    public int calculateAge(String birthDate) {
        if (birthDate == null || !birthDate.matches("\\d{8}")) {
            throw new IllegalArgumentException("Invalid birth date format: " + birthDate);
        }

        LocalDate birth = LocalDate.parse(birthDate, BIRTH_DATE_FORMATTER);
        LocalDate now = LocalDate.now();

        int age = now.getYear() - birth.getYear();

        // 생일이 아직 안 지났으면 나이 -1
        if (now.getMonthValue() < birth.getMonthValue() ||
                (now.getMonthValue() == birth.getMonthValue() && now.getDayOfMonth() < birth.getDayOfMonth())) {
            age--;
        }

        return age;
    }

    /**
     * 생년월일이 유효한지 검증
     *
     * @param birthDate yyyyMMdd 형식의 생년월일
     * @return 유효하면 true
     */
    public boolean isValidBirthDate(String birthDate) {
        if (birthDate == null || !birthDate.matches("\\d{8}")) {
            return false;
        }

        try {
            LocalDate birth = LocalDate.parse(birthDate, BIRTH_DATE_FORMATTER);
            LocalDate now = LocalDate.now();

            // 미래 날짜는 불가
            if (birth.isAfter(now)) {
                return false;
            }

            // 150년 전보다 이전은 불가
            LocalDate minDate = now.minusYears(150);
            return !birth.isBefore(minDate);

        } catch (Exception e) {
            return false;
        }
    }
}