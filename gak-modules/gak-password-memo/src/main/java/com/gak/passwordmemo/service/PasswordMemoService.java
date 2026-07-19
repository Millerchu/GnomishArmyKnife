package com.gak.passwordmemo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.framework.exception.BusinessException;
import com.gak.framework.response.PagedResult;
import com.gak.passwordmemo.domain.PasswordMemo;
import com.gak.passwordmemo.domain.PasswordMemoHistory;
import com.gak.passwordmemo.dto.CreatePasswordHistoryRequest;
import com.gak.passwordmemo.dto.PasswordMemoQueryRequest;
import com.gak.passwordmemo.dto.SavePasswordMemoRequest;
import com.gak.passwordmemo.dto.UpdateMemoPasswordRequest;
import com.gak.passwordmemo.dto.UpdatePasswordHistoryRequest;
import com.gak.passwordmemo.dto.UpdatePasswordMemoInfoRequest;
import com.gak.passwordmemo.dto.VerifyAccessRequest;
import com.gak.passwordmemo.mapper.PasswordMemoHistoryMapper;
import com.gak.passwordmemo.mapper.PasswordMemoMapper;
import com.gak.passwordmemo.service.PasswordMemoCryptoService.EncryptedPayload;
import com.gak.passwordmemo.vo.PasswordMemoDetailVO;
import com.gak.passwordmemo.vo.PasswordMemoHistoryVO;
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
    private final PasswordMemoHistoryMapper passwordMemoHistoryMapper;
    private final PasswordMemoCryptoService passwordMemoCryptoService;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public PasswordMemoService(PasswordMemoMapper passwordMemoMapper,
                               PasswordMemoHistoryMapper passwordMemoHistoryMapper,
                               PasswordMemoCryptoService passwordMemoCryptoService,
                               UserMapper userMapper,
                               PasswordEncoder passwordEncoder) {
        this.passwordMemoMapper = passwordMemoMapper;
        this.passwordMemoHistoryMapper = passwordMemoHistoryMapper;
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
        passwordMemo.setPasswordStartedAt(now);
        passwordMemo.setRegisteredPhone(trimToNull(request.getRegisteredPhone()));
        passwordMemo.setRegisteredEmail(trimToNull(request.getRegisteredEmail()));
        passwordMemo.setRemark(trimToNull(request.getRemark()));
        passwordMemo.setCreatedAt(now);
        passwordMemo.setUpdatedAt(now);
        passwordMemoMapper.insert(passwordMemo);
        return toDetail(passwordMemo);
    }

    @Transactional
    public PasswordMemoDetailVO update(Long currentUserId, Long id, UpdatePasswordMemoInfoRequest request) {
        ensureCurrentUserExists(currentUserId);

        PasswordMemo current = getOwnedMemoOrThrow(currentUserId, id);

        current.setSiteName(request.getSiteName().trim());
        current.setSiteUrl(request.getSiteUrl().trim());
        current.setUsername(trimToNull(request.getUsername()));
        current.setRegisteredPhone(trimToNull(request.getRegisteredPhone()));
        current.setRegisteredEmail(trimToNull(request.getRegisteredEmail()));
        current.setRemark(trimToNull(request.getRemark()));
        current.setUpdatedAt(LocalDateTime.now());
        passwordMemoMapper.updateById(current);
        return toDetail(current);
    }

    /**
     * 归档当前密码并启用新密码，两个写操作必须处于同一事务。
     */
    @Transactional
    public PasswordMemoDetailVO updatePassword(Long currentUserId, Long id, UpdateMemoPasswordRequest request) {
        ensureCurrentUserExists(currentUserId);
        PasswordMemo current = getOwnedMemoOrThrow(currentUserId, id);
        String newPassword = request.getNewPassword().trim();
        String currentPassword = passwordMemoCryptoService.decrypt(
                current.getPasswordCiphertext(), current.getPasswordNonce());
        if (currentPassword.equals(newPassword)) {
            throw new BusinessException("PASSWORD_MEMO_PASSWORD_UNCHANGED", "新密码不能与当前密码相同");
        }

        LocalDateTime now = LocalDateTime.now();
        archiveCurrentPassword(currentUserId, current, now);
        EncryptedPayload encryptedPayload = passwordMemoCryptoService.encrypt(newPassword);
        current.setPasswordCiphertext(encryptedPayload.ciphertext());
        current.setPasswordNonce(encryptedPayload.nonce());
        current.setPasswordStartedAt(now);
        current.setUpdatedAt(now);
        passwordMemoMapper.updateById(current);
        return toDetail(current);
    }

    /**
     * 手工补录历史密码，不影响当前正在使用的密码。
     */
    @Transactional
    public PasswordMemoDetailVO createPasswordHistory(Long currentUserId,
                                                      Long memoId,
                                                      CreatePasswordHistoryRequest request) {
        ensureCurrentUserExists(currentUserId);
        PasswordMemo memo = getOwnedMemoOrThrow(currentUserId, memoId);
        validateUsagePeriod(request.getUsageStartedAt(), request.getUsageEndedAt());
        EncryptedPayload encryptedPayload = passwordMemoCryptoService.encrypt(request.getPassword().trim());

        PasswordMemoHistory history = new PasswordMemoHistory();
        history.setMemoId(memoId);
        history.setOwnerUserId(currentUserId);
        history.setPasswordCiphertext(encryptedPayload.ciphertext());
        history.setPasswordNonce(encryptedPayload.nonce());
        history.setUsageStartedAt(request.getUsageStartedAt());
        history.setUsageEndedAt(request.getUsageEndedAt());
        history.setCreatedAt(LocalDateTime.now());
        passwordMemoHistoryMapper.insert(history);
        return toDetail(memo);
    }

    /**
     * 编辑历史记录时，空密码表示只调整使用周期。
     */
    @Transactional
    public PasswordMemoDetailVO updatePasswordHistory(Long currentUserId,
                                                      Long memoId,
                                                      Long historyId,
                                                      UpdatePasswordHistoryRequest request) {
        ensureCurrentUserExists(currentUserId);
        PasswordMemo memo = getOwnedMemoOrThrow(currentUserId, memoId);
        PasswordMemoHistory history = getOwnedHistoryOrThrow(currentUserId, memoId, historyId);
        validateUsagePeriod(request.getUsageStartedAt(), request.getUsageEndedAt());
        if (StringUtils.hasText(request.getPassword())) {
            EncryptedPayload encryptedPayload = passwordMemoCryptoService.encrypt(request.getPassword().trim());
            history.setPasswordCiphertext(encryptedPayload.ciphertext());
            history.setPasswordNonce(encryptedPayload.nonce());
        }
        history.setUsageStartedAt(request.getUsageStartedAt());
        history.setUsageEndedAt(request.getUsageEndedAt());
        passwordMemoHistoryMapper.updateById(history);
        return toDetail(memo);
    }

    @Transactional
    public PasswordMemoDetailVO deletePasswordHistory(Long currentUserId, Long memoId, Long historyId) {
        ensureCurrentUserExists(currentUserId);
        PasswordMemo memo = getOwnedMemoOrThrow(currentUserId, memoId);
        PasswordMemoHistory history = getOwnedHistoryOrThrow(currentUserId, memoId, historyId);
        passwordMemoHistoryMapper.deleteById(history.getId());
        return toDetail(memo);
    }

    @Transactional
    public void delete(Long currentUserId, Long id) {
        ensureCurrentUserExists(currentUserId);
        PasswordMemo current = getOwnedMemoOrThrow(currentUserId, id);
        QueryWrapper<PasswordMemoHistory> historyWrapper = new QueryWrapper<>();
        historyWrapper.eq("memo_id", current.getId()).eq("owner_user_id", currentUserId);
        passwordMemoHistoryMapper.delete(historyWrapper);
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
        return new VerifyAccessResponse(password, maskFirstCharacter(password), listPasswordHistory(memo, true));
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

    private PasswordMemoHistory getOwnedHistoryOrThrow(Long currentUserId, Long memoId, Long historyId) {
        QueryWrapper<PasswordMemoHistory> wrapper = new QueryWrapper<>();
        wrapper.eq("id", historyId)
                .eq("memo_id", memoId)
                .eq("owner_user_id", currentUserId);
        PasswordMemoHistory history = passwordMemoHistoryMapper.selectOne(wrapper);
        if (history == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "历史密码记录不存在");
        }
        return history;
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
        vo.setPasswordHistory(listPasswordHistory(memo, false));
        vo.setCreatedAt(memo.getCreatedAt());
        vo.setUpdatedAt(memo.getUpdatedAt());
        return vo;
    }

    /**
     * 历史密码默认保持全掩码，避免未完成二次校验时泄露密码特征。
     */
    private List<PasswordMemoHistoryVO> listPasswordHistory(PasswordMemo memo, boolean showFirstCharacter) {
        if (memo.getId() == null) {
            return Collections.emptyList();
        }
        QueryWrapper<PasswordMemoHistory> wrapper = new QueryWrapper<>();
        wrapper.eq("memo_id", memo.getId())
                .eq("owner_user_id", memo.getOwnerUserId())
                .orderByDesc("usage_started_at")
                .orderByDesc("id");
        List<PasswordMemoHistoryVO> historyItems = new ArrayList<>();
        List<PasswordMemoHistory> histories = passwordMemoHistoryMapper.selectList(wrapper);
        if (histories == null) {
            return historyItems;
        }
        for (PasswordMemoHistory history : histories) {
            PasswordMemoHistoryVO historyVO = new PasswordMemoHistoryVO();
            historyVO.setId(history.getId());
            if (showFirstCharacter) {
                historyVO.setMaskedPassword(maskHistoryFirstCharacter(history));
            } else {
                historyVO.setMaskedPassword(MASKED_PASSWORD);
            }
            historyVO.setUsageStartedAt(history.getUsageStartedAt());
            historyVO.setUsageEndedAt(history.getUsageEndedAt());
            historyItems.add(historyVO);
        }
        return historyItems;
    }

    private String maskHistoryFirstCharacter(PasswordMemoHistory history) {
        try {
            String historyPassword = passwordMemoCryptoService.decrypt(
                    history.getPasswordCiphertext(), history.getPasswordNonce());
            return maskFirstCharacter(historyPassword);
        } catch (RuntimeException exception) {
            log.warn("failed to mask password memo history, historyId={}", history.getId(), exception);
            return MASKED_PASSWORD;
        }
    }

    private void archiveCurrentPassword(Long currentUserId, PasswordMemo memo, LocalDateTime endedAt) {
        PasswordMemoHistory history = new PasswordMemoHistory();
        history.setMemoId(memo.getId());
        history.setOwnerUserId(currentUserId);
        history.setPasswordCiphertext(memo.getPasswordCiphertext());
        history.setPasswordNonce(memo.getPasswordNonce());
        history.setUsageStartedAt(memo.getPasswordStartedAt() == null ? memo.getCreatedAt() : memo.getPasswordStartedAt());
        history.setUsageEndedAt(endedAt);
        history.setCreatedAt(endedAt);
        passwordMemoHistoryMapper.insert(history);
    }

    private String maskFirstCharacter(String password) {
        if (!StringUtils.hasText(password)) {
            return MASKED_PASSWORD;
        }
        return password.substring(0, 1) + "*".repeat(Math.max(password.length() - 1, 1));
    }

    private void validateUsagePeriod(LocalDateTime usageStartedAt, LocalDateTime usageEndedAt) {
        if (!usageStartedAt.isBefore(usageEndedAt)) {
            throw new BusinessException("PASSWORD_HISTORY_PERIOD_INVALID", "使用结束时间必须晚于起始时间");
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
