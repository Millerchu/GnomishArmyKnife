package com.gak.attachment.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UserAvatarAttachmentAccessPolicyTest {

    private final UserAvatarAttachmentAccessPolicy policy = new UserAvatarAttachmentAccessPolicy();

    @Test
    void shouldAllowOnlyAvatarOwnerToViewAndManage() {
        assertTrue(policy.canView(7L, 7L));
        assertTrue(policy.canManage(7L, 7L));
        assertFalse(policy.canView(8L, 7L));
        assertFalse(policy.canManage(8L, 7L));
    }
}
