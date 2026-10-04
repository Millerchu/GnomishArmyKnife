package com.gak.fuelstats.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gak.attachment.service.AttachmentService;
import com.gak.fuelstats.domain.FuelRecord;
import com.gak.fuelstats.mapper.FuelRecordMapper;
import com.gak.fuelstats.vo.FuelSummaryVO;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 覆盖真实补油周期、记录中断及多车聚合边界。 */
class FuelRecordServiceTest {
    private FuelSummaryVO summary(FuelRecord... records) {
        FuelRecordMapper mapper = mock(FuelRecordMapper.class);
        UserMapper users = mock(UserMapper.class);
        when(users.selectById(1L)).thenReturn(new User());
        when(mapper.selectList(any())).thenReturn(List.of(records));
        return new FuelRecordService(mapper, users, mock(AttachmentService.class), mock(FuelBillSyncService.class)).getSummary(1L);
    }

    private FuelRecord record(int day, String mileage, String volume, String fill) {
        FuelRecord record = new FuelRecord();
        record.setId((long) day);
        record.setVehicleName("长安CS75 Plus");
        record.setFuelDate(LocalDate.of(2026, 8, 1).plusDays(day));
        record.setOdometerKm(new BigDecimal(mileage));
        record.setFuelVolume(new BigDecimal(volume));
        record.setFillType(fill);
        record.setLastRecordKnown(true);
        record.setTotalAmount(BigDecimal.ZERO);
        record.setDiscountedAmount(BigDecimal.ZERO);
        return record;
    }

    @Test
    void shouldCalculateCostFromCompletedCyclePaidAmounts() {
        FuelRecord baseline = record(0, "1000", "50", "FULL");
        baseline.setDiscountedAmount(new BigDecimal("999"));
        FuelRecord partial = record(1, "1200", "10", "PARTIAL");
        partial.setDiscountedAmount(new BigDecimal("75.50"));
        FuelRecord full = record(2, "1500", "35", "FULL");
        full.setDiscountedAmount(new BigDecimal("249.50"));
        var result = summary(baseline, partial, full);
        assertEquals(new BigDecimal("325.00"), result.getRecentRecords().get(0).getConsumptionAmount());
        assertEquals(new BigDecimal("0.650"), result.getRecentRecords().get(0).getCostPerKm());
        assertNull(result.getRecentRecords().get(1).getCostPerKm());
        assertNull(result.getRecentRecords().get(2).getCostPerKm());
    }

    @Test
    void shouldResetCycleMoneyAfterMissingRecordAndKeepValidZeroCost() {
        FuelRecord partial = record(1, "1100", "10", "PARTIAL");
        partial.setDiscountedAmount(new BigDecimal("100"));
        FuelRecord missing = record(2, "1200", "10", "FULL");
        missing.setLastRecordKnown(false);
        missing.setDiscountedAmount(new BigDecimal("200"));
        var result = summary(record(0, "1000", "50", "FULL"), partial, missing,
                record(3, "1500", "30", "FULL"));
        assertEquals(new BigDecimal("0.000"), result.getRecentRecords().get(0).getCostPerKm());
        assertNull(result.getRecentRecords().get(1).getCostPerKm());
    }

    @Test
    void shouldCloseRealPartialFillCycleAndKeepAdjacentDistance() {
        var result = summary(record(42, "42916", "54.52", "FULL"),
                record(8, "42194", "53.38", "FULL"), record(25, "42616", "13.39", "PARTIAL"));
        var latest = result.getRecentRecords().get(0);
        assertEquals(new BigDecimal("9.41"), latest.getFuelConsumption());
        assertEquals(new BigDecimal("722.0"), latest.getConsumptionDistanceKm());
        assertEquals(new BigDecimal("67.91"), latest.getConsumptionFuelVolume());
        assertEquals(new BigDecimal("300.0"), latest.getDistanceKm());
        assertNull(result.getRecentRecords().get(1).getFuelConsumption());
        assertNull(result.getRecentRecords().get(2).getFuelConsumption());
        assertEquals(new BigDecimal("9.41"), result.getAverageConsumption());
        assertEquals(new BigDecimal("9.41"), result.getVehicleStats().get(0).getAverageConsumption());
        assertEquals(new BigDecimal("121.29"), result.getTotalFuelVolume());
    }

    @Test
    void shouldWeightCompletedCyclesAndExcludeOpenCycle() {
        var result = summary(record(0, "1000", "50", "FULL"), record(1, "1100", "10", "FULL"),
                record(2, "2000", "45", "FULL"), record(3, "2100", "20", "PARTIAL"));
        assertEquals(new BigDecimal("5.50"), result.getAverageConsumption());
        assertEquals(new BigDecimal("5.50"), result.getVehicleStats().get(0).getAverageConsumption());
    }

    @Test
    void shouldRestartAfterMissingRecordAtFullFill() {
        FuelRecord missing = record(1, "1200", "20", "FULL");
        missing.setLastRecordKnown(false);
        var result = summary(record(0, "1000", "50", "FULL"), missing, record(2, "1500", "30", "FULL"));
        assertNull(result.getRecentRecords().get(1).getFuelConsumption());
        assertEquals(new BigDecimal("10.00"), result.getRecentRecords().get(0).getFuelConsumption());
    }

    @Test
    void shouldDiscardBrokenPartialCycleUntilNextFullFill() {
        FuelRecord missing = record(1, "1200", "20", "PARTIAL");
        missing.setLastRecordKnown(false);
        var result = summary(record(0, "1000", "50", "FULL"), missing, record(2, "1500", "30", "FULL"));
        assertNull(result.getRecentRecords().get(0).getFuelConsumption());
        assertEquals(new BigDecimal("0.00"), result.getAverageConsumption());
    }

    @Test
    void shouldAccumulateMultiplePartialFillsIncludingSameMileage() {
        var result = summary(record(0, "1000", "50", "FULL"), record(1, "1200", "10", "PARTIAL"),
                record(2, "1200", "5", "PARTIAL"), record(3, "1500", "35", "FULL"));
        assertEquals(new BigDecimal("10.00"), result.getRecentRecords().get(0).getFuelConsumption());
    }

    @Test
    void shouldResetAfterOdometerRollback() {
        var result = summary(record(0, "1000", "50", "FULL"), record(1, "900", "10", "PARTIAL"),
                record(2, "1500", "35", "FULL"));
        assertNull(result.getRecentRecords().get(0).getFuelConsumption());
    }

    @Test
    void shouldKeepVehiclesIndependentAndNotUseInitialPartialFill() {
        FuelRecord other = record(1, "1200", "20", "FULL");
        other.setVehicleName("其他车辆");
        var result = summary(record(0, "1000", "50", "PARTIAL"), other, record(2, "1500", "30", "FULL"));
        assertTrue(result.getRecentRecords().stream().allMatch(item -> item.getFuelConsumption() == null));
    }
}
