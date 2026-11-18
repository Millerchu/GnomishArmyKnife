package com.gak.aisubscription.service;
import com.gak.aisubscription.mapper.AiAccessMapper;
import com.gak.user.service.user.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
/** 每次 API 请求校验功能授权，写操作以用户行锁串行化去重与退款。 */
@Service
public class AiAccessService {
 public static final String APP = "APP_AI_SUBSCRIPTION";
 private final AiAccessMapper mapper;
 private final TokenService tokens;
 public AiAccessService(AiAccessMapper mapper, TokenService tokens) {this.mapper=mapper;this.tokens=tokens;}
 public Long requireCurrentUserId(HttpServletRequest request) {Long owner=tokens.requireCurrentUserId(request);requireApp(owner,APP);return owner;}
 public void requireApp(Long owner,String app) {if(!mapper.hasAppAccess(owner,app)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"未获得应用权限");}
 public boolean hasApp(Long owner,String app) {return mapper.hasAppAccess(owner,app);}
 public void lockOwner(Long owner) {requireApp(owner,APP);if(mapper.lockOwner(owner)==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"用户不可用");}
}
