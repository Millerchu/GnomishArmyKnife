package com.gak.fuelstats.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.attachment.constant.AttachmentConstants;
import com.gak.attachment.service.AttachmentService;
import com.gak.framework.response.PagedResult;
import com.gak.fuelstats.domain.FuelPriceSnapshot;
import com.gak.fuelstats.domain.FuelRecord;
import com.gak.fuelstats.dto.FuelRecordQueryRequest;
import com.gak.fuelstats.dto.SaveFuelRecordRequest;
import com.gak.fuelstats.mapper.FuelPriceSnapshotMapper;
import com.gak.fuelstats.mapper.FuelRecordMapper;
import com.gak.fuelstats.vo.FuelLatestPricesVO;
import com.gak.fuelstats.vo.FuelMonthlyReportItemVO;
import com.gak.fuelstats.vo.FuelRecordVO;
import com.gak.fuelstats.vo.FuelReportsVO;
import com.gak.fuelstats.vo.FuelSummaryVO;
import com.gak.fuelstats.vo.FuelVehicleStatVO;
import com.gak.fuelstats.vo.FuelYearlyCostReportItemVO;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * 油耗统计服务。
 */
@Service
public class FuelRecordService {

    private static final int MONEY_SCALE = 2;
    private static final int VOLUME_SCALE = 2;
    private static final int ODOMETER_SCALE = 1;
    private static final int UNIT_PRICE_SCALE = 3;
    private static final int RECENT_RECORD_LIMIT = 4;
    private static final int MONTH_REPORT_SIZE = 12;
    private static final String LIMIT_ONE_SQL = "LIMIT 1";
    private static final String DEFAULT_VEHICLE_NAME = "未命名车辆";
    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    private static final BigDecimal ZERO_VOLUME = BigDecimal.ZERO.setScale(VOLUME_SCALE, RoundingMode.HALF_UP);
    private static final BigDecimal ZERO_ODOMETER = BigDecimal.ZERO.setScale(ODOMETER_SCALE, RoundingMode.HALF_UP);
    private static final BigDecimal ZERO_UNIT_PRICE = BigDecimal.ZERO.setScale(UNIT_PRICE_SCALE, RoundingMode.HALF_UP);
    private static final BigDecimal ZERO_CONSUMPTION = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final List<String> ALLOWED_FUEL_TYPES = List.of("92", "95", "98", "DIESEL", "ELECTRIC");
    private static final List<String> ALLOWED_FILL_TYPES = List.of("FULL", "PARTIAL");
    private static final DateTimeFormatter MONTH_LABEL_FORMATTER = DateTimeFormatter.ofPattern("MM月", Locale.CHINA);

    private final FuelRecordMapper fuelRecordMapper;
    private final FuelPriceSnapshotMapper fuelPriceSnapshotMapper;
    private final UserMapper userMapper;
    private final AttachmentService attachmentService;

    public FuelRecordService(FuelRecordMapper fuelRecordMapper,
                             FuelPriceSnapshotMapper fuelPriceSnapshotMapper,
                             UserMapper userMapper,
                             AttachmentService attachmentService) {
        this.fuelRecordMapper = fuelRecordMapper;
        this.fuelPriceSnapshotMapper = fuelPriceSnapshotMapper;
        this.userMapper = userMapper;
        this.attachmentService = attachmentService;
    }

    /**
     * 分页查询当前用户加油记录。
     */
    public PagedResult<FuelRecordVO> page(Long currentUserId, FuelRecordQueryRequest request) {
        ensureCurrentUserExists(currentUserId);

        List<FuelRecordVO> records = buildDerivedRecords(loadAllRecords(currentUserId));
        long total = records.size();
        int pageSize = request.getPageSize().intValue();
        long maxPageNo = Math.max(1, (total + pageSize - 1) / pageSize);
        int pageNo = (int) Math.min(request.getPageNo(), maxPageNo);
        int fromIndex = Math.max(0, (pageNo - 1) * pageSize);
        int toIndex = Math.min(records.size(), fromIndex + pageSize);
        List<FuelRecordVO> list = fromIndex >= toIndex ? List.of() : records.subList(fromIndex, toIndex);
        return new PagedResult<>(list, total);
    }

