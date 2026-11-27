package com.gak.gakstart.config;

import com.gak.framework.security.AppPermissionCheck;
import com.gak.framework.security.PublicEndpoint;
import com.gak.framework.security.RequireAppPermission;
import com.gak.user.controller.user.AuthController;
import com.gak.user.service.user.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

/** 项目 Controller 默认要求有效会话，应用入口进一步检查实时授权。 */
@Component
public class EndpointAccessInterceptor implements HandlerInterceptor {
    private final TokenService tokens;
    private final AppPermissionCheck permissions;

    public EndpointAccessInterceptor(TokenService tokens, AppPermissionCheck permissions) {
        this.tokens = tokens;
        this.permissions = permissions;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method) || !method.getBeanType().getPackageName().startsWith("com.gak.")) {
            return true;
        }
        if (method.hasMethodAnnotation(PublicEndpoint.class)) {
            return true;
        }
        // 改密入口由控制器验证普通或受限会话。
        if (method.getBeanType() == AuthController.class && method.getMethod().getName().equals("changePassword")) {
            return true;
        }
        Long userId = tokens.authenticateRequest(request);
        RequireAppPermission permission = method.getMethodAnnotation(RequireAppPermission.class);
        if (permission == null) {
            permission = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequireAppPermission.class);
        }
        if (permission != null && !permissions.isAllowed(userId, permission.value())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该应用");
        }
        return true;
    }
}
