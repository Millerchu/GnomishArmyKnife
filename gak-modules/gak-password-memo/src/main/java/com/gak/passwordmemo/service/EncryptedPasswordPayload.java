package com.gak.passwordmemo.service;

/**
 * 密码备忘录加密结果载荷。
 */
public record EncryptedPasswordPayload(String ciphertext, String nonce) {
}
