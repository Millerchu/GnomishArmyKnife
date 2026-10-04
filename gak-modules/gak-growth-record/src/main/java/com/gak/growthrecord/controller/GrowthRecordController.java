package com.gak.growthrecord.controller;

import com.gak.framework.response.ApiResponse;
import com.gak.growthrecord.dto.SaveGrowthChildRequest;
import com.gak.growthrecord.dto.SaveGrowthMeasurementRequest;
import com.gak.growthrecord.dto.SaveGrowthVaccinationRequest;
import com.gak.growthrecord.dto.SaveGrowthMilestoneRequest;
import com.gak.growthrecord.vo.GrowthChildVO;
import com.gak.growthrecord.vo.GrowthMeasurementVO;
import com.gak.growthrecord.vo.GrowthVaccinationVO;
import com.gak.growthrecord.vo.GrowthMilestoneVO;
import com.gak.growthrecord.service.GrowthAccessService;
import com.gak.growthrecord.service.GrowthRecordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

/** 芽记 API：登录与应用授权一致应用于所有读写接口。 */
@RestController
@RequestMapping("/growth-records")
public class GrowthRecordController {
    private final GrowthRecordService service;
    private final GrowthAccessService access;

    public GrowthRecordController(GrowthRecordService service, GrowthAccessService access) {
        this.service = service;
        this.access = access;
    }

    @GetMapping("/children")
    public ApiResponse<List<GrowthChildVO>> listChildren(HttpServletRequest http) {
        return ApiResponse.success(service.listChildren(access.requireCurrentUserId(http)));
    }

    @PostMapping("/children")
    public ApiResponse<GrowthChildVO> createChild(@Valid @RequestBody SaveGrowthChildRequest request, HttpServletRequest http) {
        return ApiResponse.success(service.createChild(access.requireCurrentUserId(http), request));
    }

    @PutMapping("/children/{childId}")
    public ApiResponse<GrowthChildVO> updateChild(@PathVariable Long childId,
            @Valid @RequestBody SaveGrowthChildRequest request, HttpServletRequest http) {
        return ApiResponse.success(service.updateChild(access.requireCurrentUserId(http), childId, request));
    }

    @DeleteMapping("/children/{childId}")
    public ApiResponse<Void> deleteChild(@PathVariable Long childId, HttpServletRequest http) {
        service.deleteChild(access.requireCurrentUserId(http), childId);
        return ApiResponse.success();
    }

    @GetMapping("/children/{childId}/measurements")
    public ApiResponse<List<GrowthMeasurementVO>> listMeasurements(@PathVariable Long childId, HttpServletRequest http) {
        return ApiResponse.success(service.listMeasurements(access.requireCurrentUserId(http), childId));
    }

    @PostMapping("/children/{childId}/measurements")
    public ApiResponse<GrowthMeasurementVO> createMeasurement(@PathVariable Long childId,
            @Valid @RequestBody SaveGrowthMeasurementRequest request, HttpServletRequest http) {
        return ApiResponse.success(service.createMeasurement(access.requireCurrentUserId(http), childId, request));
    }

    @PutMapping("/children/{childId}/measurements/{id}")
    public ApiResponse<GrowthMeasurementVO> updateMeasurement(@PathVariable Long childId, @PathVariable Long id,
            @Valid @RequestBody SaveGrowthMeasurementRequest request, HttpServletRequest http) {
        return ApiResponse.success(service.updateMeasurement(access.requireCurrentUserId(http), childId, id, request));
    }

    @DeleteMapping("/children/{childId}/measurements/{id}")
    public ApiResponse<Void> deleteMeasurement(@PathVariable Long childId, @PathVariable Long id, HttpServletRequest http) {
        service.deleteMeasurement(access.requireCurrentUserId(http), childId, id);
        return ApiResponse.success();
    }

    @GetMapping("/children/{childId}/vaccinations")
    public ApiResponse<List<GrowthVaccinationVO>> listVaccinations(@PathVariable Long childId, HttpServletRequest http) {
        return ApiResponse.success(service.listVaccinations(access.requireCurrentUserId(http), childId));
    }

    @PostMapping("/children/{childId}/vaccinations")
    public ApiResponse<GrowthVaccinationVO> createVaccination(@PathVariable Long childId,
            @Valid @RequestBody SaveGrowthVaccinationRequest request, HttpServletRequest http) {
        return ApiResponse.success(service.createVaccination(access.requireCurrentUserId(http), childId, request));
    }

    @PutMapping("/children/{childId}/vaccinations/{id}")
    public ApiResponse<GrowthVaccinationVO> updateVaccination(@PathVariable Long childId, @PathVariable Long id,
            @Valid @RequestBody SaveGrowthVaccinationRequest request, HttpServletRequest http) {
        return ApiResponse.success(service.updateVaccination(access.requireCurrentUserId(http), childId, id, request));
    }

    @DeleteMapping("/children/{childId}/vaccinations/{id}")
    public ApiResponse<Void> deleteVaccination(@PathVariable Long childId, @PathVariable Long id, HttpServletRequest http) {
        service.deleteVaccination(access.requireCurrentUserId(http), childId, id);
        return ApiResponse.success();
    }

    @GetMapping("/children/{childId}/milestones")
    public ApiResponse<List<GrowthMilestoneVO>> listMilestones(@PathVariable Long childId, HttpServletRequest http) {
        return ApiResponse.success(service.listMilestones(access.requireCurrentUserId(http), childId));
    }

    @PostMapping("/children/{childId}/milestones")
    public ApiResponse<GrowthMilestoneVO> createMilestone(@PathVariable Long childId,
            @Valid @RequestBody SaveGrowthMilestoneRequest request, HttpServletRequest http) {
        return ApiResponse.success(service.createMilestone(access.requireCurrentUserId(http), childId, request));
    }

    @PutMapping("/children/{childId}/milestones/{id}")
    public ApiResponse<GrowthMilestoneVO> updateMilestone(@PathVariable Long childId, @PathVariable Long id,
            @Valid @RequestBody SaveGrowthMilestoneRequest request, HttpServletRequest http) {
        return ApiResponse.success(service.updateMilestone(access.requireCurrentUserId(http), childId, id, request));
    }

    @DeleteMapping("/children/{childId}/milestones/{id}")
    public ApiResponse<Void> deleteMilestone(@PathVariable Long childId, @PathVariable Long id, HttpServletRequest http) {
        service.deleteMilestone(access.requireCurrentUserId(http), childId, id);
        return ApiResponse.success();
    }
}


