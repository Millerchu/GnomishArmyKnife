package com.gak.wowcharacter.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.framework.exception.BusinessException;
import com.gak.user.constant.UserSecurityConstants;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import com.gak.wowcharacter.constant.WowSeasonConstants;
import com.gak.wowcharacter.domain.WowSpecializationGuide;
import com.gak.wowcharacter.dto.SaveWowSpecializationGuideRequest;
import com.gak.wowcharacter.mapper.WowSpecializationGuideMapper;
import com.gak.wowcharacter.vo.WowSpecializationGuideVO;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * WoW 赛季职业专精指南服务。
 */
@Service
public class WowSpecializationGuideService {

    private final WowSpecializationGuideMapper wowSpecializationGuideMapper;
    private final UserMapper userMapper;

    public WowSpecializationGuideService(WowSpecializationGuideMapper wowSpecializationGuideMapper,
                                         UserMapper userMapper) {
        this.wowSpecializationGuideMapper = wowSpecializationGuideMapper;
        this.userMapper = userMapper;
    }

    public List<WowSpecializationGuideVO> listCurrentSeasonGuides() {
        QueryWrapper<WowSpecializationGuide> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("season_code", WowSeasonConstants.CURRENT_SEASON_CODE)
                .orderByAsc("sort_no", "id");
        return wowSpecializationGuideMapper.selectList(queryWrapper).stream()
                .map(this::toVO)
                .toList();
    }

    @Transactional
    public WowSpecializationGuideVO update(Long currentUserId, Long id, SaveWowSpecializationGuideRequest request) {
        requireAdminOperator(currentUserId);
        WowSpecializationGuide guide = wowSpecializationGuideMapper.selectById(id);
        if (guide == null || !WowSeasonConstants.CURRENT_SEASON_CODE.equals(guide.getSeasonCode())) {
            throw new BusinessException("WOW_SPECIALIZATION_GUIDE_NOT_FOUND", "当前赛季职业指南不存在");
        }
        guide.setMythicTalentBuildName(request.getMythicTalentBuildName().trim());
        guide.setMythicTalentSummary(normalizeText(request.getMythicTalentSummary()));
        guide.setMythicTalentImportCode(normalizeText(request.getMythicTalentImportCode()));
        guide.setRaidTalentBuildName(request.getRaidTalentBuildName().trim());
        guide.setRaidTalentSummary(normalizeText(request.getRaidTalentSummary()));
        guide.setRaidTalentImportCode(normalizeText(request.getRaidTalentImportCode()));
        guide.setStatPriority(request.getStatPriority().trim());
        guide.setRotationNotes(normalizeText(request.getRotationNotes()));
        guide.setTrinketRanking(normalizeText(request.getTrinketRanking()));
        guide.setSourceName(request.getSourceName().trim());
        guide.setSourceUrl(request.getSourceUrl().trim());
        guide.setSourceUpdatedAt(request.getSourceUpdatedAt());
        guide.setUpdatedAt(LocalDateTime.now());
        wowSpecializationGuideMapper.updateById(guide);
        return toVO(guide);
    }

    private void requireAdminOperator(Long currentUserId) {
        User operator = currentUserId == null ? null : userMapper.selectById(currentUserId);
        if (operator == null
                || !Boolean.TRUE.equals(operator.getEnabled())
                || !UserSecurityConstants.ENABLED_STATUS.equalsIgnoreCase(operator.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户已失效，请重新登录");
        }
        String roleCode = StringUtils.hasText(operator.getRoleCode())
                ? operator.getRoleCode()
                : UserSecurityConstants.DEFAULT_ROLE_CODE;
        if (!UserSecurityConstants.ADMIN_ROLE_CODE.equalsIgnoreCase(roleCode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可修改职业专精指南");
        }
    }

    private String normalizeText(String value) {
        return value == null ? null : value.trim();
    }

    private WowSpecializationGuideVO toVO(WowSpecializationGuide guide) {
        WowSpecializationGuideVO vo = new WowSpecializationGuideVO();
        vo.setId(guide.getId());
        vo.setSeasonCode(guide.getSeasonCode());
        vo.setClassCode(guide.getClassCode());
        vo.setClassName(guide.getClassName());
        vo.setSpecCode(guide.getSpecCode());
        vo.setSpecName(guide.getSpecName());
        vo.setRoleType(guide.getRoleType());
        vo.setMythicTalentBuildName(guide.getMythicTalentBuildName());
        vo.setMythicTalentSummary(guide.getMythicTalentSummary());
        vo.setMythicTalentImportCode(guide.getMythicTalentImportCode());
        vo.setRaidTalentBuildName(guide.getRaidTalentBuildName());
        vo.setRaidTalentSummary(guide.getRaidTalentSummary());
        vo.setRaidTalentImportCode(guide.getRaidTalentImportCode());
        vo.setStatPriority(guide.getStatPriority());
        vo.setRotationNotes(guide.getRotationNotes());
        vo.setTrinketRanking(guide.getTrinketRanking());
        vo.setSourceName(guide.getSourceName());
        vo.setSourceUrl(guide.getSourceUrl());
        vo.setSourceUpdatedAt(guide.getSourceUpdatedAt());
        vo.setUpdatedAt(guide.getUpdatedAt());
        return vo;
    }
}
