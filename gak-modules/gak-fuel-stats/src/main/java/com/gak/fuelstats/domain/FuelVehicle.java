package com.gak.fuelstats.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 用户维护的车辆档案，用于能源记录快速选择。
 */
@TableName("gak_fuel_vehicle")
public class FuelVehicle {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long ownerUserId;
    private String vehicleName;
    private String energyType;
    private String defaultFuelType;
    private Boolean defaultVehicle;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getVehicleName() { return vehicleName; }
    public void setVehicleName(String vehicleName) { this.vehicleName = vehicleName; }
    public String getEnergyType() { return energyType; }
    public void setEnergyType(String energyType) { this.energyType = energyType; }
    public String getDefaultFuelType() { return defaultFuelType; }
    public void setDefaultFuelType(String defaultFuelType) { this.defaultFuelType = defaultFuelType; }
    public Boolean getDefaultVehicle() { return defaultVehicle; }
    public void setDefaultVehicle(Boolean defaultVehicle) { this.defaultVehicle = defaultVehicle; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
