package com.gak.healthrecord.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.attachment.constant.AttachmentConstants;
import com.gak.attachment.service.AttachmentService;
import com.gak.framework.exception.BusinessException;
import com.gak.healthrecord.domain.HealthRecord;
import com.gak.healthrecord.domain.HealthReport;
import com.gak.healthrecord.domain.HealthReportDetail;
import com.gak.healthrecord.domain.HealthReportDetailItem;
import com.gak.healthrecord.dto.HealthReportDetailItemRequest;
import com.gak.healthrecord.dto.SaveHealthReportDetailsRequest;
import com.gak.healthrecord.enums.HealthReportAbnormalFlag;
import com.gak.healthrecord.enums.HealthReportItemGroup;
import com.gak.healthrecord.enums.HealthReportResultType;
import com.gak.healthrecord.mapper.HealthRecordMapper;
import com.gak.healthrecord.mapper.HealthReportDetailMapper;
import com.gak.healthrecord.mapper.HealthReportMapper;
import com.gak.healthrecord.vo.HealthReportDetailItemVO;
import com.gak.healthrecord.vo.HealthReportDetailsVO;
import com.gak.healthrecord.vo.HealthReportVO;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * 体检报告明细服务，负责明细读取、整组替换、乐观锁版本控制与核心指标一对一关联。
 */
@Service
public class HealthReportDetailService {

    public static final int MAX_ITEMS = 200;
    private static final int MAX_NAME_LENGTH = 80;
    private static final int MAX_QUALITATIVE_LENGTH = 80;
    private static final int MAX_TEXT_LENGTH = 1000;
    private static final int MAX_UNIT_LENGTH = 24;
    private static final int MAX_REFERENCE_LENGTH = 160;
    private static final int MAX_SOURCE_FILE_LABEL_LENGTH = 255;
    private static final int MIN_SOURCE_PAGE = 1;
    private static final int MAX_SOURCE_PAGE = 9999;
    private static final Pattern NUMBER_RESULT_PATTERN = Pattern.compile("^-?\\d{1,10}(?:\\.\\d{1,8})?$");

    private final HealthReportMapper healthReportMapper;
    private final HealthRecordMapper healthRecordMapper;
    private final HealthReportDetailMapper healthReportDetailMapper;
    private final UserMapper userMapper;
    private final AttachmentService attachmentService;

    public HealthReportDetailService(HealthReportMapper healthReportMapper,
                                     HealthRecordMapper healthRecordMapper,
                                     HealthReportDetailMapper healthReportDetailMapper,
                                     UserMapper userMapper,
                                     AttachmentService attachmentService) {
        this.healthReportMapper = healthReportMapper;
        this.healthRecordMapper = healthRecordMapper;
        this.healthReportDetailMapper = healthReportDetailMapper;
        this.userMapper = userMapper;
        this.attachmentService = attachmentService;
    }

    public HealthReportDetailsVO getDetails(Long currentUserId, Long reportId) {
        ensureCurrentUserExists(currentUserId);
        HealthReport report = getOwnedReportOrThrow(currentUserId, reportId);
        HealthReportDetail detail = healthReportDetailMapper.findByReportId(report.getId());
        return buildDetailsVO(report, detail);
    }