    /**
     * 新增加油记录。
     */
    @Transactional
    public FuelRecordVO create(Long currentUserId, SaveFuelRecordRequest request) {
        ensureCurrentUserExists(currentUserId);
        NormalizedFuelRecord normalized = normalizeRequest(request);

        FuelRecord record = new FuelRecord();
        LocalDateTime now = LocalDateTime.now();
        record.setOwnerUserId(currentUserId);
        applyNormalized(record, normalized);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        fuelRecordMapper.insert(record);
        attachmentService.syncBusinessAttachments(currentUserId,
                AttachmentConstants.BUSINESS_FUEL_RECORD, record.getId(), AttachmentConstants.USAGE_IMAGE,
                request.getAttachmentIds(), 3);
        return findRecordVOById(currentUserId, record.getId());
    }

    /**
     * 更新加油记录。
     */
    @Transactional
    public FuelRecordVO update(Long currentUserId, Long id, SaveFuelRecordRequest request) {
        ensureCurrentUserExists(currentUserId);
        FuelRecord current = getOwnedRecordOrThrow(currentUserId, id);
        NormalizedFuelRecord normalized = normalizeRequest(request);

        applyNormalized(current, normalized);
        current.setUpdatedAt(LocalDateTime.now());
        fuelRecordMapper.updateById(current);
        attachmentService.syncBusinessAttachments(currentUserId,
                AttachmentConstants.BUSINESS_FUEL_RECORD, current.getId(), AttachmentConstants.USAGE_IMAGE,
                request.getAttachmentIds(), 3);
        return findRecordVOById(currentUserId, id);
    }

    /**
     * 删除加油记录。
     */
    @Transactional
    public void delete(Long currentUserId, Long id) {
        ensureCurrentUserExists(currentUserId);
        FuelRecord current = getOwnedRecordOrThrow(currentUserId, id);
        attachmentService.deleteByBusiness(AttachmentConstants.BUSINESS_FUEL_RECORD, current.getId());
        fuelRecordMapper.deleteById(current.getId());
    }

    /**
     * 查询概览统计。
     */
    public FuelSummaryVO getSummary(Long currentUserId) {
        ensureCurrentUserExists(currentUserId);
        List<FuelRecordVO> records = buildDerivedRecords(loadAllRecords(currentUserId));
        FuelSummaryVO summary = new FuelSummaryVO();
        summary.setTotalAmount(sumDiscountedAmount(records));
        summary.setTotalDiscountAmount(sumDiscountAmount(records));
        summary.setTotalFuelVolume(sumFuelVolume(records));
        summary.setAverageUnitPrice(calculateAverageUnitPrice(records));
        summary.setAverageConsumption(calculateAverageConsumption(records));
        summary.setCurrentMonthAmount(calculateCurrentMonthAmount(records));
        summary.setVehicleStats(buildVehicleStats(records));
        summary.setRecentRecords(records.stream().limit(RECENT_RECORD_LIMIT).toList());
        return summary;
    }

    /**
     * 查询最新油价快照。
     */
    public FuelLatestPricesVO getLatestPrices() {
        QueryWrapper<FuelPriceSnapshot> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("publish_date").orderByDesc("updated_at").last(LIMIT_ONE_SQL);
        FuelPriceSnapshot snapshot = fuelPriceSnapshotMapper.selectOne(wrapper);

        FuelLatestPricesVO result = new FuelLatestPricesVO();
        Map<String, BigDecimal> prices = new LinkedHashMap<>();
        prices.put("92", scaleMoney(snapshot == null ? null : snapshot.getPrice92()));
        prices.put("95", scaleMoney(snapshot == null ? null : snapshot.getPrice95()));
        prices.put("98", scaleMoney(snapshot == null ? null : snapshot.getPrice98()));
        prices.put("DIESEL", scaleMoney(snapshot == null ? null : snapshot.getPriceDiesel()));
        result.setPublishDate(snapshot == null ? null : snapshot.getPublishDate());
        result.setNextAdjustTime(snapshot == null ? null : snapshot.getNextAdjustTime());
        result.setAdjustWindow(snapshot == null ? null : snapshot.getAdjustWindow());
        result.setPriceChangeHint(snapshot == null ? null : snapshot.getPriceChangeHint());
        result.setRemark(snapshot == null ? null : snapshot.getRemark());
        result.setPrices(prices);
        return result;
    }

