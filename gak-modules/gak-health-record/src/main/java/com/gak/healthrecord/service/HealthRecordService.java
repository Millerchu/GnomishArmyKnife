package com.gak.healthrecord.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.attachment.constant.AttachmentConstants;
import com.gak.attachment.service.AttachmentService;
import com.gak.framework.exception.BusinessException;
import com.gak.framework.response.PagedResult;
import com.gak.healthrecord.domain.HealthRecord;
import com.gak.healthrecord.domain.HealthReport;
import com.gak.healthrecord.domain.HealthVisit;
import com.gak.healthrecord.dto.HealthRecordQueryRequest;
import com.gak.healthrecord.dto.HealthReportQueryRequest;
import com.gak.healthrecord.dto.HealthTrendQueryRequest;
import com.gak.healthrecord.dto.HealthVisitQueryRequest;
import com.gak.healthrecord.dto.SaveHealthRecordRequest;
import com.gak.healthrecord.dto.SaveHealthReportRequest;
import com.gak.healthrecord.dto.SaveHealthVisitRequest;
import com.gak.healthrecord.mapper.HealthRecordMapper;
import com.gak.healthrecord.mapper.HealthReportMapper;
import com.gak.healthrecord.mapper.HealthVisitMapper;
import com.gak.healthrecord.vo.HealthFileUploadVO;
import com.gak.healthrecord.vo.HealthRecordVO;
import com.gak.healthrecord.vo.HealthReportVO;
import com.gak.healthrecord.vo.HealthSummaryVO;
import com.gak.healthrecord.vo.HealthTrendPointVO;
import com.gak.healthrecord.vo.HealthTrendVO;
import com.gak.healthrecord.vo.HealthVisitVO;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final HealthReportMapper healthReportMapper;
    private final UserMapper userMapper;
    private final AttachmentService attachmentService;
    private final Path localStorageDir;
    private final String publicUrlPrefix;

    public HealthRecordService(HealthRecordMapper healthRecordMapper,
                               HealthVisitMapper healthVisitMapper,
                               HealthReportMapper healthReportMapper,
                               UserMapper userMapper,
                               AttachmentService attachmentService,
                               @Value("${gak.health.file.local-dir:./data/health-records}") String localDir,
                               @Value("${gak.health.file.public-url-prefix:/api/health-records/report-files/}") String publicUrlPrefix) {
        this.healthRecordMapper = healthRecordMapper;
        this.healthVisitMapper = healthVisitMapper;
        this.healthReportMapper = healthReportMapper;
        this.userMapper = userMapper;
        this.attachmentService = attachmentService;
        this.localStorageDir = Paths.get(localDir).toAbsolutePath().normalize();
        this.publicUrlPrefix = publicUrlPrefix.endsWith("/") ? publicUrlPrefix : publicUrlPrefix + "/";
    }

    public PagedResult<HealthRecordVO> pageRecords(Long currentUserId, HealthRecordQueryRequest request) {
        ensureCurrentUserExists(currentUserId);
        List<HealthRecord> allRecords = listOwnedRecords(currentUserId);
        return buildPageResult(allRecords, request.getPageNo(), request.getPageSize(), this::toRecordVO);
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
        return toRecordVO(record);
    }

    @Transactional
    public HealthRecordVO updateRecord(Long currentUserId, Long id, SaveHealthRecordRequest request) {
        ensureCurrentUserExists(currentUserId);
        HealthRecord current = getOwnedRecordOrThrow(currentUserId, id);
        applyRecordRequest(current, request);
        current.setUpdatedAt(LocalDateTime.now());
        healthRecordMapper.updateById(current);
        return toRecordVO(current);
    }

    @Transactional
    public void deleteRecord(Long currentUserId, Long id) {
        ensureCurrentUserExists(currentUserId);
        HealthRecord current = getOwnedRecordOrThrow(currentUserId, id);
        healthRecordMapper.deleteById(current.getId());
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
            point.setMeasureDate(record.getMeasureDate());
            point.setValue(value);
            points.add(point);
        }
        if (points.size() > request.getLimit()) {
            points = points.subList(points.size() - request.getLimit(), points.size());
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
        return buildPageResult(reports, request.getPageNo(), request.getPageSize(), this::toReportVO);
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
        return toReportVO(report);
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
        return toReportVO(current);
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
        attachmentService.syncBusinessAttachments(currentUserId,
                AttachmentConstants.BUSINESS_HEALTH_VISIT, visit.getId(), AttachmentConstants.USAGE_ATTACHMENT,
                request.getAttachmentIds(), 10);
        return toVisitVO(visit, countReportsByVisit(currentUserId));
    }

    @Transactional
    public HealthVisitVO updateVisit(Long currentUserId, Long id, SaveHealthVisitRequest request) {
        ensureCurrentUserExists(currentUserId);
        HealthVisit current = getOwnedVisitOrThrow(currentUserId, id);
        applyVisitRequest(current, request);
        current.setUpdatedAt(LocalDateTime.now());
        healthVisitMapper.updateById(current);
        attachmentService.syncBusinessAttachments(currentUserId,
                AttachmentConstants.BUSINESS_HEALTH_VISIT, current.getId(), AttachmentConstants.USAGE_ATTACHMENT,
                request.getAttachmentIds(), 10);
        return toVisitVO(current, countReportsByVisit(currentUserId));
    }

    @Transactional
    public void deleteVisit(Long currentUserId, Long id) {
        ensureCurrentUserExists(currentUserId);
        HealthVisit current = getOwnedVisitOrThrow(currentUserId, id);
        attachmentService.deleteByBusiness(AttachmentConstants.BUSINESS_HEALTH_VISIT, current.getId());
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

    public ReportFileResource loadReportFile(String fileName) {
        String normalized = normalizeFileName(fileName);
        Path path = localStorageDir.resolve(normalized).normalize();
        if (!path.startsWith(localStorageDir) || !Files.exists(path) || !Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "健康附件不存在");
        }
        try {
            Resource resource = new UrlResource(path.toUri());
            return new ReportFileResource(resource, resolveMediaType(path));
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
        QueryWrapper<HealthRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("owner_user_id", currentUserId)
                .orderByDesc("measure_date")
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

    private HealthRecordVO toRecordVO(HealthRecord record) {
        HealthRecordVO vo = new HealthRecordVO();
        vo.setId(record.getId());
        vo.setMeasureDate(record.getMeasureDate());
        vo.setHeightCm(record.getHeightCm());
        vo.setWeightKg(record.getWeightKg());
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

    private HealthReportVO toReportVO(HealthReport report) {
        HealthReportVO vo = new HealthReportVO();
        vo.setId(report.getId());
        vo.setVisitId(report.getVisitId());
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
        int pageSize = requestPageSize == null ? 20 : requestPageSize;
        long total = allItems.size();
        long maxPageNo = Math.max(1, (total + pageSize - 1) / pageSize);
        int pageNo = requestPageNo == null ? 1 : requestPageNo;
        pageNo = (int) Math.min(Math.max(pageNo, 1), maxPageNo);
        int fromIndex = Math.max(0, (pageNo - 1) * pageSize);
        int toIndex = Math.min(allItems.size(), fromIndex + pageSize);
        List<R> list = fromIndex >= toIndex
                ? List.of()
                : allItems.subList(fromIndex, toIndex).stream().map(mapper).toList();
        return new PagedResult<>(list, total);
    }

    private void ensureCurrentUserExists(Long currentUserId) {
        User currentUser = userMapper.selectById(currentUserId);
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
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
        mapping.put("bodyFatRate", HealthRecord::getBodyFatRate);
        mapping.put("systolicPressure", record -> integerToDecimal(record.getSystolicPressure()));
        mapping.put("diastolicPressure", record -> integerToDecimal(record.getDiastolicPressure()));
        mapping.put("totalCholesterol", HealthRecord::getTotalCholesterol);
        mapping.put("ldlCholesterol", HealthRecord::getLdlCholesterol);
        mapping.put("fastingGlucose", HealthRecord::getFastingGlucose);
        mapping.put("uricAcid", record -> integerToDecimal(record.getUricAcid()));
        mapping.put("alanineAminotransferase", record -> integerToDecimal(record.getAlanineAminotransferase()));
        mapping.put("aspartateAminotransferase", record -> integerToDecimal(record.getAspartateAminotransferase()));
        mapping.put("gammaGlutamylTransferase", record -> integerToDecimal(record.getGammaGlutamylTransferase()));
        return mapping;
    }

    private static BigDecimal integerToDecimal(Integer value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    public record ReportFileResource(Resource resource, MediaType mediaType) {
    }
}
