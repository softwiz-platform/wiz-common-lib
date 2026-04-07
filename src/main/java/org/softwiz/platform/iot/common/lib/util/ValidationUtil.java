package org.softwiz.platform.iot.common.lib.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 검증 유틸리티
 * - 이메일, 비밀번호, Provider 등의 형식 검증
 */
@Slf4j
@Component
public class ValidationUtil {

    private static final List<String> VALID_PROVIDERS = Arrays.asList("kakao", "naver", "google", "apple");
    private static final String EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@(.+)$";
    private static final int MIN_PASSWORD_LENGTH = 8;

    /**
     * 이메일 형식 검증
     * 
     * @param email 검증할 이메일
     * @return 유효하면 true
     */
    public boolean isValidEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return email.matches(EMAIL_PATTERN);
    }

    /**
     * 비밀번호 형식 검증 (최소 길이만 체크)
     * 
     * @param password 검증할 비밀번호
     * @return 유효하면 true
     */
    public boolean isValidPasswordFormat(String password) {
        if (password == null) {
            return false;
        }
        return password.length() >= MIN_PASSWORD_LENGTH;
    }

    /**
     * 비밀번호 강도 검증 (영문 + 숫자 + 특수문자)
     * SignUpValidator에서 사용하는 것과 동일
     * 
     * @param password 검증할 비밀번호
     * @return 유효하면 true
     */
    public boolean isStrongPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            return false;
        }

        boolean hasLetter = password.matches(".*[a-zA-Z].*");
        boolean hasDigit = password.matches(".*\\d.*");
        boolean hasSpecial = password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*");

        return hasLetter && hasDigit && hasSpecial;
    }

    /**
     * Provider 유효성 검증
     * 
     * @param provider 검증할 Provider
     * @return 유효하면 true
     */
    public boolean isValidProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return false;
        }
        return VALID_PROVIDERS.contains(provider.toLowerCase());
    }

    /**
     * Device ID 형식 검증
     * UUID 또는 16진수 문자열
     * 
     * @param deviceId 검증할 Device ID
     * @return 유효하면 true
     */
    public boolean isValidDeviceId(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return false;
        }

        // UUID 형식 (8-4-4-4-12)
        String uuidPattern = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
        if (deviceId.matches(uuidPattern)) {
            return true;
        }

        // 16진수 문자열 (Android ID: 8~64자)
        String hexPattern = "^[0-9a-fA-F]{8,64}$";
        return deviceId.matches(hexPattern);
    }

    /**
     * 유효한 Provider 목록 조회
     * 
     * @return Provider 목록
     */
    public List<String> getValidProviders() {
        return VALID_PROVIDERS;
    }
}