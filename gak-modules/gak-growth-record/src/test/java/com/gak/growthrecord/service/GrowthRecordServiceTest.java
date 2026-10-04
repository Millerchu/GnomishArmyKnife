package com.gak.growthrecord.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.framework.exception.BusinessException;
import com.gak.growthrecord.domain.GrowthChild;
import com.gak.growthrecord.domain.GrowthMeasurement;
import com.gak.growthrecord.dto.SaveGrowthChildRequest;
import com.gak.growthrecord.dto.SaveGrowthMeasurementRequest;
import com.gak.growthrecord.dto.SaveGrowthVaccinationRequest;
import com.gak.growthrecord.enums.ChildGender;
import com.gak.growthrecord.enums.VaccinationStatus;
import com.gak.growthrecord.mapper.GrowthChildMapper;
import com.gak.growthrecord.mapper.GrowthMeasurementMapper;
import com.gak.growthrecord.mapper.GrowthMilestoneMapper;
import com.gak.growthrecord.mapper.GrowthVaccinationMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 记录归属和生日边界，不能由浏览器校验代替。 */
class GrowthRecordServiceTest {
    private GrowthChildMapper children;
    private GrowthMeasurementMapper measurements;
    private GrowthVaccinationMapper vaccinations;
    private GrowthMilestoneMapper milestones;
    private GrowthRecordService service;

    @BeforeEach
    void setup() {
        children = mock(GrowthChildMapper.class);
        measurements = mock(GrowthMeasurementMapper.class);
        vaccinations = mock(GrowthVaccinationMapper.class);
        milestones = mock(GrowthMilestoneMapper.class);
        service = new GrowthRecordService(children, measurements, vaccinations, milestones);
        GrowthChild child = new GrowthChild();
        child.setId(2L); child.setOwnerUserId(1L); child.setBirthday(LocalDate.of(2021, 6, 18));
        when(children.lockOwnedChild(1L, 2L)).thenReturn(child);
    }

    @Test
    void foreignChildBlocksCreateAndDeleteWithoutTouchingRecords() {
        assertThrows(BusinessException.class, () -> service.createMeasurement(99L, 2L, measurement()));
        assertThrows(BusinessException.class, () -> service.deleteMilestone(99L, 2L, 4L));
        verifyNoInteractions(measurements, vaccinations, milestones);
    }

    @Test
    void measurementCannotPredateBirthOrBeEmpty() {
        SaveGrowthMeasurementRequest request = measurement();
        request.setMeasureDate(LocalDate.of(2021, 6, 17));
        assertThrows(BusinessException.class, () -> service.createMeasurement(1L, 2L, request));
        request.setMeasureDate(LocalDate.of(2021, 6, 18)); request.setHeightCm(null);
        assertThrows(BusinessException.class, () -> service.createMeasurement(1L, 2L, request));
        verify(measurements, never()).insert(any(GrowthMeasurement.class));
    }

    @Test
    void futurePlanAllowedButCompletedInjectionRejected() {
        SaveGrowthVaccinationRequest request = new SaveGrowthVaccinationRequest();
        request.setVaccineName("预约疫苗"); request.setDose(1);
        request.setVaccinationDate(LocalDate.now().plusDays(10)); request.setStatus(VaccinationStatus.PLANNED);
        assertEquals(VaccinationStatus.PLANNED, service.createVaccination(1L, 2L, request).getStatus());
        request.setStatus(VaccinationStatus.COMPLETED);
        assertThrows(BusinessException.class, () -> service.createVaccination(1L, 2L, request));
    }

    @Test
    void birthdayChangeCannotMovePastExistingMeasurement() {
        when(measurements.selectCount(any(QueryWrapper.class))).thenReturn(1L);
        SaveGrowthChildRequest request = new SaveGrowthChildRequest();
        request.setName("宝宝"); request.setBirthday(LocalDate.of(2024, 1, 1)); request.setGender(ChildGender.UNKNOWN);
        assertThrows(BusinessException.class, () -> service.updateChild(1L, 2L, request));
        verify(children, never()).updateById(any(GrowthChild.class));
    }

    @Test
    void populatedChildCannotBeDeleted() {
        when(measurements.selectCount(any(QueryWrapper.class))).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.deleteChild(1L, 2L));
        verify(children, never()).delete(any(QueryWrapper.class));
    }

    @Test
    void recordIdOutsideSelectedChildCannotBeUpdated() {
        when(measurements.selectOne(any(QueryWrapper.class))).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.updateMeasurement(1L, 2L, 88L, measurement()));
        verify(measurements, never()).updateById(any(GrowthMeasurement.class));
    }

    private SaveGrowthMeasurementRequest measurement() {
        SaveGrowthMeasurementRequest request = new SaveGrowthMeasurementRequest();
        request.setMeasureDate(LocalDate.of(2024, 6, 18)); request.setHeightCm(new BigDecimal("99.60"));
        return request;
    }
}
