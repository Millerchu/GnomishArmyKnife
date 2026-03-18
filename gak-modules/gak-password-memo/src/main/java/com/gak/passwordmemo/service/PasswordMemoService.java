package com.gak.passwordmemo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.framework.exception.BusinessException;
import com.gak.framework.response.PagedResult;
import com.gak.passwordmemo.domain.PasswordMemo;
import com.gak.passwordmemo.dto.PasswordMemoQueryRequest;
import com.gak.passwordmemo.dto.SavePasswordMemoRequest;
import com.gak.passwordmemo.dto.VerifyAccessRequest;
import com.gak.passwordmemo.mapper.PasswordMemoMapper;
import com.gak.passwordmemo.service.PasswordMemoCryptoService.EncryptedPayload;
import com.gak.passwordmemo.vo.PasswordMemoDetailVO;
import com.gak.passwordmemo.vo.PasswordMemoListItemVO;
import com.gak.passwordmemo.vo.VerifyAccessResponse;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * 密码备忘录服务。
 */
@Service
public class PasswordMemoService {

    private static final Logger log = LoggerFactory.getLogger(PasswordMemoService.class);
    private static final String MASKED_PASSWORD = "********";

    private final PasswordMemoMapper passwordMemoMapper;
    private final PasswordMemoCryptoService passwordMemoCryptoService;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public PasswordMemoService(PasswordMemoMapper passwordMemoMapper,
                               PasswordMemoCryptoService passwordMemoCryptoService,
                               UserMapper userMapper,
                               PasswordEncoder passwordEncoder) {
        this.passwordMemoMapper = passwordMemoMapper;
        this.passwordMemoCryptoService = passwordMemoCryptoService;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public PagedResult<PasswordMemoListItemVO> page(Long currentUserId, PasswordMemoQueryRequest request) {
        ensureCurrentUserExists(currentUserId);

        QueryWrapper<PasswordMemo> wrapper = new QueryWrapper<>();
        wrapper.eq("owner_user_id", currentUserId);
        String keyword = trimToNull(request.getKeyword());
        if (StringUtils.hasText(keyword)) {
            wrapper.and(query -> query.like("site_name", keyword)
                    .or()
                    .like("site_url", keyword)
                    .or()
                    .like("username", keyword)
                    .or()
                    .like("registered_phone", keyword)
                    .or()
                    .like("registered_email", keyword));
        }
        wrapper.orderByDesc("updated_at").orderByDesc("id");

        List<PasswordMemo> records = passwordMemoMapper.selectList(wrapper);
        long total = records.size();
        long fromIndex = Math.max((request.getPageNo() - 1) * request.getPageSize(), 0L);
        long toIndex = Math.min(fromIndex + request.getPageSize(), total);
        if (fromIndex >= total) {
            return new PagedResult<>(Collections.emptyList(), total);
        }

        List<PasswordMemoListItemVO> list = new ArrayList<>();
        for (PasswordMemo record : records.subList((int) fromIndex, (int) toIndex)) {
            list.add(toListItem(record));
        }
        return new PagedResult<>(list, total);
    }

    @Transactional
    public PasswordMemoDetailVO create(Long currentUserId, SavePasswordMemoRequest request) {
        ensureCurrentUserExists(currentUserId);

        EncryptedPayload encryptedPayload = passwordMemoCryptoService.encrypt(request.getPassword().trim());
        LocalDateTime now = LocalDateTime.now();

        PasswordMemo passwordMemo = new PasswordMemo();
        passwordMemo.setOwnerUserId(currentUserId);
        passwordMemo.setSiteName(request.getSiteName().trim());
        passwordMemo.setSiteUrl(request.getSiteUrl().trim());
        passwordMemo.setUsername(trimToNull(request.getUsername()));
        passwordMemo.setPasswordCiphertext(encryptedPayload.ciphertext());
        passwordMemo.setPasswordNonce(encryptedPayload.nonce());
        passwordMemo.setRegisteredPhone(trimToNull(request.getRegisteredPhone()));
        passwordMemo.setRegisteredEmail(trimToNull(request.getRegisteredEmail()));
        passwordMemo.setRemark(trimToNull(request.getRemark()));
        passwordMemo.setCreatedAt(now);
        passwordMemo.setUpdatedAt(now);
        passwordMemoMapper.insert(passwordMemo);
        return toDetail(passwordMemo);
    }

    @Transactional
    public PasswordMemoDetailVO update(Long currentUserId, Long id, SavePasswordMemoRequest request) {
        ensureCurrentUserExists(currentUserId);

        PasswordMemo current = getOwnedMemoOrThrow(currentUserId, id);
        EncryptedPayload encryptedPayload = passwordMemoCryptoService.encrypt(request.getPassword().trim());

        current.setSiteName(request.getSiteName().trim());
        current.setSiteUrl(request.getSiteUrl().trim());
        current.setUsername(trimToNull(request.getUsername()));
        current.setPasswordCiphertext(encryptedPayload.ciphertext());
        current.setPasswordNonce(encryptedPayload.nonce());
        current.setRegisteredPhone(trimToNull(request.getRegisteredPhone()));
        current.setRegisteredEmail(trimToNull(request.getRegisteredEmail()));
        current.setRemark(trimToNull(request.getRemark()));
        current.setUpdatedAt(LocalDateTime.now());
        passwordMemoMapper.updateById(current);
        return toDetail(current);
    }

    @Transactional
    public void delete(Long currentUserId, Long id) {
        ensureCurrentUserExists(currentUserId);
        PasswordMemo current = getOwnedMemoOrThrow(currentUserId, id);
        passwordMemoMapper.deleteById(current.getId());
    }

    public PasswordMemoDetailVO get(Long currentUserId, Long id) {
        ensureCurrentUserExists(currentUserId);
        return toDetail(getOwnedMemoOrThrow(currentUserId, id));
    }

    public VerifyAccessResponse verifyAccess(Long currentUserId, Long id, VerifyAccessRequest request, String ipAddress) {
        User currentUser = ensureCurrentUserExists(currentUserId);
        PasswordMemo memo = getOwnedMemoOrThrow(currentUserId, id);
        if (!passwordEncoder.matches(request.getLoginPassword(), currentUser.getPasswordHash())) {
            throw new BusinessException("LOGIN_PASSWORD_INVALID", "当前用户密码错误");
        }
        String password = passwordMemoCryptoService.decrypt(memo.getPasswordCiphertext(), memo.getPasswordNonce());
        log.info("password memo revealed, userId={}, memoId={}, ip={}", currentUserId, id, ipAddress);
        return new VerifyAccessResponse(password);
    }

    private User ensureCurrentUserExists(Long currentUserId) {
        User user = userMapper.selectById(currentUserId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
        }
        return user;
    }

    private PasswordMemo getOwnedMemoOrThrow(Long currentUserId, Long id) {
        QueryWrapper<PasswordMemo> wrapper = new QueryWrapper<>();
        wrapper.eq("id", id).eq("owner_user_id", currentUserId);
        PasswordMemo memo = passwordMemoMapper.selectOne(wrapper);
        if (memo == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "密码备忘录不存在");
        }
        return memo;
    }

