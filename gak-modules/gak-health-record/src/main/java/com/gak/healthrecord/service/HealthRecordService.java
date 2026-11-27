package com.gak.healthrecord.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.gak.attachment.constant.AttachmentConstants;
import com.gak.attachment.service.AttachmentService;
import com.gak.framework.exception.BusinessException;
import com.gak.framework.response.PagedResult;
import com.gak.healthrecord.domain.HealthRecord;
import com.gak.healthrecord.domain.HealthReport;
import com.gak.healthrecord.domain.HealthReportDetail;
import com.gak.healthrecord.domain.HealthVisit;
import com.gak.healthrecord.dto.HealthRecordQueryRequest;
import com.gak.healthrecord.dto.HealthReportQueryRequest;
import com.gak.healthrecord.dto.HealthTrendQueryRequest;
import com.gak.healthrecord.dto.HealthVisitQueryRequest;
import com.gak.healthrecord.dto.SaveHealthRecordRequest;
import com.gak.healthrecord.dto.SaveHealthReportRequest;
import com.gak.healthrecord.dto.SaveHealthVisitRequest;
import com.gak.healthrecord.mapper.HealthRecordMapper;
import com.gak.healthrecord.mapper.HealthReportDetailMapper;
import com.gak.healthrecord.mapper.HealthReportMapper;
import com.gak.healthrecord.mapper.HealthVisitMapper;
import com.gak.healthrecord.vo.HealthFileUploadVO;
import com.gak.healthrecord.vo.HealthRecordVO;
import com.gak.healthrecord.vo.HealthReportFileResource;
import com.gak.healthrecord.vo.HealthReportVO;
import com.gak.healthrecord.vo.HealthSummaryVO;
import com.gak.healthrecord.vo.HealthTrendPointVO;
import com.gak.healthrecord.vo.HealthTrendVO;
import com.gak.healthrecord.vo.HealthVisitVO;
import com.gak.user.constant.UserSecurityConstants;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 健康应用服务。
 */
@Service
public class HealthRecordService {

    private static final int MONEY_SCALE = 2;
    private static final int BMI_SCALE = 2;
    private static final BigDecimal BMI_HEIGHT_FACTOR = BigDecimal.valueOf(10000);
    private static final BigDecimal MAX_VISIT_AMOUNT = new BigDecimal("9999999999.99");
    private static final BigDecimal ZERO_DECIMAL = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final long MAX_FILE_SIZE = 10L * 1024L * 1024L;
    private static final Map<String, String> CONTENT_TYPE_EXTENSION_MAP = Map.of(
            "application/pdf", ".pdf",
            "application/msword", ".doc",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx",
            "image/png", ".png",
            "image/jpeg", ".jpg",
            "image/webp", ".webp"
    );
    private static final Map<String, Function<HealthRecord, BigDecimal>> TREND_VALUE_GETTER_MAP = createTrendValueGetterMap();

    private final HealthRecordMapper healthRecordMapper;
    private final HealthVisitMapper healthVisitMapper;
    private final HealthVisitBillSyncService visitBillSyncService;
    private final HealthReportMapper healthReportMapper;
    private final HealthReportDetailMapper healthReportDetailMapper;
    private final UserMapper userMapper;
    private final AttachmentService attachmentService;
    private final Path localStorageDir;
    private final String publicUrlPrefix;

    public HealthRecordService(HealthRecordMapper healthRecordMapper,
                               HealthVisitMapper healthVisitMapper,
                               HealthReportMapper healthReportMapper,
                               HealthReportDetailMapper healthReportDetailMapper,
                               UserMapper userMapper,
                               AttachmentService attachmentService,
                               HealthVisitBillSyncService visitBillSyncService,
                               @Value("${gak.health.file.local-dir:./data/health-records}") String localDir,
                               @Value("${gak.health.file.public-url-prefix:/api/health-records/report-files/}") String publicUrlPrefix) {
        this.healthRecordMapper = healthRecordMapper;
        this.healthVisitMapper = healthVisitMapper;
        this.healthReportMapper = healthReportMapper;
        this.healthReportDetailMapper = healthReportDetailMapper;
        this.userMapper = userMapper;
        this.attachmentService = attachmentService;
        this.visitBillSyncService = visitBillSyncService;
        this.localStorageDir = Paths.get(localDir).toAbsolutePath().normalize();
        this.publicUrlPrefix = publicUrlPrefix.endsWith("/") ? publicUrlPrefix : publicUrlPrefix + "/";
    }

