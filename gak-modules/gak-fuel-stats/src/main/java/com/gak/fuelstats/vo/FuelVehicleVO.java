package com.gak.fuelstats.vo;

/**
 * 车辆档案展示对象。
 */
public class FuelVehicleVO {

    private Long id;
    private String vehicleName;
    private String energyType;
    private String defaultFuelType;
    private Boolean defaultVehicle;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getVehicleName() { return vehicleName; }
    public void setVehicleName(String vehicleName) { this.vehicleName = vehicleName; }
    public String getEnergyType() { return energyType; }
    public void setEnergyType(String energyType) { this.energyType = energyType; }
    public String getDefaultFuelType() { return defaultFuelType; }
    public void setDefaultFuelType(String defaultFuelType) { this.defaultFuelType = defaultFuelType; }
    public Boolean getDefaultVehicle() { return defaultVehicle; }
    public void setDefaultVehicle(Boolean defaultVehicle) { this.defaultVehicle = defaultVehicle; }
}