    private PasswordMemoListItemVO toListItem(PasswordMemo memo) {
        PasswordMemoListItemVO vo = new PasswordMemoListItemVO();
        vo.setId(memo.getId());
        vo.setSiteName(memo.getSiteName());
        vo.setSiteUrl(memo.getSiteUrl());
        vo.setUsername(memo.getUsername());
        vo.setRegisteredPhone(memo.getRegisteredPhone());
        vo.setRegisteredEmail(memo.getRegisteredEmail());
        vo.setUpdatedAt(memo.getUpdatedAt());
        return vo;
    }

    private PasswordMemoDetailVO toDetail(PasswordMemo memo) {
        PasswordMemoDetailVO vo = new PasswordMemoDetailVO();
        vo.setId(memo.getId());
        vo.setSiteName(memo.getSiteName());
        vo.setSiteUrl(memo.getSiteUrl());
        vo.setUsername(memo.getUsername());
        vo.setRegisteredPhone(memo.getRegisteredPhone());
        vo.setRegisteredEmail(memo.getRegisteredEmail());
        vo.setRemark(memo.getRemark());
        vo.setMaskedPassword(MASKED_PASSWORD);
        vo.setCreatedAt(memo.getCreatedAt());
        vo.setUpdatedAt(memo.getUpdatedAt());
        return vo;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