    public PagedResult<HealthRecordVO> pageRecords(Long currentUserId, HealthRecordQueryRequest request) {
        ensureCurrentUserExists(currentUserId);
        List<HealthRecord> allRecords = listOwnedRecords(currentUserId, request.getMeasureDate());
        List<HealthRecord> pageItems = slicePage(allRecords, request.getPageNo(), request.getPageSize());
        Map<Long, Long> sourceReportMap = findSourceReportIds(pageItems.stream().map(HealthRecord::getId).toList());
        List<HealthRecordVO> list = pageItems.stream()
                .map(item -> toRecordVO(item, sourceReportMap.get(item.getId())))
                .toList();
        return new PagedResult<>(list, allRecords.size());
    }

    /** 按 ID 查询当前用户的核心指标记录，用于从报告单直达。 */
    public HealthRecordVO getRecord(Long currentUserId, Long id) {
        ensureCurrentUserExists(currentUserId);
        HealthRecord current = getOwnedRecordOrThrow(currentUserId, id);
        return toRecordVO(current, findSourceReportId(current.getId()));
    }

    @Transactional
    public HealthRecordVO createRecord(Long currentUserId, SaveHealthRecordRequest request) {
        ensureCurrentUserExists(currentUserId);
        HealthRecord record = new HealthRecord();
        record.setOwnerUserId(currentUserId);
        applyRecordRequest(record, request);
        LocalDateTime now = LocalDateTime.now();
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        healthRecordMapper.insert(record);
        return toRecordVO(record, null);
    }

    @Transactional
    public HealthRecordVO updateRecord(Long currentUserId, Long id, SaveHealthRecordRequest request) {
        ensureCurrentUserExists(currentUserId);
        HealthRecord current = getOwnedRecordOrThrow(currentUserId, id);
        applyRecordRequest(current, request);
        current.setUpdatedAt(LocalDateTime.now());
        healthRecordMapper.updateById(current);
        return toRecordVO(current, findSourceReportId(current.getId()));
    }

    @Transactional
    public void deleteRecord(Long currentUserId, Long id) {
        ensureCurrentUserExists(currentUserId);
        HealthRecord current = getOwnedRecordOrThrow(currentUserId, id);
        if (findSourceReportId(current.getId()) != null) {
            throw new BusinessException("HEALTH_RECORD_LINKED", "该核心指标记录已关联体检报告，请先解除关联后再删除");
        }
        try {
            healthRecordMapper.deleteById(current.getId());
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException("HEALTH_RECORD_LINKED", "该核心指标记录已关联体检报告，请先解除关联后再删除");
        }
    }

    public HealthSummaryVO getSummary(Long currentUserId) {
        ensureCurrentUserExists(currentUserId);
        List<HealthRecord> records = listOwnedRecords(currentUserId);
        List<HealthVisit> visits = listOwnedVisits(currentUserId);
        List<HealthReport> reports = listOwnedReports(currentUserId, null);

        HealthSummaryVO summary = new HealthSummaryVO();
        summary.setLatestMeasureDate(records.isEmpty() ? null : records.get(0).getMeasureDate());
        summary.setLastVisitDate(visits.isEmpty() ? null : visits.get(0).getVisitDate());
        summary.setLastExamDate(reports.isEmpty() ? null : reports.get(0).getExamDate());
        summary.setRecordCount(records.size());
        summary.setVisitCount(visits.size());
        summary.setReportCount(reports.size());
        return summary;
    }

    public HealthTrendVO getTrends(Long currentUserId, HealthTrendQueryRequest request) {
        ensureCurrentUserExists(currentUserId);
        String metricKey = normalizeMetricKey(request.getMetricKey());
        Function<HealthRecord, BigDecimal> valueGetter = TREND_VALUE_GETTER_MAP.get(metricKey);
        List<HealthRecord> records = listOwnedRecords(currentUserId);
        List<HealthTrendPointVO> points = new ArrayList<>();
        for (int index = records.size() - 1; index >= 0; index -= 1) {
            HealthRecord record = records.get(index);
            BigDecimal value = valueGetter.apply(record);
            if (value == null) {
                continue;
            }
            HealthTrendPointVO point = new HealthTrendPointVO();
            point.setRecordId(record.getId());
            point.setMeasureDate(record.getMeasureDate());
            point.setValue(value);
            points.add(point);
        }
        int limit = request.getLimit() == null ? 12 : request.getLimit();
        if (points.size() > limit) {
            points = new ArrayList<>(points.subList(points.size() - limit, points.size()));
        }
        HealthTrendVO trend = new HealthTrendVO();
        trend.setMetricKey(metricKey);
        trend.setPoints(points);
        return trend;
    }

