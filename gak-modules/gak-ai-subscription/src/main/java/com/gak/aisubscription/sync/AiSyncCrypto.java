package com.gak.aisubscription.sync;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 独立主密钥由服务端环境注入；未配置不阻止手动业务启动。 */
@Component
public class AiSyncCrypto {
    private static final int NONCE_BYTES = 12;
    private static final int KEY_BYTES = 32;
    private final byte[] masterKey;
    private final SecureRandom random = new SecureRandom();
    public AiSyncCrypto(@Value("${gak.ai-sync.master-key:}") String encodedKey) {
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(encodedKey); }
        catch (IllegalArgumentException ignored) { decoded = new byte[0]; }
        masterKey = decoded.length == KEY_BYTES ? decoded : new byte[0];
    }
    public boolean configured() { return masterKey.length == KEY_BYTES; }
    public String encrypt(Long owner, Long account, String plaintext) {
        requireConfigured();
        try {
            byte[] nonce = new byte[NONCE_BYTES]; random.nextBytes(nonce);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce, owner, account);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] envelope = Arrays.copyOf(nonce, nonce.length + ciphertext.length);
            System.arraycopy(ciphertext, 0, envelope, nonce.length, ciphertext.length);
            return "v1:" + Base64.getEncoder().encodeToString(envelope);
        } catch (Exception ignored) { throw new AiSyncFailure("ENCRYPTION_UNAVAILABLE", false, 0); }
    }
    public String decrypt(Long owner, Long account, String envelope) {
        requireConfigured();
        try {
            if (envelope == null || !envelope.startsWith("v1:")) throw new IllegalArgumentException();
            byte[] bytes = Base64.getDecoder().decode(envelope.substring(3));
            if (bytes.length <= NONCE_BYTES) throw new IllegalArgumentException();
            Cipher cipher = cipher(Cipher.DECRYPT_MODE, Arrays.copyOf(bytes, NONCE_BYTES), owner, account);
            return new String(cipher.doFinal(Arrays.copyOfRange(bytes, NONCE_BYTES, bytes.length)), StandardCharsets.UTF_8);
        } catch (Exception ignored) { throw new AiSyncFailure("KEY_REAUTH_REQUIRED", false, 0); }
    }
    private Cipher cipher(int mode, byte[] nonce, Long owner, Long account) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(masterKey, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD((owner + ":" + account).getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
    private void requireConfigured() {
        if (!configured()) throw new AiSyncFailure("SERVER_KEY_NOT_CONFIGURED", false, 0);
    }
}
