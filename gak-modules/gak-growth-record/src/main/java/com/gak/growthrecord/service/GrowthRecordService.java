package com.gak.growthrecord.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.framework.exception.BusinessException;
import com.gak.growthrecord.domain.GrowthChild;
import com.gak.growthrecord.domain.GrowthMeasurement;
import com.gak.growthrecord.domain.GrowthVaccination;
import com.gak.growthrecord.domain.GrowthMilestone;
import com.gak.growthrecord.dto.SaveGrowthChildRequest;
import com.gak.growthrecord.dto.SaveGrowthMeasurementRequest;
import com.gak.growthrecord.dto.SaveGrowthVaccinationRequest;
import com.gak.growthrecord.dto.SaveGrowthMilestoneRequest;
import com.gak.growthrecord.vo.GrowthChildVO;
import com.gak.growthrecord.vo.GrowthMeasurementVO;
import com.gak.growthrecord.vo.GrowthVaccinationVO;
import com.gak.growthrecord.vo.GrowthMilestoneVO;
import com.gak.growthrecord.mapper.GrowthChildMapper;
import com.gak.growthrecord.mapper.GrowthMeasurementMapper;
import com.gak.growthrecord.mapper.GrowthVaccinationMapper;
import com.gak.growthrecord.mapper.GrowthMilestoneMapper;
import com.gak.growthrecord.enums.VaccinationStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 宝宝行锁使生日变更和记录写入遵守同一日期边界。 */
@Service
public class GrowthRecordService {
    private final GrowthChildMapper children;
    private final GrowthMeasurementMapper measurements;
    private final GrowthVaccinationMapper vaccinations;
    private final GrowthMilestoneMapper milestones;

    public GrowthRecordService(GrowthChildMapper children, GrowthMeasurementMapper measurements,
                               GrowthVaccinationMapper vaccinations, GrowthMilestoneMapper milestones) {
        this.children = children;
        this.measurements = measurements;
        this.vaccinations = vaccinations;
        this.milestones = milestones;
    }

    public List<GrowthChildVO> listChildren(Long owner) {
        return children.selectList(new QueryWrapper<GrowthChild>().eq("owner_user_id", owner)
                .orderByAsc("created_at", "id")).stream().map(child -> copy(child, GrowthChildVO::new)).toList();
    }

    @Transactional
    public GrowthChildVO createChild(Long owner, SaveGrowthChildRequest request) {
        validateBirthday(request.getBirthday());
        GrowthChild child = new GrowthChild();
        BeanUtils.copyProperties(request, child);
        child.setName(request.getName().trim());
        child.setOwnerUserId(owner);
        child.setCreatedAt(LocalDateTime.now());
        child.setUpdatedAt(child.getCreatedAt());
        children.insert(child);
        return copy(child, GrowthChildVO::new);
    }

    @Transactional
    public GrowthChildVO updateChild(Long owner, Long childId, SaveGrowthChildRequest request) {
        GrowthChild child = lockChild(owner, childId);
        validateBirthday(request.getBirthday());
        if (measurements.selectCount(GrowthRecordService.<GrowthMeasurement>owned(owner, childId).lt("measure_date", request.getBirthday())) > 0
                || vaccinations.selectCount(GrowthRecordService.<GrowthVaccination>owned(owner, childId).lt("vaccination_date", request.getBirthday())) > 0
                || milestones.selectCount(GrowthRecordService.<GrowthMilestone>owned(owner, childId).lt("event_date", request.getBirthday())) > 0) {
            throw new BusinessException("GROWTH_BIRTHDAY_CONFLICT", "出生日期不能晚于已有记录的发生日期");
        }
        BeanUtils.copyProperties(request, child);
        child.setName(request.getName().trim());
        child.setUpdatedAt(LocalDateTime.now());
        children.updateById(child);
        return copy(child, GrowthChildVO::new);
    }

