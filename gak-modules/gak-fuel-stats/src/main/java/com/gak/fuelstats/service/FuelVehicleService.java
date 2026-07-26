package com.gak.fuelstats.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.gak.fuelstats.domain.FuelVehicle;
import com.gak.fuelstats.dto.SaveFuelVehicleRequest;
import com.gak.fuelstats.mapper.FuelVehicleMapper;
import com.gak.fuelstats.vo.FuelVehicleVO;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * 车辆档案服务，负责用户私有车辆的维护与默认车辆切换。
 */
@Service
public class FuelVehicleService {

    private static final String ENERGY_TYPE_FUEL = "FUEL";
    private static final String ENERGY_TYPE_ELECTRIC = "ELECTRIC";
    private static final String ELECTRIC_FUEL_TYPE = "ELECTRIC";
    private static final List<String> FUEL_TYPES = List.of("92", "95", "98", "DIESEL");

    private final FuelVehicleMapper fuelVehicleMapper;
    private final UserMapper userMapper;

    public FuelVehicleService(FuelVehicleMapper fuelVehicleMapper, UserMapper userMapper) {
        this.fuelVehicleMapper = fuelVehicleMapper;
        this.userMapper = userMapper;
    }

    /**
     * 查询当前用户车辆，默认车辆始终排在第一位。
     */
    public List<FuelVehicleVO> list(Long currentUserId) {
        ensureCurrentUserExists(currentUserId);
        QueryWrapper<FuelVehicle> wrapper = new QueryWrapper<>();
        wrapper.eq("owner_user_id", currentUserId)
                .orderByDesc("default_vehicle")
                .orderByDesc("updated_at")
                .orderByDesc("id");
        return fuelVehicleMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    /**
     * 创建车辆，并在需要时更新默认车辆。
     */
    @Transactional
    public FuelVehicleVO create(Long currentUserId, SaveFuelVehicleRequest request) {
        ensureCurrentUserExists(currentUserId);
        NormalizedFuelVehicle normalized = normalizeRequest(request);
        ensureVehicleNameAvailable(currentUserId, normalized.vehicleName(), null);

        FuelVehicle vehicle = new FuelVehicle();
        LocalDateTime now = LocalDateTime.now();
        vehicle.setOwnerUserId(currentUserId);
        applyNormalized(vehicle, normalized);
        vehicle.setCreatedAt(now);
        vehicle.setUpdatedAt(now);
        fuelVehicleMapper.insert(vehicle);
        applyDefaultVehicle(currentUserId, vehicle.getId(), normalized.defaultVehicle());
        return toVO(fuelVehicleMapper.selectById(vehicle.getId()));
    }

    /**
     * 修改车辆档案；名称可改，历史记录保留原名称以确保统计口径稳定。
     */
    @Transactional
    public FuelVehicleVO update(Long currentUserId, Long id, SaveFuelVehicleRequest request) {
        ensureCurrentUserExists(currentUserId);
        FuelVehicle vehicle = getOwnedVehicleOrThrow(currentUserId, id);
        NormalizedFuelVehicle normalized = normalizeRequest(request);
        ensureVehicleNameAvailable(currentUserId, normalized.vehicleName(), id);

        applyNormalized(vehicle, normalized);
        vehicle.setUpdatedAt(LocalDateTime.now());
        fuelVehicleMapper.updateById(vehicle);
        applyDefaultVehicle(currentUserId, vehicle.getId(), normalized.defaultVehicle());
        return toVO(fuelVehicleMapper.selectById(vehicle.getId()));
    }

    /**
     * 删除车辆档案不删除任何历史能源记录。
     */
    @Transactional
    public void delete(Long currentUserId, Long id) {
        ensureCurrentUserExists(currentUserId);
        FuelVehicle vehicle = getOwnedVehicleOrThrow(currentUserId, id);
        fuelVehicleMapper.deleteById(vehicle.getId());
    }

    private void applyDefaultVehicle(Long currentUserId, Long vehicleId, boolean defaultVehicle) {
        if (!defaultVehicle) {
            return;
        }
        UpdateWrapper<FuelVehicle> wrapper = new UpdateWrapper<>();
        wrapper.eq("owner_user_id", currentUserId)
                .ne("id", vehicleId)
                .set("default_vehicle", false);
        fuelVehicleMapper.update(null, wrapper);
    }

    private void ensureCurrentUserExists(Long currentUserId) {
        User user = userMapper.selectById(currentUserId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "当前登录用户不存在");
        }
    }

    private void ensureVehicleNameAvailable(Long currentUserId, String vehicleName, Long excludedId) {
        QueryWrapper<FuelVehicle> wrapper = new QueryWrapper<>();
        wrapper.eq("owner_user_id", currentUserId).eq("vehicle_name", vehicleName);
        if (excludedId != null) {
            wrapper.ne("id", excludedId);
        }
        if (fuelVehicleMapper.selectCount(wrapper) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "车辆名称已存在");
        }
    }

    private FuelVehicle getOwnedVehicleOrThrow(Long currentUserId, Long id) {
        FuelVehicle vehicle = fuelVehicleMapper.selectById(id);
        if (vehicle == null || !Objects.equals(vehicle.getOwnerUserId(), currentUserId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "车辆不存在");
        }
        return vehicle;
    }

    private NormalizedFuelVehicle normalizeRequest(SaveFuelVehicleRequest request) {
        String vehicleName = request.getVehicleName().trim();
        String energyType = request.getEnergyType().trim().toUpperCase(Locale.ROOT);
        String defaultFuelType = request.getDefaultFuelType().trim().toUpperCase(Locale.ROOT);
        if (!List.of(ENERGY_TYPE_FUEL, ENERGY_TYPE_ELECTRIC).contains(energyType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "energyType 非法");
        }
        if (ENERGY_TYPE_ELECTRIC.equals(energyType) && !ELECTRIC_FUEL_TYPE.equals(defaultFuelType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新能源车辆默认充电类型必须为 ELECTRIC");
        }
        if (ENERGY_TYPE_FUEL.equals(energyType) && !FUEL_TYPES.contains(defaultFuelType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "燃油车默认油号非法");
        }
        if (!StringUtils.hasText(vehicleName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "车辆名称不能为空");
        }
        return new NormalizedFuelVehicle(vehicleName, energyType, defaultFuelType, request.getDefaultVehicle());
    }

    private void applyNormalized(FuelVehicle vehicle, NormalizedFuelVehicle normalized) {
        vehicle.setVehicleName(normalized.vehicleName());
        vehicle.setEnergyType(normalized.energyType());
        vehicle.setDefaultFuelType(normalized.defaultFuelType());
        vehicle.setDefaultVehicle(normalized.defaultVehicle());
    }

    private FuelVehicleVO toVO(FuelVehicle vehicle) {
        FuelVehicleVO vo = new FuelVehicleVO();
        vo.setId(vehicle.getId());
        vo.setVehicleName(vehicle.getVehicleName());
        vo.setEnergyType(vehicle.getEnergyType());
        vo.setDefaultFuelType(vehicle.getDefaultFuelType());
        vo.setDefaultVehicle(Boolean.TRUE.equals(vehicle.getDefaultVehicle()));
        return vo;
    }

    private record NormalizedFuelVehicle(String vehicleName,
                                         String energyType,
                                         String defaultFuelType,
                                         boolean defaultVehicle) {
    }
}
