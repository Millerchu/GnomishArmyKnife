package com.gak.user.dto;

/**
 * 登录密码加密公钥返回体。
 */
public class PublicKeyResponse {

    /**
     * Base64 编码的 RSA 公钥。
     */
    private final String publicKey;

    public PublicKeyResponse(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getPublicKey() {
        return publicKey;
    }
}
