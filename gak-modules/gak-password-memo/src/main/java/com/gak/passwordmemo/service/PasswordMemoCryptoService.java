package com.gak.passwordmemo.service;

import com.gak.framework.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 密码备忘录可逆加密服务。强制由外部注入 AES 密钥并绑定 ownerUserId:memoId 作为 GCM AAD。
 */
@Service
public class PasswordMemoCryptoService {

    private static final String AES_ALGORITHM = "AES";
    private static final String AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int NONCE_LENGTH = 12;
    private static final String V2_PREFIX = "v2:";
    private static final byte[] KNOWN_WEAK_DEFAULT_KEY = Base64.getDecoder()
            .decode("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec secretKeySpec;

    public PasswordMemoCryptoService(@Value("${gak.security.memo.aes-key:}") String base64Key) {
        if (!StringUtils.hasText(base64Key)) {
            throw new IllegalStateException("未配置密码备忘录加密密钥 gak.security.memo.aes-key（环境变量 GAK_PASSWORD_MEMO_AES_KEY）");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("密码备忘录加密密钥 gak.security.memo.aes-key 不是合法的 Base64 编码", exception);
        }
        if (key.length != 16 && key.length != 24 && key.length != 32) {
            throw new IllegalStateException("AES key 长度必须为 16/24/32 字节");
        }
        if (isKnownWeakKey(key)) {
            throw new IllegalStateException("密码备忘录加密密钥不能使用已知弱密钥或默认占位密钥");
        }
        this.secretKeySpec = new SecretKeySpec(key, AES_ALGORITHM);
    }

    public EncryptedPasswordPayload encrypt(Long ownerUserId, Long memoId, String plaintext) {
        if (ownerUserId == null || memoId == null || plaintext == null) {
            throw new BusinessException("PASSWORD_MEMO_ENCRYPT_FAILED", "密码备忘录加密参数不完整");
        }
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            secureRandom.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_LENGTH, nonce));
            cipher.updateAAD(buildAad(ownerUserId, memoId));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new EncryptedPasswordPayload(
                    V2_PREFIX + Base64.getEncoder().encodeToString(ciphertext),
                    Base64.getEncoder().encodeToString(nonce)
            );
        } catch (GeneralSecurityException e) {
            throw new BusinessException("PASSWORD_MEMO_ENCRYPT_FAILED", "密码备忘录加密失败");
        }
    }

    public String decrypt(Long ownerUserId, Long memoId, String ciphertext, String nonce) {
        if (ownerUserId == null || memoId == null || !StringUtils.hasText(ciphertext) || !StringUtils.hasText(nonce)) {
            throw new BusinessException("PASSWORD_MEMO_DECRYPT_FAILED", "密码备忘录解密参数不完整");
        }
        try {
            boolean hasV2Prefix = ciphertext.startsWith(V2_PREFIX);
            String encodedCiphertext = hasV2Prefix ? ciphertext.substring(V2_PREFIX.length()) : ciphertext;
            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKeySpec,
                    new GCMParameterSpec(GCM_TAG_LENGTH, Base64.getDecoder().decode(nonce))
            );
            if (hasV2Prefix) {
                cipher.updateAAD(buildAad(ownerUserId, memoId));
            }
            byte[] plain = cipher.doFinal(Base64.getDecoder().decode(encodedCiphertext));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new BusinessException("PASSWORD_MEMO_DECRYPT_FAILED", "密码备忘录解密失败");
        }
    }

    private byte[] buildAad(Long ownerUserId, Long memoId) {
        return (ownerUserId + ":" + memoId).getBytes(StandardCharsets.UTF_8);
    }

    private boolean isKnownWeakKey(byte[] key) {
        if (MessageDigest.isEqual(key, KNOWN_WEAK_DEFAULT_KEY)) {
            return true;
        }
        byte first = key[0];
        for (int i = 1; i < key.length; i++) {
            if (key[i] != first) {
                return false;
            }
        }
        return true;
    }
}
