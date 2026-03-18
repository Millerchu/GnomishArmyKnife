package com.gak.passwordmemo.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PasswordMemoCryptoServiceTest {

    @Test
    void encryptAndDecryptShouldBeReversible() {
        PasswordMemoCryptoService service = new PasswordMemoCryptoService("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");

        PasswordMemoCryptoService.EncryptedPayload encryptedPayload = service.encrypt("ThirdPartyPassword123!");
        String decrypted = service.decrypt(encryptedPayload.ciphertext(), encryptedPayload.nonce());

        assertNotEquals("ThirdPartyPassword123!", encryptedPayload.ciphertext());
        assertEquals("ThirdPartyPassword123!", decrypted);
    }
}
