package com.gak.passwordmemo.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PasswordMemoCryptoServiceTest {

    @ParameterizedTest
    @ValueSource(strings = {"ThirdPartyPassword123!", "", "   ", " password "})
    void encryptAndDecryptShouldBeReversible(String password) {
        PasswordMemoCryptoService service = new PasswordMemoCryptoService("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");

        PasswordMemoCryptoService.EncryptedPayload encryptedPayload = service.encrypt(password);
        String decrypted = service.decrypt(encryptedPayload.ciphertext(), encryptedPayload.nonce());

        assertNotEquals(password, encryptedPayload.ciphertext());
        assertEquals(password, decrypted);
    }
}
