package com.gak.fuelstats.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 保存车辆档案请求。
 */
public class SaveFuelVehicleRequest {

    @NotBlank(message = "vehicleName 不能为空")
    @Size(max = 64, message = "vehicleName 长度不能超过 64")
    private String vehicleName;

    @NotBlank(message = "energyType 不能为空")
    @Size(max = 16, message = "energyType 长度不能超过 16")
    private String energyType;

    @NotBlank(message = "defaultFuelType 不能为空")
    @Size(max = 16, message = "defaultFuelType 长度不能超过 16")
    private String defaultFuelType;

    @NotNull(message = "defaultVehicle 不能为空")
    private Boolean defaultVehicle;

    public String getVehicleName() { return vehicleName; }
    public void setVehicleName(String vehicleName) { this.vehicleName = vehicleName; }
    public String getEnergyType() { return energyType; }
    public void setEnergyType(String energyType) { this.energyType = energyType; }
    public String getDefaultFuelType() { return defaultFuelType; }
    public void setDefaultFuelType(String defaultFuelType) { this.defaultFuelType = defaultFuelType; }
    public Boolean getDefaultVehicle() { return defaultVehicle; }
    public void setDefaultVehicle(Boolean defaultVehicle) { this.defaultVehicle = defaultVehicle; }
}
