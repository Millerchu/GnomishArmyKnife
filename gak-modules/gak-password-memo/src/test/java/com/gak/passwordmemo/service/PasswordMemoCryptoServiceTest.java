package com.gak.passwordmemo.service;

import com.gak.framework.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordMemoCryptoServiceTest {

    private static final String TEST_KEY = "M2Y5YzhlN2Q2YjVhNGYzZTJkMWMwOWI4YTc2ZjVlNGQ=";
    private static final String KNOWN_WEAK_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @ParameterizedTest
    @ValueSource(strings = {"ThirdPartyPassword123!", "", "   ", " password "})
    void encryptAndDecryptShouldBeReversibleWithBoundAad(String password) {
        PasswordMemoCryptoService service = new PasswordMemoCryptoService(TEST_KEY);

        EncryptedPasswordPayload encryptedPayload = service.encrypt(10L, 20L, password);
        String decrypted = service.decrypt(10L, 20L, encryptedPayload.ciphertext(), encryptedPayload.nonce());

        assertTrue(encryptedPayload.ciphertext().startsWith("v2:"));
        assertNotEquals(password, encryptedPayload.ciphertext());
        assertEquals(password, decrypted);
    }

    @Test
    void decryptShouldFailWhenAadDiffers() {
        PasswordMemoCryptoService service = new PasswordMemoCryptoService(TEST_KEY);
        EncryptedPasswordPayload encryptedPayload = service.encrypt(10L, 20L, "Secret123!");

        assertThrows(BusinessException.class,
                () -> service.decrypt(11L, 20L, encryptedPayload.ciphertext(), encryptedPayload.nonce()));
        assertThrows(BusinessException.class,
                () -> service.decrypt(10L, 21L, encryptedPayload.ciphertext(), encryptedPayload.nonce()));
    }

    @Test
    void constructorShouldFailFastWhenKeyMissingOrWeak() {
        assertThrows(IllegalStateException.class, () -> new PasswordMemoCryptoService(""));
        assertThrows(IllegalStateException.class, () -> new PasswordMemoCryptoService("not-base64!"));
        assertThrows(IllegalStateException.class, () -> new PasswordMemoCryptoService(KNOWN_WEAK_KEY));
        assertThrows(IllegalStateException.class,
                () -> new PasswordMemoCryptoService(Base64.getEncoder().encodeToString(new byte[32])));
        assertThrows(IllegalStateException.class,
                () -> new PasswordMemoCryptoService(Base64.getEncoder().encodeToString("short".getBytes(StandardCharsets.UTF_8))));
    }
}
