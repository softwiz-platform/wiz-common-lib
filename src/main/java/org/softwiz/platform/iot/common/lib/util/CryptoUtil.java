package org.softwiz.platform.iot.common.lib.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Component
public class CryptoUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final int KEY_SIZE = 32;
    private static final int IV_SIZE = 16;

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
}