package com.gak.fuelstats.controller;

import com.gak.framework.response.ApiResponse;
import com.gak.framework.response.PagedResult;
import com.gak.fuelstats.dto.FuelRecordQueryRequest;
import com.gak.fuelstats.dto.SaveFuelRecordRequest;
import com.gak.fuelstats.service.FuelRecordService;
import com.gak.fuelstats.vo.FuelLatestPricesVO;
import com.gak.fuelstats.vo.FuelRecordVO;
import com.gak.fuelstats.vo.FuelReportsVO;
import com.gak.fuelstats.vo.FuelSummaryVO;
import com.gak.user.service.user.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 油耗统计控制器。
 */
@RestController
@RequestMapping("/fuel-records")
public class FuelRecordController {

    private final FuelRecordService fuelRecordService;
    private final TokenService tokenService;

    public FuelRecordController(FuelRecordService fuelRecordService, TokenService tokenService) {
        this.fuelRecordService = fuelRecordService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ApiResponse<PagedResult<FuelRecordVO>> page(@Valid FuelRecordQueryRequest request,
                                                       HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(fuelRecordService.page(currentUserId, request));
    }

    @PostMapping
    public ApiResponse<FuelRecordVO> create(@Valid @RequestBody SaveFuelRecordRequest request,
                                            HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(fuelRecordService.create(currentUserId, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<FuelRecordVO> update(@PathVariable Long id,
                                            @Valid @RequestBody SaveFuelRecordRequest request,
                                            HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(fuelRecordService.update(currentUserId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        fuelRecordService.delete(currentUserId, id);
        return ApiResponse.success();
    }

    @GetMapping("/summary")
    public ApiResponse<FuelSummaryVO> summary(HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(fuelRecordService.getSummary(currentUserId));
    }

    @GetMapping("/latest-prices")
    public ApiResponse<FuelLatestPricesVO> latestPrices() {
        return ApiResponse.success(fuelRecordService.getLatestPrices());
    }

    @GetMapping("/reports")
    public ApiResponse<FuelReportsVO> reports(HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(fuelRecordService.getReports(currentUserId));
    }
}