    @Transactional
    public void deleteChild(Long owner, Long childId) {
        lockChild(owner, childId);
        if (measurements.selectCount(owned(owner, childId)) > 0
                || vaccinations.selectCount(owned(owner, childId)) > 0
                || milestones.selectCount(owned(owner, childId)) > 0) {
            throw new BusinessException("GROWTH_CHILD_NOT_EMPTY", "请先删除宝宝的生长、接种和里程碑记录，再删除档案");
        }
        children.delete(new QueryWrapper<GrowthChild>().eq("id", childId).eq("owner_user_id", owner));
    }

    private void validateBirthday(LocalDate birthday) {
        if (birthday == null || birthday.isAfter(LocalDate.now())) {
            throw new BusinessException("GROWTH_DATE_INVALID", "出生日期不能为空或晚于今天");
        }
    }

    private GrowthChild readChild(Long owner, Long childId) {
        GrowthChild child = children.selectOne(new QueryWrapper<GrowthChild>()
                .eq("id", childId).eq("owner_user_id", owner));
        if (child == null) {
            throw missing();
        }
        return child;
    }

    private GrowthChild lockChild(Long owner, Long childId) {
        GrowthChild child = children.lockOwnedChild(owner, childId);
        if (child == null) {
            throw missing();
        }
        return child;
    }

    private void validateDate(GrowthChild child, LocalDate date, boolean allowFuture) {
        if (date == null || date.isBefore(child.getBirthday()) || (!allowFuture && date.isAfter(LocalDate.now()))) {
            throw new BusinessException("GROWTH_DATE_INVALID", allowFuture
                    ? "预约日期不能早于宝宝出生日期" : "记录日期应在宝宝出生日期与今天之间");
        }
    }

    private static <T> QueryWrapper<T> owned(Long owner, Long childId) {
        return new QueryWrapper<T>().eq("owner_user_id", owner).eq("child_id", childId);
    }

    private static BusinessException missing() {
        return new BusinessException("GROWTH_NOT_FOUND", "宝宝或记录不存在，或无权访问");
    }

    private static <T> T copy(Object source, Supplier<T> factory) {
        T target = factory.get();
        BeanUtils.copyProperties(source, target);
        return target;
    }

    public List<GrowthMeasurementVO> listMeasurements(Long owner, Long childId) {
        readChild(owner, childId);
        return measurements.selectList(GrowthRecordService.<GrowthMeasurement>owned(owner, childId)
                .orderByDesc("measure_date", "created_at", "id")).stream()
                .map(record -> copy(record, GrowthMeasurementVO::new)).toList();
    }

    @Transactional
    public GrowthMeasurementVO createMeasurement(Long owner, Long childId, SaveGrowthMeasurementRequest request) {
        GrowthChild child = lockChild(owner, childId);
        validateDate(child, request.getMeasureDate(), false);
        if (request.getHeightCm() == null && request.getWeightKg() == null && request.getHeadCm() == null) {
            throw new BusinessException("GROWTH_METRIC_REQUIRED", "身高、体重、头围至少填写一项");
        }
        GrowthMeasurement record = new GrowthMeasurement();
        BeanUtils.copyProperties(request, record);
        record.setOwnerUserId(owner);
        record.setChildId(childId);
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(record.getCreatedAt());
        measurements.insert(record);
        return copy(record, GrowthMeasurementVO::new);
    }

    @Transactional
    public GrowthMeasurementVO updateMeasurement(Long owner, Long childId, Long id, SaveGrowthMeasurementRequest request) {
        GrowthChild child = lockChild(owner, childId);
        GrowthMeasurement record = measurements.selectOne(GrowthRecordService.<GrowthMeasurement>owned(owner, childId).eq("id", id));
        if (record == null) {
            throw missing();
        }
        validateDate(child, request.getMeasureDate(), false);
        if (request.getHeightCm() == null && request.getWeightKg() == null && request.getHeadCm() == null) {
            throw new BusinessException("GROWTH_METRIC_REQUIRED", "身高、体重、头围至少填写一项");
        }
        BeanUtils.copyProperties(request, record);
        record.setUpdatedAt(LocalDateTime.now());
        measurements.updateById(record);
        return copy(record, GrowthMeasurementVO::new);
    }

    @Transactional
    public void deleteMeasurement(Long owner, Long childId, Long id) {
        lockChild(owner, childId);
        if (measurements.delete(GrowthRecordService.<GrowthMeasurement>owned(owner, childId).eq("id", id)) == 0) {
            throw missing();
        }
    }