    public PagedResult<HealthReportVO> pageReports(Long currentUserId, HealthReportQueryRequest request) {
        ensureCurrentUserExists(currentUserId);
        if (request.getVisitId() != null) {
            getOwnedVisitOrThrow(currentUserId, request.getVisitId());
        }
        List<HealthReport> reports = listOwnedReports(currentUserId, request.getVisitId());
        List<HealthReport> pageItems = slicePage(reports, request.getPageNo(), request.getPageSize());
        Map<Long, Long> linkedRecordMap = findLinkedRecordIds(pageItems.stream().map(HealthReport::getId).toList());
        List<HealthReportVO> list = pageItems.stream()
                .map(item -> toReportVO(item, linkedRecordMap.get(item.getId())))
                .toList();
        return new PagedResult<>(list, reports.size());
    }

    @Transactional
    public HealthReportVO createReport(Long currentUserId, SaveHealthReportRequest request) {
        ensureCurrentUserExists(currentUserId);
        HealthReport report = new HealthReport();
        report.setOwnerUserId(currentUserId);
        applyReportRequest(currentUserId, report, request);
        LocalDateTime now = LocalDateTime.now();
        report.setCreatedAt(now);
        report.setUpdatedAt(now);
        healthReportMapper.insert(report);
        attachmentService.syncBusinessAttachments(currentUserId,
                AttachmentConstants.BUSINESS_HEALTH_REPORT, report.getId(), AttachmentConstants.USAGE_ATTACHMENT,
                request.getAttachmentIds(), 10);
        return toReportVO(report, null);
    }

    @Transactional
    public HealthReportVO updateReport(Long currentUserId, Long id, SaveHealthReportRequest request) {
        ensureCurrentUserExists(currentUserId);
        HealthReport current = getOwnedReportOrThrow(currentUserId, id);
        applyReportRequest(currentUserId, current, request);
        current.setUpdatedAt(LocalDateTime.now());
        healthReportMapper.updateById(current);
        attachmentService.syncBusinessAttachments(currentUserId,
                AttachmentConstants.BUSINESS_HEALTH_REPORT, current.getId(), AttachmentConstants.USAGE_ATTACHMENT,
                request.getAttachmentIds(), 10);
        return toReportVO(current, findLinkedRecordId(current.getId()));
    }

    @Transactional
    public void deleteReport(Long currentUserId, Long id) {
        ensureCurrentUserExists(currentUserId);
        HealthReport current = getOwnedReportOrThrow(currentUserId, id);
        attachmentService.deleteByBusiness(AttachmentConstants.BUSINESS_HEALTH_REPORT, current.getId());
        healthReportMapper.deleteById(current.getId());
    }

    public PagedResult<HealthVisitVO> pageVisits(Long currentUserId, HealthVisitQueryRequest request) {
        ensureCurrentUserExists(currentUserId);
        List<HealthVisit> allVisits = listOwnedVisits(currentUserId);
        List<HealthVisit> filteredVisits = filterVisitsByKeyword(allVisits, request.getKeyword());
        Map<Long, Integer> reportCountMap = countReportsByVisit(currentUserId);
        return buildPageResult(filteredVisits, request.getPageNo(), request.getPageSize(), item -> toVisitVO(item, reportCountMap));
    }

    /** 账单来源定位单次就诊，避免分页限制，并校验当前用户归属。 */
    public HealthVisitVO getVisit(Long currentUserId, Long id) {
        ensureCurrentUserExists(currentUserId);
        return toVisitVO(getOwnedVisitOrThrow(currentUserId, id), countReportsByVisit(currentUserId));
    }

    @Transactional
    public HealthVisitVO createVisit(Long currentUserId, SaveHealthVisitRequest request) {
        ensureCurrentUserExists(currentUserId);
        HealthVisit visit = new HealthVisit();
        visit.setOwnerUserId(currentUserId);
        applyVisitRequest(visit, request);
        LocalDateTime now = LocalDateTime.now();
        visit.setCreatedAt(now);
        visit.setUpdatedAt(now);
        healthVisitMapper.insert(visit);
        visitBillSyncService.sync(visit);
        attachmentService.syncBusinessAttachments(currentUserId,
                AttachmentConstants.BUSINESS_HEALTH_VISIT, visit.getId(), AttachmentConstants.USAGE_ATTACHMENT,
                request.getAttachmentIds(), 10);
        return toVisitVO(visit, countReportsByVisit(currentUserId));
    }