    @Transactional
    public HealthReportDetailsVO saveDetails(Long currentUserId, Long reportId, SaveHealthReportDetailsRequest request) {
        ensureCurrentUserExists(currentUserId);
        validateRequestPresence(request);
        // 锁定当前用户的报告主行，串行化同一报告的并发明细保存。
        HealthReport report = getOwnedReportForUpdate(currentUserId, reportId);
        List<HealthReportDetailItem> normalizedItems = validateAndNormalizeRequests(request.getItems());

        HealthReportDetail existing = healthReportDetailMapper.findByReportId(report.getId());
        int currentRevision = existing == null ? 0 : existing.getRevision();
        if (!Integer.valueOf(currentRevision).equals(request.getExpectedRevision())) {
            throw new BusinessException("HEALTH_REPORT_DETAIL_CONFLICT", "报告明细已被更新，请重新加载后再保存");
        }

        Long linkedRecordId = request.getLinkedRecordId();
        if (linkedRecordId != null) {
            ensureOwnedRecordExists(currentUserId, linkedRecordId);
            HealthReportDetail linkedByOther = healthReportDetailMapper.findByRecordId(linkedRecordId);
            if (linkedByOther != null && !report.getId().equals(linkedByOther.getReportId())) {
                throw new BusinessException("HEALTH_RECORD_ALREADY_LINKED", "该核心指标记录已关联其他体检报告");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        HealthReportDetail nextDetail = new HealthReportDetail();
        nextDetail.setReportId(report.getId());
        nextDetail.setRecordId(linkedRecordId);
        nextDetail.setRevision(currentRevision + 1);
        nextDetail.setItems(normalizedItems);
        nextDetail.setUpdatedAt(now);

        try {
            if (existing == null) {
                healthReportDetailMapper.insertDetail(nextDetail);
            } else {
                int updated = healthReportDetailMapper.updateDetailWithRevision(nextDetail, currentRevision);
                if (updated == 0) {
                    throw new BusinessException("HEALTH_REPORT_DETAIL_CONFLICT", "报告明细已被更新，请重新加载后再保存");
                }
            }
        } catch (DataIntegrityViolationException exception) {
            if (linkedRecordId != null) {
                throw new BusinessException("HEALTH_RECORD_ALREADY_LINKED", "该核心指标记录已关联其他体检报告");
            }
            throw exception;
        }

        return buildDetailsVO(report, nextDetail);
    }

    public List<HealthReportDetailItem> validateAndNormalizeRequests(List<HealthReportDetailItemRequest> requests) {
        if (requests == null) {
            throw new BusinessException("HEALTH_REPORT_DETAIL_INVALID", "items 不能为空");
        }
        if (requests.size() > MAX_ITEMS) {
            throw new BusinessException("HEALTH_REPORT_DETAIL_INVALID", "每份报告最多保存 200 项明细");
        }
        List<HealthReportDetailItem> normalized = new ArrayList<>(requests.size());
        for (int index = 0; index < requests.size(); index++) {
            HealthReportDetailItemRequest item = requests.get(index);
            int rowNo = index + 1;
            if (item == null) {
                throw new BusinessException("HEALTH_REPORT_DETAIL_INVALID", "第 " + rowNo + " 项明细不能为空");
            }
            normalized.add(validateAndNormalizeSingle(
                    rowNo,
                    item.getGroup(),
                    item.getItemCode(),
                    item.getName(),
                    item.getResultType(),
                    item.getResultText(),
                    item.getUnit(),
                    item.getReferenceText(),
                    item.getAbnormalFlag(),
                    item.getSourceFileLabel(),
                    item.getSourcePage()
            ));
        }
        return normalized;
    }

    public List<HealthReportDetailItem> validateAndNormalizeStoredItems(List<HealthReportDetailItem> items) {
        if (items == null) {
            throw new BusinessException("HEALTH_REPORT_DETAIL_INVALID", "items 不能为空");
        }
        if (items.size() > MAX_ITEMS) {
            throw new BusinessException("HEALTH_REPORT_DETAIL_INVALID", "每份报告最多保存 200 项明细");
        }
        List<HealthReportDetailItem> normalized = new ArrayList<>(items.size());
        for (int index = 0; index < items.size(); index++) {
            HealthReportDetailItem item = items.get(index);
            int rowNo = index + 1;
            if (item == null) {
                throw new BusinessException("HEALTH_REPORT_DETAIL_INVALID", "第 " + rowNo + " 项明细不能为空");
            }
            normalized.add(validateAndNormalizeSingle(
                    rowNo,
                    item.getGroup(),
                    item.getItemCode(),
                    item.getName(),
                    item.getResultType(),
                    item.getResultText(),
                    item.getUnit(),
                    item.getReferenceText(),
                    item.getAbnormalFlag(),
                    item.getSourceFileLabel(),
                    item.getSourcePage()
            ));
        }
        return normalized;
    }

    private void validateRequestPresence(SaveHealthReportDetailsRequest request) {
        if (request == null) {
            throw new BusinessException("HEALTH_REPORT_DETAIL_INVALID", "请求体不能为空");
        }
        if (request.getExpectedRevision() == null || request.getExpectedRevision() < 0) {
            throw new BusinessException("HEALTH_REPORT_DETAIL_INVALID", "expectedRevision 必须是非负整数");
        }
        if (!request.isLinkedRecordIdPresent()) {
            throw new BusinessException("HEALTH_REPORT_DETAIL_INVALID", "linkedRecordId 键必传");
        }
        if (request.getItems() == null) {
            throw new BusinessException("HEALTH_REPORT_DETAIL_INVALID", "items 不能为空");
        }
    }

    private HealthReportDetailItem validateAndNormalizeSingle(int rowNo,
                                                              String rawGroup,
                                                              String rawItemCode,
                                                              String rawName,
                                                              String rawResultType,
                                                              String rawResultText,
                                                              String rawUnit,
                                                              String rawReferenceText,
                                                              String rawAbnormalFlag,
                                                              String rawSourceFileLabel,
                                                              Integer sourcePage) {
        HealthReportItemGroup group = HealthReportItemGroup.fromCode(rawGroup);
        if (group == null) {
            throw new BusinessException("HEALTH_REPORT_DETAIL_INVALID", "第 " + rowNo + " 项分组代码非法");
        }

        String itemCode = trimToNull(rawItemCode);
        if (itemCode != null) {
            HealthReportItemCatalog.CatalogItem catalogItem = HealthReportItemCatalog.findByCode(itemCode);
            if (catalogItem == null) {
                throw new BusinessException("HEALTH_REPORT_DETAIL_INVALID", "第 " + rowNo + " 项项目代码不存在");
            }
            if (catalogItem.group() != group) {
                throw new BusinessException("HEALTH_REPORT_DETAIL_INVALID", "第 " + rowNo + " 项项目代码与所选分组不匹配");
            }
        }

        String name = trimToNull(rawName);
        if (name == null || name.length() > MAX_NAME_LENGTH) {
            throw new BusinessException("HEALTH_REPORT_DETAIL_INVALID", "第 " + rowNo + " 项名称需为 1–80 字符");
        }

        HealthReportResultType resultType = HealthReportResultType.fromCode(rawResultType);
        if (resultType == null) {
            throw new BusinessException("HEALTH_REPORT_DETAIL_INVALID", "第 " + rowNo + " 项结果类型非法");
        }

        String resultText = trimToNull(rawResultText);
        if (resultText == null) {
            throw new BusinessException("HEALTH_REPORT_DETAIL_INVALID", "第 " + rowNo + " 项原始结果不能为空");
        }
        validateResultTextByType(rowNo, resultType, resultText);

        String unit = trimToNull(rawUnit);
        if (unit != null && unit.length() > MAX_UNIT_LENGTH) {
            throw new BusinessException("HEALTH_REPORT_DETAIL_INVALID", "第 " + rowNo + " 项单位不能超过 24 字符");
        }

        String referenceText = trimToNull(rawReferenceText);
        if (referenceText != null && referenceText.length() > MAX_REFERENCE_LENGTH) {
            throw new BusinessException("HEALTH_REPORT_DETAIL_INVALID", "第 " + rowNo + " 项参考区间不能超过 160 字符");
        }

        HealthReportAbnormalFlag abnormalFlag;
        if (!StringUtils.hasText(rawAbnormalFlag)) {
            abnormalFlag = HealthReportAbnormalFlag.UNSPECIFIED;
        } else {
            abnormalFlag = HealthReportAbnormalFlag.fromCode(rawAbnormalFlag);
            if (abnormalFlag == null) {
                throw new BusinessException("HEALTH_REPORT_DETAIL_INVALID", "第 " + rowNo + " 项异常标记非法");
            }
        }

        String sourceFileLabel = trimToNull(rawSourceFileLabel);
        if (sourceFileLabel != null && sourceFileLabel.length() > MAX_SOURCE_FILE_LABEL_LENGTH) {
            throw new BusinessException("HEALTH_REPORT_DETAIL_INVALID", "第 " + rowNo + " 项来源文件名称不能超过 255 字符");
        }

        if (sourcePage != null && (sourcePage < MIN_SOURCE_PAGE || sourcePage > MAX_SOURCE_PAGE)) {
            throw new BusinessException("HEALTH_REPORT_DETAIL_INVALID", "第 " + rowNo + " 项来源页码需在 1–9999 之间");
        }

        HealthReportDetailItem normalized = new HealthReportDetailItem();
        normalized.setGroup(group.name());
        normalized.setItemCode(itemCode);
        normalized.setName(name);
        normalized.setResultType(resultType.name());
        normalized.setResultText(resultText);
        normalized.setUnit(unit);
        normalized.setReferenceText(referenceText);
        normalized.setAbnormalFlag(abnormalFlag.name());
        normalized.setSourceFileLabel(sourceFileLabel);
        normalized.setSourcePage(sourcePage);
        return normalized;
    }

    private void validateResultTextByType(int rowNo, HealthReportResultType resultType, String resultText) {
        if (resultType == HealthReportResultType.NUMBER) {
            if (!NUMBER_RESULT_PATTERN.matcher(resultText).matches()) {
                throw new BusinessException("HEALTH_REPORT_DETAIL_INVALID",
                        "第 " + rowNo + " 项数值结果格式非法（最多 10 位整数和 8 位小数，可带负号）");
            }
            return;
        }
        if (resultType == HealthReportResultType.QUALITATIVE) {
            if (resultText.length() > MAX_QUALITATIVE_LENGTH) {
                throw new BusinessException("HEALTH_REPORT_DETAIL_INVALID", "第 " + rowNo + " 项定性结果不能超过 80 字符");
            }
            return;
        }
        if (resultText.length() > MAX_TEXT_LENGTH) {
            throw new BusinessException("HEALTH_REPORT_DETAIL_INVALID", "第 " + rowNo + " 项文本结果不能超过 1000 字符");
        }
    }

    private HealthReportDetailsVO buildDetailsVO(HealthReport report, HealthReportDetail detail) {
        HealthReportVO reportVO = toReportVO(report, detail == null ? null : detail.getRecordId());
        HealthReportDetailsVO vo = new HealthReportDetailsVO();
        vo.setReport(reportVO);
        vo.setRevision(detail == null ? 0 : detail.getRevision());
        vo.setLinkedRecordId(detail == null ? null : detail.getRecordId());
        List<HealthReportDetailItem> storedItems = detail == null || detail.getItems() == null
                ? List.of()
                : detail.getItems();
        vo.setItems(storedItems.stream().map(this::toItemVO).toList());
        return vo;
    }

    private HealthReportDetailItemVO toItemVO(HealthReportDetailItem item) {
        HealthReportDetailItemVO vo = new HealthReportDetailItemVO();
        vo.setGroup(item.getGroup());
        vo.setItemCode(item.getItemCode());
        vo.setName(item.getName());
        vo.setResultType(item.getResultType());
        vo.setResultText(item.getResultText());
        vo.setNumericValue(deriveNumericValue(item.getResultType(), item.getResultText()));
        vo.setUnit(item.getUnit());
        vo.setReferenceText(item.getReferenceText());
        vo.setAbnormalFlag(StringUtils.hasText(item.getAbnormalFlag())
                ? item.getAbnormalFlag()
                : HealthReportAbnormalFlag.UNSPECIFIED.name());
        vo.setSourceFileLabel(item.getSourceFileLabel());
        vo.setSourcePage(item.getSourcePage());
        return vo;
    }

    private String deriveNumericValue(String resultType, String resultText) {
        if (!HealthReportResultType.NUMBER.name().equals(resultType) || !StringUtils.hasText(resultText)) {
            return null;
        }
        try {
            return new BigDecimal(resultText.trim()).toPlainString();
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private HealthReportVO toReportVO(HealthReport report, Long linkedRecordId) {
        HealthReportVO vo = new HealthReportVO();
        vo.setId(report.getId());
        vo.setVisitId(report.getVisitId());
        vo.setLinkedRecordId(linkedRecordId);
        vo.setExamDate(report.getExamDate());
        vo.setHospitalName(report.getHospitalName());
        vo.setReportTitle(report.getReportTitle());
        vo.setSummary(report.getSummary());
        vo.setDoctorAdvice(report.getDoctorAdvice());
        vo.setReportFileName(report.getReportFileName());
        vo.setReportUrl(report.getReportUrl());
        vo.setCreatedAt(report.getCreatedAt());
        vo.setUpdatedAt(report.getUpdatedAt());
        vo.setAttachments(attachmentService.listBusinessAttachments(
                AttachmentConstants.BUSINESS_HEALTH_REPORT, report.getId(), AttachmentConstants.USAGE_ATTACHMENT));
        return vo;
    }

    private void ensureCurrentUserExists(Long currentUserId) {
        User currentUser = userMapper.selectById(currentUserId);
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
    }

    private HealthReport getOwnedReportOrThrow(Long currentUserId, Long reportId) {
        HealthReport report = healthReportMapper.selectById(reportId);
        if (report == null || !currentUserId.equals(report.getOwnerUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "健康报告不存在");
        }
        return report;
    }

    private HealthReport getOwnedReportForUpdate(Long currentUserId, Long reportId) {
        HealthReport report = healthReportMapper.selectOne(new QueryWrapper<HealthReport>()
                .eq("id", reportId)
                .eq("owner_user_id", currentUserId)
                .last("FOR UPDATE"));
        if (report == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "健康报告不存在");
        }
        return report;
    }

    private void ensureOwnedRecordExists(Long currentUserId, Long recordId) {
        HealthRecord record = healthRecordMapper.selectById(recordId);
        if (record == null || !currentUserId.equals(record.getOwnerUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "健康指标记录不存在");
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
