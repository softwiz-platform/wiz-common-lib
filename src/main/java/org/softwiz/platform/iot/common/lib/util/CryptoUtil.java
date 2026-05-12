package org.softwiz.platform.iot.common.lib.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Component
public class CryptoUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final String TRANSFORMATION_GCM = "AES/GCM/NoPadding";
    private static final int KEY_SIZE = 32;
    private static final int IV_SIZE = 16;
    // GCM IV는 12바이트가 표준 (96bit)
    private static final int GCM_IV_SIZE = 12;
    // GCM 인증 태그 길이 (128bit)
    private static final int GCM_TAG_BIT_LENGTH = 128;

    private final SecretKeySpec secretKeySpec;
    private final IvParameterSpec ivParameterSpec;

    public CryptoUtil(
            @Value("${crypto.secret-key}") String secretKey,
            @Value("${crypto.iv}") String iv) {

        if (secretKey.getBytes(StandardCharsets.UTF_8).length != KEY_SIZE) {
            throw new IllegalArgumentException(
                    "Secret key must be " + KEY_SIZE + " bytes for AES-256");
        }

        this.secretKeySpec = new SecretKeySpec(
                secretKey.getBytes(StandardCharsets.UTF_8),
                ALGORITHM
        );

        byte[] ivBytes = iv.getBytes(StandardCharsets.UTF_8);
        if (ivBytes.length != IV_SIZE) {
            throw new IllegalArgumentException("IV must be " + IV_SIZE + " bytes");
        }
        this.ivParameterSpec = new IvParameterSpec(ivBytes);
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return "";
        }

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec);

            byte[] encryptedBytes = cipher.doFinal(
                    plainText.getBytes(StandardCharsets.UTF_8)
            );

            return Base64.getEncoder().encodeToString(encryptedBytes);

        } catch (Exception e) {
            log.error("Encryption failed: {}", e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("Encryption error details", e);
            }
            return "";
        }
    }

    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) {
            return "";
        }

        try {
            byte[] encryptedBytes = Base64.getDecoder().decode(encrypted);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);

            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Decryption failed: {}", e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("Failed encrypted value length: {}, preview: {}...",
                        encrypted.length(),
                        encrypted.substring(0, Math.min(10, encrypted.length())));
            }
            return "";
        }
    }

    public String encryptUserId(String userId) {
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("userId cannot be null or empty");
        }

        try {
            String encrypted = encrypt(userId);
            if (encrypted == null || encrypted.isEmpty()) {
                throw new IllegalStateException("Encryption returned null for userId");
            }
            return encrypted;
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to encrypt userId", e);
        }
    }

    public String decryptUserId(String encryptedUserId) {
        if (encryptedUserId == null || encryptedUserId.isEmpty()) {
            throw new IllegalArgumentException("encryptedUserId cannot be null or empty");
        }

        try {
            String decrypted = decrypt(encryptedUserId);
            if (decrypted == null || decrypted.isEmpty()) {
                // 복호화 실패 시 평문으로 fallback (기존 미암호화 토큰 과도기 대응)
                log.warn("decryptUserId fallback to plaintext - value may not be encrypted");
                return encryptedUserId;
            }
            return decrypted;
        } catch (RuntimeException e) {
            // 복호화 실패 시 평문으로 fallback (기존 미암호화 토큰 과도기 대응)
            log.warn("decryptUserId fallback to plaintext - value may not be encrypted");
            return encryptedUserId;
        }
    }

    /**
     * AES-256-GCM 암호화 (랜덤 IV 포함)
     * 결과 포맷: Base64([IV 12바이트][암호문][GCM Tag 16바이트])
     * 상대방 복호화 스펙: AES-256-GCM, IV=앞 12바이트, Tag=뒤 16바이트, 인코딩=Base64 Standard
     */
    public String encryptGcm(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return "";
        }

        try {
            // 매 호출마다 랜덤 IV 생성
            byte[] iv = new byte[GCM_IV_SIZE];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_BIT_LENGTH, iv));

            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // [IV 12바이트] + [암호문 + GCM Tag] 합쳐서 Base64 인코딩
            byte[] result = new byte[GCM_IV_SIZE + encryptedBytes.length];
            System.arraycopy(iv, 0, result, 0, GCM_IV_SIZE);
            System.arraycopy(encryptedBytes, 0, result, GCM_IV_SIZE, encryptedBytes.length);

            return Base64.getEncoder().encodeToString(result);

        } catch (Exception e) {
            log.error("GCM Encryption failed: {}", e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("GCM Encryption error details", e);
            }
            return "";
        }
    }

    /**
     * AES-256-GCM 복호화
     * 입력 포맷: Base64([IV 12바이트][암호문][GCM Tag 16바이트])
     * GCM Tag 불일치(변조) 시 복호화 실패 처리
     */
    public String decryptGcm(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) {
            return "";
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(encrypted);

            // 최소 길이 검증: IV(12) + Tag(16) = 28바이트 이상이어야 함
            if (decoded.length < GCM_IV_SIZE + GCM_TAG_BIT_LENGTH / 8) {
                log.error("GCM Decryption failed: payload too short");
                return "";
            }

            // 앞 12바이트 = IV
            byte[] iv = new byte[GCM_IV_SIZE];
            System.arraycopy(decoded, 0, iv, 0, GCM_IV_SIZE);

            // 나머지 = 암호문 + GCM Tag
            byte[] cipherTextWithTag = new byte[decoded.length - GCM_IV_SIZE];
            System.arraycopy(decoded, GCM_IV_SIZE, cipherTextWithTag, 0, cipherTextWithTag.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION_GCM);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_BIT_LENGTH, iv));

            byte[] decryptedBytes = cipher.doFinal(cipherTextWithTag);
            return new String(decryptedBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("GCM Decryption failed: {}", e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("Failed encrypted value length: {}, preview: {}...",
                        encrypted.length(),
                        encrypted.substring(0, Math.min(10, encrypted.length())));
            }
            return "";
        }
    }
}