    public List<GrowthVaccinationVO> listVaccinations(Long owner, Long childId) {
        readChild(owner, childId);
        return vaccinations.selectList(GrowthRecordService.<GrowthVaccination>owned(owner, childId)
                .orderByDesc("vaccination_date", "created_at", "id")).stream()
                .map(record -> copy(record, GrowthVaccinationVO::new)).toList();
    }

    @Transactional
    public GrowthVaccinationVO createVaccination(Long owner, Long childId, SaveGrowthVaccinationRequest request) {
        GrowthChild child = lockChild(owner, childId);
        validateDate(child, request.getVaccinationDate(), request.getStatus() == VaccinationStatus.PLANNED);
        GrowthVaccination record = new GrowthVaccination();
        BeanUtils.copyProperties(request, record);
        record.setVaccineName(request.getVaccineName().trim());
        record.setOwnerUserId(owner);
        record.setChildId(childId);
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(record.getCreatedAt());
        vaccinations.insert(record);
        return copy(record, GrowthVaccinationVO::new);
    }

    @Transactional
    public GrowthVaccinationVO updateVaccination(Long owner, Long childId, Long id, SaveGrowthVaccinationRequest request) {
        GrowthChild child = lockChild(owner, childId);
        GrowthVaccination record = vaccinations.selectOne(GrowthRecordService.<GrowthVaccination>owned(owner, childId).eq("id", id));
        if (record == null) {
            throw missing();
        }
        validateDate(child, request.getVaccinationDate(), request.getStatus() == VaccinationStatus.PLANNED);
        BeanUtils.copyProperties(request, record);
        record.setVaccineName(request.getVaccineName().trim());
        record.setUpdatedAt(LocalDateTime.now());
        vaccinations.updateById(record);
        return copy(record, GrowthVaccinationVO::new);
    }

    @Transactional
    public void deleteVaccination(Long owner, Long childId, Long id) {
        lockChild(owner, childId);
        if (vaccinations.delete(GrowthRecordService.<GrowthVaccination>owned(owner, childId).eq("id", id)) == 0) {
            throw missing();
        }
    }

    public List<GrowthMilestoneVO> listMilestones(Long owner, Long childId) {
        readChild(owner, childId);
        return milestones.selectList(GrowthRecordService.<GrowthMilestone>owned(owner, childId)
                .orderByDesc("event_date", "created_at", "id")).stream()
                .map(record -> copy(record, GrowthMilestoneVO::new)).toList();
    }

    @Transactional
    public GrowthMilestoneVO createMilestone(Long owner, Long childId, SaveGrowthMilestoneRequest request) {
        GrowthChild child = lockChild(owner, childId);
        validateDate(child, request.getEventDate(), false);
        GrowthMilestone record = new GrowthMilestone();
        BeanUtils.copyProperties(request, record);
        record.setTitle(request.getTitle().trim());
        record.setOwnerUserId(owner);
        record.setChildId(childId);
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(record.getCreatedAt());
        milestones.insert(record);
        return copy(record, GrowthMilestoneVO::new);
    }

    @Transactional
    public GrowthMilestoneVO updateMilestone(Long owner, Long childId, Long id, SaveGrowthMilestoneRequest request) {
        GrowthChild child = lockChild(owner, childId);
        GrowthMilestone record = milestones.selectOne(GrowthRecordService.<GrowthMilestone>owned(owner, childId).eq("id", id));
        if (record == null) {
            throw missing();
        }
        validateDate(child, request.getEventDate(), false);
        BeanUtils.copyProperties(request, record);
        record.setTitle(request.getTitle().trim());
        record.setUpdatedAt(LocalDateTime.now());
        milestones.updateById(record);
        return copy(record, GrowthMilestoneVO::new);
    }

    @Transactional
    public void deleteMilestone(Long owner, Long childId, Long id) {
        lockChild(owner, childId);
        if (milestones.delete(GrowthRecordService.<GrowthMilestone>owned(owner, childId).eq("id", id)) == 0) {
            throw missing();
        }
    }
}