    /**
     * 查询图表报表。
     */
    public FuelReportsVO getReports(Long currentUserId) {
        ensureCurrentUserExists(currentUserId);
        List<FuelRecordVO> records = buildDerivedRecords(loadAllRecords(currentUserId));

        FuelReportsVO result = new FuelReportsVO();
        result.setCurrentYearMonthlyFuel(buildMonthlyReport(records));
        result.setYearlyCostStats(buildYearlyReport(records));
        return result;
    }

    private void ensureCurrentUserExists(Long currentUserId) {
        User user = userMapper.selectById(currentUserId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "当前登录用户不存在");
        }
    }

    private List<FuelRecord> loadAllRecords(Long currentUserId) {
        QueryWrapper<FuelRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("owner_user_id", currentUserId)
                .orderByDesc("fuel_date")
                .orderByDesc("odometer_km")
                .orderByDesc("updated_at")
                .orderByDesc("id");
        return fuelRecordMapper.selectList(wrapper);
    }

    private FuelRecord getOwnedRecordOrThrow(Long currentUserId, Long id) {
        FuelRecord record = fuelRecordMapper.selectById(id);
        if (record == null || !Objects.equals(record.getOwnerUserId(), currentUserId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "加油记录不存在");
        }
        return record;
    }

    private FuelRecordVO findRecordVOById(Long currentUserId, Long id) {
        for (FuelRecordVO item : buildDerivedRecords(loadAllRecords(currentUserId))) {
            if (Objects.equals(item.getId(), id)) {
                return item;
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "加油记录不存在");
    }

    /**
     * 按车辆和时间顺序补齐里程差、百公里油耗等派生字段。
     * 页面直接消费计算后的结果，避免前后端口径不一致。
     */
    private List<FuelRecordVO> buildDerivedRecords(List<FuelRecord> records) {
        List<FuelRecord> ascending = new ArrayList<>(records);
        ascending.sort(Comparator
                .comparing(FuelRecord::getVehicleName, Comparator.nullsLast(String::compareTo))
                .thenComparing(FuelRecord::getFuelDate, Comparator.nullsLast(LocalDate::compareTo))
                .thenComparing(FuelRecord::getOdometerKm, Comparator.nullsLast(BigDecimal::compareTo))
                .thenComparing(FuelRecord::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)));

        Map<String, FuelRecord> previousByVehicle = new LinkedHashMap<>();
        List<FuelRecordVO> derived = new ArrayList<>();
        for (FuelRecord item : ascending) {
            FuelRecord previous = previousByVehicle.get(item.getVehicleName());
            BigDecimal distanceKm = ZERO_ODOMETER;
            BigDecimal fuelConsumption = null;
            if (previous != null
                    && item.getOdometerKm() != null
                    && previous.getOdometerKm() != null
                    && item.getOdometerKm().compareTo(previous.getOdometerKm()) > 0) {
                distanceKm = item.getOdometerKm().subtract(previous.getOdometerKm()).setScale(1, RoundingMode.HALF_UP);
                if (distanceKm.compareTo(BigDecimal.ZERO) > 0) {
                    fuelConsumption = item.getFuelVolume()
                            .divide(distanceKm, 6, RoundingMode.HALF_UP)
                            .multiply(HUNDRED)
                            .setScale(2, RoundingMode.HALF_UP);
                }
            }
            previousByVehicle.put(item.getVehicleName(), item);
            derived.add(toRecordVO(item, distanceKm, fuelConsumption));
        }

        derived.sort(Comparator
                .comparing(FuelRecordVO::getFuelDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(FuelRecordVO::getOdometerKm, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(FuelRecordVO::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(FuelRecordVO::getId, Comparator.nullsLast(Comparator.reverseOrder())));
        return derived;
    }

    /**
     * 将数据库实体转换为视图对象，并统一金额与精度口径。
     */
    private FuelRecordVO toRecordVO(FuelRecord item, BigDecimal distanceKm, BigDecimal fuelConsumption) {
        FuelRecordVO vo = new FuelRecordVO();
        vo.setId(item.getId());
        vo.setVehicleName(item.getVehicleName());
        vo.setFuelDate(item.getFuelDate());
        vo.setOdometerKm(scaleOdometer(item.getOdometerKm()));
        vo.setFuelVolume(scaleVolume(item.getFuelVolume()));
        vo.setTotalAmount(scaleMoney(item.getTotalAmount()));
        vo.setDiscountedAmount(scaleMoney(item.getDiscountedAmount()));
        vo.setDiscountAmount(scaleMoney(item.getTotalAmount().subtract(item.getDiscountedAmount())));
        vo.setUnitPrice(scaleUnitPrice(item.getUnitPrice()));
        vo.setFuelType(item.getFuelType());
        vo.setFillType(item.getFillType());
        vo.setStationName(item.getStationName());
        vo.setNote(item.getNote());
        vo.setDistanceKm(scaleOdometer(distanceKm));
        vo.setFuelConsumption(fuelConsumption == null ? null : fuelConsumption.setScale(2, RoundingMode.HALF_UP));
        vo.setCreatedAt(item.getCreatedAt());
        vo.setUpdatedAt(item.getUpdatedAt());
        vo.setAttachments(attachmentService.listBusinessAttachments(
                AttachmentConstants.BUSINESS_FUEL_RECORD, item.getId(), AttachmentConstants.USAGE_IMAGE));
        return vo;
    }

    private BigDecimal sumDiscountedAmount(List<FuelRecordVO> records) {
        BigDecimal sum = BigDecimal.ZERO;
        for (FuelRecordVO item : records) {
            sum = sum.add(nullSafe(item.getDiscountedAmount()));
        }
        return sum.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal sumDiscountAmount(List<FuelRecordVO> records) {
        BigDecimal sum = BigDecimal.ZERO;
        for (FuelRecordVO item : records) {
            sum = sum.add(nullSafe(item.getDiscountAmount()));
        }
        return sum.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal sumFuelVolume(List<FuelRecordVO> records) {
        BigDecimal sum = BigDecimal.ZERO;
        for (FuelRecordVO item : records) {
            sum = sum.add(nullSafe(item.getFuelVolume()));
        }
        return sum.setScale(VOLUME_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateAverageUnitPrice(List<FuelRecordVO> records) {
        BigDecimal volume = sumFuelVolume(records);
        if (volume.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO_UNIT_PRICE;
        }
        return sumDiscountedAmount(records).divide(volume, 3, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateAverageConsumption(List<FuelRecordVO> records) {
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (FuelRecordVO item : records) {
            if (item.getFuelConsumption() != null && item.getFuelConsumption().compareTo(BigDecimal.ZERO) > 0) {
                sum = sum.add(item.getFuelConsumption());
                count++;
            }
        }
        if (count == 0) {
            return ZERO_CONSUMPTION;
        }
        return sum.divide(BigDecimal.valueOf(count), MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateCurrentMonthAmount(List<FuelRecordVO> records) {
        YearMonth currentMonth = YearMonth.now();
        BigDecimal sum = BigDecimal.ZERO;
        for (FuelRecordVO item : records) {
            if (item.getFuelDate() != null && YearMonth.from(item.getFuelDate()).equals(currentMonth)) {
                sum = sum.add(nullSafe(item.getDiscountedAmount()));
            }
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    private List<FuelVehicleStatVO> buildVehicleStats(List<FuelRecordVO> records) {
        Map<String, VehicleAccumulator> accumulatorMap = new LinkedHashMap<>();
        for (FuelRecordVO item : records) {
            String vehicleName = StringUtils.hasText(item.getVehicleName()) ? item.getVehicleName() : DEFAULT_VEHICLE_NAME;
            VehicleAccumulator accumulator = accumulatorMap.computeIfAbsent(vehicleName, key -> {
                VehicleAccumulator created = new VehicleAccumulator();
                created.vehicleName = key;
                return created;
            });
            accumulator.totalAmount = accumulator.totalAmount.add(nullSafe(item.getDiscountedAmount()));
            accumulator.totalDiscountAmount = accumulator.totalDiscountAmount.add(nullSafe(item.getDiscountAmount()));
            accumulator.totalFuelVolume = accumulator.totalFuelVolume.add(nullSafe(item.getFuelVolume()));
            accumulator.totalDistance = accumulator.totalDistance.add(nullSafe(item.getDistanceKm()));
            accumulator.recordCount++;
        }

        List<FuelVehicleStatVO> result = new ArrayList<>();
        for (VehicleAccumulator item : accumulatorMap.values()) {
            FuelVehicleStatVO vo = new FuelVehicleStatVO();
            vo.setVehicleName(item.vehicleName);
            vo.setTotalAmount(item.totalAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
            vo.setTotalDiscountAmount(item.totalDiscountAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
            vo.setTotalFuelVolume(item.totalFuelVolume.setScale(VOLUME_SCALE, RoundingMode.HALF_UP));
            if (item.totalDistance.compareTo(BigDecimal.ZERO) > 0) {
                vo.setAverageConsumption(item.totalFuelVolume
                        .divide(item.totalDistance, 6, RoundingMode.HALF_UP)
                        .multiply(HUNDRED)
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP));
            } else {
                vo.setAverageConsumption(ZERO_CONSUMPTION);
            }
            vo.setRecordCount(item.recordCount);
            result.add(vo);
        }
        result.sort(Comparator.comparing(FuelVehicleStatVO::getRecordCount, Comparator.reverseOrder())
                .thenComparing(FuelVehicleStatVO::getVehicleName));
        return result;
    }

    /**
     * 生成当前年度 12 个月的月度报表，缺失月份补 0，方便前端直接画折线图。
     */
    private List<FuelMonthlyReportItemVO> buildMonthlyReport(List<FuelRecordVO> records) {
        YearMonth currentYearMonth = YearMonth.of(LocalDate.now().getYear(), 1);
        List<FuelMonthlyReportItemVO> result = new ArrayList<>();
        for (int monthOffset = 0; monthOffset < MONTH_REPORT_SIZE; monthOffset++) {
            YearMonth month = currentYearMonth.plusMonths(monthOffset);
            FuelMonthlyReportItemVO item = new FuelMonthlyReportItemVO();
            item.setLabel(month.atDay(1).format(MONTH_LABEL_FORMATTER));

            BigDecimal fuelVolume = BigDecimal.ZERO;
            BigDecimal totalAmount = BigDecimal.ZERO;
            for (FuelRecordVO record : records) {
                if (record.getFuelDate() != null && YearMonth.from(record.getFuelDate()).equals(month)) {
                    fuelVolume = fuelVolume.add(nullSafe(record.getFuelVolume()));
                    totalAmount = totalAmount.add(nullSafe(record.getDiscountedAmount()));
                }
            }
            item.setFuelVolume(fuelVolume.setScale(VOLUME_SCALE, RoundingMode.HALF_UP));
            item.setTotalAmount(totalAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
            result.add(item);
        }
        return result;
    }

    /**
     * 生成年维度支出报表，供前端年趋势卡片直接消费。
     */
    private List<FuelYearlyCostReportItemVO> buildYearlyReport(List<FuelRecordVO> records) {
        Map<String, FuelYearlyCostReportItemVO> resultMap = new LinkedHashMap<>();
        for (FuelRecordVO record : records) {
            String yearLabel = record.getFuelDate() == null ? "未知" : String.valueOf(record.getFuelDate().getYear());
            FuelYearlyCostReportItemVO item = resultMap.computeIfAbsent(yearLabel, key -> {
                FuelYearlyCostReportItemVO created = new FuelYearlyCostReportItemVO();
                created.setLabel(key);
                created.setTotalAmount(BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
                created.setTotalFuelVolume(BigDecimal.ZERO.setScale(VOLUME_SCALE, RoundingMode.HALF_UP));
                return created;
            });
            item.setTotalAmount(item.getTotalAmount().add(nullSafe(record.getDiscountedAmount()))
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP));
            item.setTotalFuelVolume(item.getTotalFuelVolume().add(nullSafe(record.getFuelVolume()))
                    .setScale(VOLUME_SCALE, RoundingMode.HALF_UP));
        }
        List<FuelYearlyCostReportItemVO> list = new ArrayList<>(resultMap.values());
        list.sort(Comparator.comparing(FuelYearlyCostReportItemVO::getLabel));
        return list;
    }

    /**
     * 统一校验并归一化前端入参，避免后续持久化层再出现重复判空和精度处理。
     */
    private NormalizedFuelRecord normalizeRequest(SaveFuelRecordRequest request) {
        String vehicleName = request.getVehicleName().trim();
        String fuelType = request.getFuelType().trim().toUpperCase(Locale.ROOT);
        String fillType = request.getFillType().trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_FUEL_TYPES.contains(fuelType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fuelType 非法");
        }
        if (!ALLOWED_FILL_TYPES.contains(fillType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fillType 非法");
        }

        BigDecimal totalAmount = request.getTotalAmount().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal discountedAmount = request.getDiscountedAmount().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (discountedAmount.compareTo(totalAmount) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "优惠后金额不能大于加油金额");
        }

        BigDecimal fuelVolume = request.getFuelVolume().setScale(VOLUME_SCALE, RoundingMode.HALF_UP);
        BigDecimal odometerKm = request.getOdometerKm().setScale(ODOMETER_SCALE, RoundingMode.HALF_UP);
        BigDecimal unitPrice = request.getUnitPrice();
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            unitPrice = discountedAmount.divide(fuelVolume, UNIT_PRICE_SCALE, RoundingMode.HALF_UP);
        } else {
            unitPrice = unitPrice.setScale(UNIT_PRICE_SCALE, RoundingMode.HALF_UP);
        }

        return new NormalizedFuelRecord(
                vehicleName,
                request.getFuelDate(),
                odometerKm,
                fuelVolume,
                totalAmount,
                discountedAmount,
                unitPrice,
                fuelType,
                fillType,
                trimToNull(request.getStationName()),
                trimToNull(request.getNote())
        );
    }

    private void applyNormalized(FuelRecord record, NormalizedFuelRecord normalized) {
        record.setVehicleName(normalized.vehicleName());
        record.setFuelDate(normalized.fuelDate());
        record.setOdometerKm(normalized.odometerKm());
        record.setFuelVolume(normalized.fuelVolume());
        record.setTotalAmount(normalized.totalAmount());
        record.setDiscountedAmount(normalized.discountedAmount());
        record.setUnitPrice(normalized.unitPrice());
        record.setFuelType(normalized.fuelType());
        record.setFillType(normalized.fillType());
        record.setStationName(normalized.stationName());
        record.setNote(normalized.note());
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal scaleMoney(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleVolume(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(VOLUME_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleOdometer(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(ODOMETER_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleUnitPrice(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(UNIT_PRICE_SCALE, RoundingMode.HALF_UP);
    }

    private record NormalizedFuelRecord(String vehicleName,
                                        LocalDate fuelDate,
                                        BigDecimal odometerKm,
                                        BigDecimal fuelVolume,
                                        BigDecimal totalAmount,
                                        BigDecimal discountedAmount,
                                        BigDecimal unitPrice,
                                        String fuelType,
                                        String fillType,
                                        String stationName,
                                        String note) {
    }

    /**
     * 车辆统计聚合中间对象。
     */
    private static final class VehicleAccumulator {

        private String vehicleName;
        private BigDecimal totalAmount = BigDecimal.ZERO;
        private BigDecimal totalDiscountAmount = BigDecimal.ZERO;
        private BigDecimal totalFuelVolume = BigDecimal.ZERO;
        private BigDecimal totalDistance = BigDecimal.ZERO;
        private long recordCount = 0;
    }
}
