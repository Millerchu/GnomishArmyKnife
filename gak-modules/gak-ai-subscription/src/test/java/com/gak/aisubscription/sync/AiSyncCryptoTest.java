package com.gak.aisubscription.sync;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AiSyncCryptoTest {
    private final AiSyncCrypto crypto = new AiSyncCrypto(Base64.getEncoder().encodeToString(new byte[32]));
    @Test void ciphertextIsRandomAndBoundToOwnerAndAccount() {
        String first = crypto.encrypt(1L, 2L, "admin-secret");
        assertNotEquals(first, crypto.encrypt(1L, 2L, "admin-secret"));
        assertFalse(first.contains("admin-secret"));
        assertEquals("admin-secret", crypto.decrypt(1L, 2L, first));
        assertThrows(AiSyncFailure.class, () -> crypto.decrypt(9L, 2L, first));
        assertThrows(AiSyncFailure.class, () -> crypto.decrypt(1L, 9L, first));
        assertThrows(AiSyncFailure.class, () -> crypto.decrypt(1L, 2L, first.substring(0, 10)));
    }
    @Test void missingMasterKeyDisablesOnlySync() {
        AiSyncCrypto missing = new AiSyncCrypto("");
        assertFalse(missing.configured());
        assertEquals("SERVER_KEY_NOT_CONFIGURED", assertThrows(AiSyncFailure.class,
                () -> missing.encrypt(1L, 2L, "secret")).getMessage());
    }
}
