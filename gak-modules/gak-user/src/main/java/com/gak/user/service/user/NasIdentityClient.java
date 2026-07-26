package com.gak.user.service.user;

/**
 * NAS 身份校验客户端。
 */
public interface NasIdentityClient {

    NasIdentity verify(String nasToken, String tokenWhere);
}