    @Transactional
    public HealthVisitVO updateVisit(Long currentUserId, Long id, SaveHealthVisitRequest request) {
        ensureCurrentUserExists(currentUserId);
        HealthVisit current = getOwnedVisitForUpdate(currentUserId, id);
        applyVisitRequest(current, request);
        current.setUpdatedAt(LocalDateTime.now());
        healthVisitMapper.updateById(current);
        visitBillSyncService.sync(current);
        attachmentService.syncBusinessAttachments(currentUserId,
                AttachmentConstants.BUSINESS_HEALTH_VISIT, current.getId(), AttachmentConstants.USAGE_ATTACHMENT,
                request.getAttachmentIds(), 10);
        return toVisitVO(current, countReportsByVisit(currentUserId));
    }

    @Transactional
    public void deleteVisit(Long currentUserId, Long id) {
        ensureCurrentUserExists(currentUserId);
        HealthVisit current = getOwnedVisitForUpdate(currentUserId, id);
        attachmentService.deleteByBusiness(AttachmentConstants.BUSINESS_HEALTH_VISIT, current.getId());
        visitBillSyncService.delete(currentUserId, current.getId());
        healthReportMapper.update(null, new UpdateWrapper<HealthReport>()
                .eq("owner_user_id", currentUserId).eq("visit_id", current.getId()).set("visit_id", null));
        healthVisitMapper.deleteById(current.getId());
    }

