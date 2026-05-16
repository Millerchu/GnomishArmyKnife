package com.gak.permission.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppIconStorageTypeTest {

    @Test
    void normalizeShouldAcceptPublicAsset() {
        assertEquals("PUBLIC_ASSET", AppIconStorageType.normalize("PUBLIC_ASSET"));
    }
}
