package com.gak.passwordmemo.service;

import com.gak.framework.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 密码备忘录可逆加密服务。
 */
@Service
public class PasswordMemoCryptoService {

    private static final String AES_ALGORITHM = "AES";
    private static final String AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int NONCE_LENGTH = 12;
    private static final String DEFAULT_AES_KEY_BASE64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec secretKeySpec;

    public PasswordMemoCryptoService(@Value("${gak.security.memo.aes-key:" + DEFAULT_AES_KEY_BASE64 + "}") String base64Key) {
        byte[] key = Base64.getDecoder().decode(base64Key);
        if (key.length != 16 && key.length != 24 && key.length != 32) {
            throw new IllegalArgumentException("AES key 长度必须为 16/24/32 字节");
        }
        this.secretKeySpec = new SecretKeySpec(key, AES_ALGORITHM);
    }

    public EncryptedPayload encrypt(String plaintext) {
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            secureRandom.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_LENGTH, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new EncryptedPayload(
                    Base64.getEncoder().encodeToString(ciphertext),
                    Base64.getEncoder().encodeToString(nonce)
            );
        } catch (GeneralSecurityException e) {
            throw new BusinessException("PASSWORD_MEMO_ENCRYPT_FAILED", "密码备忘录加密失败");
        }
    }

    public String decrypt(String ciphertext, String nonce) {
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKeySpec,
                    new GCMParameterSpec(GCM_TAG_LENGTH, Base64.getDecoder().decode(nonce))
            );
            byte[] plain = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new BusinessException("PASSWORD_MEMO_DECRYPT_FAILED", "密码备忘录解密失败");
        }
    }

    public record EncryptedPayload(String ciphertext, String nonce) {
    }
}