    public HealthFileUploadVO uploadReportFile(Long currentUserId, MultipartFile file) {
        ensureCurrentUserExists(currentUserId);
        validateFile(file);
        String extension = resolveExtension(file);
        String storedFileName = UUID.randomUUID().toString().replace("-", "") + extension;
        Path target = localStorageDir.resolve(storedFileName).normalize();
        if (!target.startsWith(localStorageDir)) {
            throw new BusinessException("HEALTH_FILE_PATH_INVALID", "健康附件保存路径非法");
        }
        try {
            Files.createDirectories(localStorageDir);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "健康附件上传失败");
        }
        HealthFileUploadVO result = new HealthFileUploadVO();
        result.setFileName(storedFileName);
        result.setFileUrl(publicUrlPrefix + storedFileName);
        return result;
    }

    public HealthReportFileResource loadReportFile(Long currentUserId, String fileName) {
        ensureCurrentUserExists(currentUserId);
        if (!healthReportMapper.hasHealthAppAccess(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "未获得健康应用权限");
        }
        String normalized = normalizeFileName(fileName);
        String expectedUrl = publicUrlPrefix + normalized;
        boolean ownsFile = healthReportMapper.existsOwnedReportFile(currentUserId, normalized, expectedUrl)
                || healthVisitMapper.existsOwnedCaseRecordFile(currentUserId, normalized, expectedUrl);
        if (!ownsFile) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "健康附件不存在");
        }
        Path path = localStorageDir.resolve(normalized).normalize();
        if (!path.startsWith(localStorageDir) || !Files.exists(path) || !Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "健康附件不存在");
        }
        try {
            Resource resource = new UrlResource(path.toUri());
            return new HealthReportFileResource(resource, resolveMediaType(path), normalized);
        } catch (MalformedURLException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "健康附件不存在");
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "健康附件读取失败");
        }
    }

    private void applyRecordRequest(HealthRecord record, SaveHealthRecordRequest request) {
        validateNonNegative(request.getHeightCm(), "heightCm");
        validateNonNegative(request.getWeightKg(), "weightKg");
        validateNonNegative(request.getBodyFatRate(), "bodyFatRate");
        validateNonNegative(request.getTotalCholesterol(), "totalCholesterol");
        validateNonNegative(request.getTriglycerides(), "triglycerides");
        validateNonNegative(request.getHdlCholesterol(), "hdlCholesterol");
        validateNonNegative(request.getLdlCholesterol(), "ldlCholesterol");
        validateNonNegative(request.getFastingGlucose(), "fastingGlucose");
        validateNonNegative(request.getSystolicPressure(), "systolicPressure");
        validateNonNegative(request.getDiastolicPressure(), "diastolicPressure");
        validateNonNegative(request.getHeartRate(), "heartRate");
        validateNonNegative(request.getUricAcid(), "uricAcid");
        validateNonNegative(request.getAlanineAminotransferase(), "alanineAminotransferase");
        validateNonNegative(request.getAspartateAminotransferase(), "aspartateAminotransferase");
        validateNonNegative(request.getGammaGlutamylTransferase(), "gammaGlutamylTransferase");

        record.setMeasureDate(request.getMeasureDate());
        record.setHeightCm(scaleNullable(request.getHeightCm(), 1));
        record.setWeightKg(scaleNullable(request.getWeightKg(), 1));
        record.setBodyFatRate(scaleNullable(request.getBodyFatRate(), 1));
        record.setSystolicPressure(request.getSystolicPressure());
        record.setDiastolicPressure(request.getDiastolicPressure());
        record.setTotalCholesterol(scaleNullable(request.getTotalCholesterol(), 2));
        record.setTriglycerides(scaleNullable(request.getTriglycerides(), 2));
        record.setHdlCholesterol(scaleNullable(request.getHdlCholesterol(), 2));
        record.setLdlCholesterol(scaleNullable(request.getLdlCholesterol(), 2));
        record.setFastingGlucose(scaleNullable(request.getFastingGlucose(), 2));
        record.setHeartRate(request.getHeartRate());
        record.setUricAcid(request.getUricAcid());
        record.setAlanineAminotransferase(request.getAlanineAminotransferase());
        record.setAspartateAminotransferase(request.getAspartateAminotransferase());
        record.setGammaGlutamylTransferase(request.getGammaGlutamylTransferase());
        record.setNote(trimToNull(request.getNote()));
    }

    private void applyReportRequest(Long currentUserId, HealthReport report, SaveHealthReportRequest request) {
        if (request.getVisitId() != null) {
            getOwnedVisitOrThrow(currentUserId, request.getVisitId());
        }
        report.setVisitId(request.getVisitId());
        report.setExamDate(request.getExamDate());
        report.setHospitalName(trimToNull(request.getHospitalName()));
        report.setReportTitle(request.getReportTitle().trim());
        report.setSummary(trimToNull(request.getSummary()));
        report.setDoctorAdvice(trimToNull(request.getDoctorAdvice()));
        report.setReportFileName(trimToNull(request.getReportFileName()));
        report.setReportUrl(trimToNull(request.getReportUrl()));
    }

    private void applyVisitRequest(HealthVisit visit, SaveHealthVisitRequest request) {
        visit.setVisitDate(request.getVisitDate());
        BigDecimal amount = request.getAmount();
        if (amount != null && (amount.signum() < 0 || amount.compareTo(MAX_VISIT_AMOUNT) > 0
                || amount.stripTrailingZeros().scale() > MONEY_SCALE)) {
            throw new BusinessException("HEALTH_VISIT_AMOUNT_INVALID", "实际自付金额须在 0 至 9999999999.99 元之间，最多两位小数");
        }
        visit.setAmount(amount == null ? null : amount.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY));
        visit.setHospitalName(request.getHospitalName().trim());
        visit.setDepartmentName(trimToNull(request.getDepartmentName()));
        visit.setDoctorName(trimToNull(request.getDoctorName()));
        visit.setVisitType(trimToNull(request.getVisitType()));
        visit.setChiefComplaint(trimToNull(request.getChiefComplaint()));
        visit.setDiagnosisSummary(trimToNull(request.getDiagnosisSummary()));
        visit.setTreatmentPlan(trimToNull(request.getTreatmentPlan()));
        visit.setDoctorAdvice(trimToNull(request.getDoctorAdvice()));
        visit.setCaseRecordFileName(trimToNull(request.getCaseRecordFileName()));
        visit.setCaseRecordUrl(trimToNull(request.getCaseRecordUrl()));
        visit.setNote(trimToNull(request.getNote()));
    }

    private List<HealthRecord> listOwnedRecords(Long currentUserId) {
        return listOwnedRecords(currentUserId, null);
    }

    private List<HealthRecord> listOwnedRecords(Long currentUserId, LocalDate measureDate) {
        QueryWrapper<HealthRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("owner_user_id", currentUserId);
        if (measureDate != null) {
            wrapper.eq("measure_date", measureDate);
        }
        wrapper.orderByDesc("measure_date")
                .orderByDesc("updated_at")
                .orderByDesc("id");
        return healthRecordMapper.selectList(wrapper);
    }

    private List<HealthVisit> listOwnedVisits(Long currentUserId) {
        QueryWrapper<HealthVisit> wrapper = new QueryWrapper<>();
        wrapper.eq("owner_user_id", currentUserId)
                .orderByDesc("visit_date")
                .orderByDesc("updated_at")
                .orderByDesc("id");
        return healthVisitMapper.selectList(wrapper);
    }

    private List<HealthReport> listOwnedReports(Long currentUserId, Long visitId) {
        QueryWrapper<HealthReport> wrapper = new QueryWrapper<>();
        wrapper.eq("owner_user_id", currentUserId);
        if (visitId != null) {
            wrapper.eq("visit_id", visitId);
        }
        wrapper.orderByDesc("exam_date")
                .orderByDesc("updated_at")
                .orderByDesc("id");
        return healthReportMapper.selectList(wrapper);
    }

    private List<HealthVisit> filterVisitsByKeyword(List<HealthVisit> visits, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return visits;
        }
        String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        return visits.stream().filter(item -> containsIgnoreCase(item.getHospitalName(), normalizedKeyword)
                || containsIgnoreCase(item.getDepartmentName(), normalizedKeyword)
                || containsIgnoreCase(item.getDoctorName(), normalizedKeyword)
                || containsIgnoreCase(item.getChiefComplaint(), normalizedKeyword)
                || containsIgnoreCase(item.getDiagnosisSummary(), normalizedKeyword))
                .toList();
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private HealthRecord getOwnedRecordOrThrow(Long currentUserId, Long id) {
        HealthRecord current = healthRecordMapper.selectById(id);
        if (current == null || !currentUserId.equals(current.getOwnerUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "健康指标记录不存在");
        }
        return current;
    }

    /** 串行化同次就诊的修改和删除，防止账单与最终金额不一致。 */
    private HealthVisit getOwnedVisitForUpdate(Long currentUserId, Long id) {
        HealthVisit current = healthVisitMapper.selectOne(new QueryWrapper<HealthVisit>()
                .eq("id", id).eq("owner_user_id", currentUserId).last("FOR UPDATE"));
        if (current == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "医院就诊记录不存在");
        }
        return current;
    }

    private HealthVisit getOwnedVisitOrThrow(Long currentUserId, Long id) {
        HealthVisit current = healthVisitMapper.selectById(id);
        if (current == null || !currentUserId.equals(current.getOwnerUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "医院就诊记录不存在");
        }
        return current;
    }

    private HealthReport getOwnedReportOrThrow(Long currentUserId, Long id) {
        HealthReport current = healthReportMapper.selectById(id);
        if (current == null || !currentUserId.equals(current.getOwnerUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "健康报告不存在");
        }
        return current;
    }

    private HealthRecordVO toRecordVO(HealthRecord record, Long sourceReportId) {
        HealthRecordVO vo = new HealthRecordVO();
        vo.setId(record.getId());
        vo.setSourceReportId(sourceReportId);
        vo.setMeasureDate(record.getMeasureDate());
        vo.setHeightCm(record.getHeightCm());
        vo.setWeightKg(record.getWeightKg());
        vo.setBmi(calculateBmi(record));
        vo.setBodyFatRate(record.getBodyFatRate());
        vo.setSystolicPressure(record.getSystolicPressure());
        vo.setDiastolicPressure(record.getDiastolicPressure());
        vo.setTotalCholesterol(record.getTotalCholesterol());
        vo.setTriglycerides(record.getTriglycerides());
        vo.setHdlCholesterol(record.getHdlCholesterol());
        vo.setLdlCholesterol(record.getLdlCholesterol());
        vo.setFastingGlucose(record.getFastingGlucose());
        vo.setHeartRate(record.getHeartRate());
        vo.setUricAcid(record.getUricAcid());
        vo.setAlanineAminotransferase(record.getAlanineAminotransferase());
        vo.setAspartateAminotransferase(record.getAspartateAminotransferase());
        vo.setGammaGlutamylTransferase(record.getGammaGlutamylTransferase());
        vo.setNote(record.getNote());
        vo.setCreatedAt(record.getCreatedAt());
        vo.setUpdatedAt(record.getUpdatedAt());
        return vo;
    }

    private HealthVisitVO toVisitVO(HealthVisit visit, Map<Long, Integer> reportCountMap) {
        HealthVisitVO vo = new HealthVisitVO();
        vo.setId(visit.getId());
        vo.setVisitDate(visit.getVisitDate());
        vo.setAmount(visit.getAmount());
        vo.setHospitalName(visit.getHospitalName());
        vo.setDepartmentName(visit.getDepartmentName());
        vo.setDoctorName(visit.getDoctorName());
        vo.setVisitType(visit.getVisitType());
        vo.setChiefComplaint(visit.getChiefComplaint());
        vo.setDiagnosisSummary(visit.getDiagnosisSummary());
        vo.setTreatmentPlan(visit.getTreatmentPlan());
        vo.setDoctorAdvice(visit.getDoctorAdvice());
        vo.setCaseRecordFileName(visit.getCaseRecordFileName());
        vo.setCaseRecordUrl(visit.getCaseRecordUrl());
        vo.setNote(visit.getNote());
        vo.setReportCount(reportCountMap.getOrDefault(visit.getId(), 0));
        vo.setCreatedAt(visit.getCreatedAt());
        vo.setUpdatedAt(visit.getUpdatedAt());
        vo.setAttachments(attachmentService.listBusinessAttachments(
                AttachmentConstants.BUSINESS_HEALTH_VISIT, visit.getId(), AttachmentConstants.USAGE_ATTACHMENT));
        return vo;
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

    private Map<Long, Integer> countReportsByVisit(Long currentUserId) {
        Map<Long, Integer> result = new HashMap<>();
        for (HealthReport report : listOwnedReports(currentUserId, null)) {
            if (report.getVisitId() == null) {
                continue;
            }
            result.merge(report.getVisitId(), 1, Integer::sum);
        }
        return result;
    }

    private <T, R> PagedResult<R> buildPageResult(List<T> allItems, Integer requestPageNo, Integer requestPageSize,
                                                  Function<T, R> mapper) {
        List<T> pageItems = slicePage(allItems, requestPageNo, requestPageSize);
        List<R> list = pageItems.stream().map(mapper).toList();
        return new PagedResult<>(list, allItems.size());
    }

    private <T> List<T> slicePage(List<T> allItems, Integer requestPageNo, Integer requestPageSize) {
        int pageSize = requestPageSize == null ? 20 : requestPageSize;
        long total = allItems.size();
        long maxPageNo = Math.max(1, (total + pageSize - 1) / pageSize);
        int pageNo = requestPageNo == null ? 1 : requestPageNo;
        pageNo = (int) Math.min(Math.max(pageNo, 1), maxPageNo);
        int fromIndex = Math.max(0, (pageNo - 1) * pageSize);
        int toIndex = Math.min(allItems.size(), fromIndex + pageSize);
        return fromIndex >= toIndex ? List.of() : allItems.subList(fromIndex, toIndex);
    }

    private Long findSourceReportId(Long recordId) {
        if (recordId == null) {
            return null;
        }
        HealthReportDetail detail = healthReportDetailMapper.findByRecordId(recordId);
        return detail == null ? null : detail.getReportId();
    }

    private Long findLinkedRecordId(Long reportId) {
        if (reportId == null) {
            return null;
        }
        HealthReportDetail detail = healthReportDetailMapper.findByReportId(reportId);
        return detail == null ? null : detail.getRecordId();
    }

    private Map<Long, Long> findSourceReportIds(List<Long> recordIds) {
        if (recordIds == null || recordIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<HealthReportDetail> links = healthReportDetailMapper.findLinksByRecordIds(recordIds);
        if (links == null || links.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Long> map = new HashMap<>();
        for (HealthReportDetail link : links) {
            if (link.getRecordId() != null && link.getReportId() != null) {
                map.put(link.getRecordId(), link.getReportId());
            }
        }
        return map;
    }

    private Map<Long, Long> findLinkedRecordIds(List<Long> reportIds) {
        if (reportIds == null || reportIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<HealthReportDetail> links = healthReportDetailMapper.findLinksByReportIds(reportIds);
        if (links == null || links.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Long> map = new HashMap<>();
        for (HealthReportDetail link : links) {
            if (link.getReportId() != null && link.getRecordId() != null) {
                map.put(link.getReportId(), link.getRecordId());
            }
        }
        return map;
    }

    private void ensureCurrentUserExists(Long currentUserId) {
        User currentUser = currentUserId == null ? null : userMapper.selectById(currentUserId);
        if (currentUser == null
                || !Boolean.TRUE.equals(currentUser.getEnabled())
                || !UserSecurityConstants.ENABLED_STATUS.equalsIgnoreCase(currentUser.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户已失效，请重新登录");
        }
    }

    private void validateNonNegative(BigDecimal value, String fieldName) {
        if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("HEALTH_VALUE_INVALID", fieldName + " 不能小于 0");
        }
    }

    private void validateNonNegative(Integer value, String fieldName) {
        if (value != null && value < 0) {
            throw new BusinessException("HEALTH_VALUE_INVALID", fieldName + " 不能小于 0");
        }
    }

    private BigDecimal scaleNullable(BigDecimal value, int scale) {
        if (value == null) {
            return null;
        }
        return value.setScale(scale, RoundingMode.HALF_UP);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeMetricKey(String metricKey) {
        String normalized = trimToNull(metricKey);
        if (normalized == null) {
            return "weightKg";
        }
        if (!TREND_VALUE_GETTER_MAP.containsKey(normalized)) {
            throw new BusinessException("HEALTH_METRIC_KEY_INVALID", "不支持的趋势指标");
        }
        return normalized;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("HEALTH_FILE_REQUIRED", "上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("HEALTH_FILE_TOO_LARGE", "健康附件不能超过 10MB");
        }
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType) || !CONTENT_TYPE_EXTENSION_MAP.containsKey(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException("HEALTH_FILE_TYPE_INVALID", "仅支持 PDF、图片和 Word 文档");
        }
    }

    private String resolveExtension(MultipartFile file) {
        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType)) {
            String byContentType = CONTENT_TYPE_EXTENSION_MAP.get(contentType.toLowerCase(Locale.ROOT));
            if (byContentType != null) {
                return byContentType;
            }
        }
        throw new BusinessException("HEALTH_FILE_TYPE_INVALID", "无法识别健康附件类型");
    }

    private String normalizeFileName(String fileName) {
        String normalized = trimToNull(fileName);
        if (normalized == null || normalized.contains("..") || normalized.contains("/") || normalized.contains("\\")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "健康附件文件名非法");
        }
        return normalized;
    }

    private MediaType resolveMediaType(Path path) throws IOException {
        String contentType = Files.probeContentType(path);
        if (StringUtils.hasText(contentType)) {
            return MediaType.parseMediaType(contentType);
        }
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF;
        }
        if (fileName.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (fileName.endsWith(".doc")) {
            return MediaType.parseMediaType("application/msword");
        }
        if (fileName.endsWith(".docx")) {
            return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private static Map<String, Function<HealthRecord, BigDecimal>> createTrendValueGetterMap() {
        Map<String, Function<HealthRecord, BigDecimal>> mapping = new HashMap<>();
        mapping.put("weightKg", HealthRecord::getWeightKg);
        mapping.put("bmi", HealthRecordService::calculateBmi);
        mapping.put("bodyFatRate", HealthRecord::getBodyFatRate);
        mapping.put("systolicPressure", record -> integerToDecimal(record.getSystolicPressure()));
        mapping.put("diastolicPressure", record -> integerToDecimal(record.getDiastolicPressure()));
        mapping.put("totalCholesterol", HealthRecord::getTotalCholesterol);
        mapping.put("triglycerides", HealthRecord::getTriglycerides);
        mapping.put("hdlCholesterol", HealthRecord::getHdlCholesterol);
        mapping.put("ldlCholesterol", HealthRecord::getLdlCholesterol);
        mapping.put("fastingGlucose", HealthRecord::getFastingGlucose);
        mapping.put("uricAcid", record -> integerToDecimal(record.getUricAcid()));
        mapping.put("alanineAminotransferase", record -> integerToDecimal(record.getAlanineAminotransferase()));
        mapping.put("aspartateAminotransferase", record -> integerToDecimal(record.getAspartateAminotransferase()));
        mapping.put("gammaGlutamylTransferase", record -> integerToDecimal(record.getGammaGlutamylTransferase()));
        return mapping;
    }

    /** 按同一条核心记录的身高(cm)与体重(kg)派生 BMI，任一为空或非正数时返回 null。 */
    public static BigDecimal calculateBmi(HealthRecord record) {
        if (record == null) {
            return null;
        }
        return calculateBmi(record.getHeightCm(), record.getWeightKg());
    }

    public static BigDecimal calculateBmi(BigDecimal heightCm, BigDecimal weightKg) {
        if (heightCm == null || weightKg == null || heightCm.signum() <= 0 || weightKg.signum() <= 0) {
            return null;
        }
        BigDecimal numerator = weightKg.multiply(BMI_HEIGHT_FACTOR);
        BigDecimal denominator = heightCm.multiply(heightCm);
        return numerator.divide(denominator, BMI_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal integerToDecimal(Integer value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }
}
