package com.gak.growthrecord.service;

import com.gak.growthrecord.mapper.GrowthChildMapper;
import com.gak.user.service.user.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 服务端验证登录和应用授权，防止绕过桌面直接访问 API。 */
@Service
public class GrowthAccessService {
    public static final String APP_CODE = "APP_GROWTH_RECORD";
    private final TokenService tokens;
    private final GrowthChildMapper children;

    public GrowthAccessService(TokenService tokens, GrowthChildMapper children) {
        this.tokens = tokens;
        this.children = children;
    }

    public Long requireCurrentUserId(HttpServletRequest request) {
        Long owner = tokens.requireCurrentUserId(request);
        if (!children.hasAppAccess(owner, APP_CODE)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前用户未获芽记应用授权");
        }
        return owner;
    }
}


