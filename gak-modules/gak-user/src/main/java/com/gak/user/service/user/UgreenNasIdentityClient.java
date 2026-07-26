package com.gak.user.service.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.gak.framework.exception.BusinessException;
import com.gak.user.config.NasSsoProperties;
import java.net.URI;
import java.util.List;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 通过 UGOS 本机接口校验 NAS 会话并读取当前用户。
 */
@Component
public class UgreenNasIdentityClient implements NasIdentityClient {

    private static final String TOKEN_HEADER = "X-Ugreen-Token";
    private static final String TOKEN_WHERE_HEADER = "header";
    private static final String VERIFY_PATH = "/ugreen/v1/verify/is_login";
    private static final String CURRENT_USER_PATH = "/ugreen/v1/user/current/user";
    private static final String LEGACY_USER_INFO_PATH = "/ugreen/v1/user/userInfo";
    private static final String API_VERSION_HEADER = "Api-Version";
    private static final String API_VERSION = "2";
    private static final String SUCCESS_CODE = "200";

    private final NasSsoProperties properties;
    private final RestClient restClient;

    public UgreenNasIdentityClient(NasSsoProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public NasIdentity verify(String nasToken, String tokenWhere) {
        if (!properties.isEnabled()) {
            throw new BusinessException("NAS_SSO_DISABLED", "NAS 单点登录未启用");
        }
        if (!StringUtils.hasText(nasToken)) {
            throw new BusinessException("NAS_SSO_TOKEN_INVALID", "NAS 登录状态无效");
        }

        try {
            JsonNode loginStatus = executeGet(VERIFY_PATH, nasToken, tokenWhere);
            requireSuccess(loginStatus);
            JsonNode userInfo = executeUserInfo(nasToken, tokenWhere);
            requireSuccess(userInfo);
            return parseIdentity(userInfo);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new BusinessException("NAS_SSO_UNAVAILABLE", "NAS 身份校验暂时不可用");
        }
    }

    private JsonNode executeGet(String path, String nasToken, String tokenWhere) {
        URI uri = buildUri(path, nasToken, tokenWhere);
        RestClient.RequestHeadersSpec<?> request = restClient.get().uri(uri);
        if (CURRENT_USER_PATH.equals(path)) {
            request.header(API_VERSION_HEADER, API_VERSION);
        }
        if (TOKEN_WHERE_HEADER.equals(tokenWhere)) {
            request.header(TOKEN_HEADER, nasToken);
        }
        JsonNode response = request.retrieve().body(JsonNode.class);
        if (response == null) {
            throw new BusinessException("NAS_SSO_RESPONSE_INVALID", "NAS 身份校验响应无效");
        }
        return response;
    }

    /**
     * 当前固件使用 current/user，保留 userInfo 作为旧固件兼容回退。
     */
    private JsonNode executeUserInfo(String nasToken, String tokenWhere) {
        try {
            JsonNode currentUser = executeGet(CURRENT_USER_PATH, nasToken, tokenWhere);
            requireSuccess(currentUser);
            return currentUser;
        } catch (BusinessException | RestClientException currentUserException) {
            JsonNode legacyUser = executeGet(LEGACY_USER_INFO_PATH, nasToken, tokenWhere);
            requireSuccess(legacyUser);
            return legacyUser;
        }
    }

    private URI buildUri(String path, String nasToken, String tokenWhere) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(properties.getBaseUrl())
                .path(path);
        if (!TOKEN_WHERE_HEADER.equals(tokenWhere)) {
            builder.queryParam("token", nasToken);
        }
        return builder.build().encode().toUri();
    }

    private void requireSuccess(JsonNode response) {
        String code = response.path("code").asText();
        if (!SUCCESS_CODE.equals(code)) {
            throw new BusinessException("NAS_SSO_TOKEN_INVALID", "NAS 登录状态无效或已过期");
        }
    }

    private NasIdentity parseIdentity(JsonNode response) {
        JsonNode dataNode = response.path("data");
        String username = firstText(dataNode, List.of(
                "/username",
                "/user/username",
                "/result/username",
                "/result/user/username"
        ));
        if (!StringUtils.hasText(username)) {
            throw new BusinessException("NAS_SSO_USER_INVALID", "未能识别 NAS 当前用户");
        }
        String userId = firstText(dataNode, List.of(
                "/uid",
                "/user_id",
                "/user/uid",
                "/result/uid",
                "/result/user/uid"
        ));
        String userType = firstText(dataNode, List.of(
                "/role",
                "/user_type",
                "/user/role",
                "/result/role",
                "/result/user/role"
        ));
        return new NasIdentity(userId, username.trim(), userType);
    }

    private String firstText(JsonNode root, List<String> pointers) {
        for (String pointer : pointers) {
            JsonNode value = root.at(pointer);
            if (!value.isMissingNode() && !value.isNull() && StringUtils.hasText(value.asText())) {
                return value.asText();
            }
        }
        return null;
    }
}
