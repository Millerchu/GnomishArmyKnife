package com.gak.fuelstats.controller;

import com.gak.framework.response.ApiResponse;
import com.gak.fuelstats.dto.SaveFuelVehicleRequest;
import com.gak.fuelstats.service.FuelVehicleService;
import com.gak.fuelstats.vo.FuelVehicleVO;
import com.gak.user.service.user.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户车辆档案控制器。
 */
@RestController
@RequestMapping("/fuel-vehicles")
public class FuelVehicleController {

    private final FuelVehicleService fuelVehicleService;
    private final TokenService tokenService;

    public FuelVehicleController(FuelVehicleService fuelVehicleService, TokenService tokenService) {
        this.fuelVehicleService = fuelVehicleService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ApiResponse<List<FuelVehicleVO>> list(HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(fuelVehicleService.list(currentUserId));
    }

    @PostMapping
    public ApiResponse<FuelVehicleVO> create(@Valid @RequestBody SaveFuelVehicleRequest request,
                                             HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(fuelVehicleService.create(currentUserId, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<FuelVehicleVO> update(@PathVariable Long id,
                                             @Valid @RequestBody SaveFuelVehicleRequest request,
                                             HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(fuelVehicleService.update(currentUserId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        fuelVehicleService.delete(currentUserId, id);
        return ApiResponse.success();
    }
}
