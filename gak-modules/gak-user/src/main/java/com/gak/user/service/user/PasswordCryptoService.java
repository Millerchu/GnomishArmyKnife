package com.gak.user.service.user;

import com.gak.framework.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import javax.crypto.Cipher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 登录密码加解密服务（RSA）。
 */
@Service
public class PasswordCryptoService {

    private final String publicKey;
    private final PrivateKey privateKey;

    public PasswordCryptoService() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            this.publicKey = toPem(keyPair.getPublic());
            this.privateKey = keyPair.getPrivate();
        } catch (GeneralSecurityException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "初始化密码加解密失败", e);
        }
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String decrypt(String encryptedPassword) {
        try {
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedPassword);
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] plain = cipher.doFinal(encryptedBytes);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException("PASSWORD_DECRYPT_FAILED", "密码解密失败");
        }
    }

    private String toPem(PublicKey key) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(key.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
    }
}
