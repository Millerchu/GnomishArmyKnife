package com.gak.travelrecord.service;

import com.gak.travelrecord.mapper.TravelRecordMapper;
import com.gak.user.service.user.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 应用权限与记录归属分别校验，直接访问 API 不能绕过应用授权。 */
@Service
public class TravelAccessService {
    private final TokenService tokens;
    private final TravelRecordMapper mapper;
    public TravelAccessService(TokenService tokens, TravelRecordMapper mapper) { this.tokens = tokens; this.mapper = mapper; }
    public Long requireCurrentUserId(HttpServletRequest request) {
        Long owner = tokens.requireCurrentUserId(request);
        requireApp(owner, "APP_TRAVEL_RECORD");
        return owner;
    }
    public void requireBillAccess(Long owner) { requireApp(owner, "APP_PERSONAL_BILLS"); }
    private void requireApp(Long owner, String app) {
        if (!mapper.hasAppAccess(owner, app)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前用户未获应用授权：" + app);
        }
    }
}
