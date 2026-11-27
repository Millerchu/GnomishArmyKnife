package com.gak.healthrecord.controller;

import com.gak.framework.security.RequireAppPermission;
import com.gak.framework.response.ApiResponse;
import com.gak.healthrecord.dto.SaveDentalCleaningRequest;
import com.gak.healthrecord.dto.SaveDentalReminderRequest;
import com.gak.healthrecord.service.DentalCleaningService;
import com.gak.healthrecord.vo.DentalCleaningOverviewVO;
import com.gak.healthrecord.vo.DentalCleaningVO;
import com.gak.healthrecord.vo.DentalReminderVO;
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

/** 健康应用的洁牙记录及个人提醒设置。 */
@RestController
@RequireAppPermission("APP_HEALTH_RECORD")
@RequestMapping("/health-records/dental-cleanings")
public class DentalCleaningController {
    private final DentalCleaningService service;
    private final TokenService tokens;

    public DentalCleaningController(DentalCleaningService service, TokenService tokens) {
        this.service = service;
        this.tokens = tokens;
    }

    @GetMapping
    public ApiResponse<DentalCleaningOverviewVO> overview(HttpServletRequest request) {
        return ApiResponse.success(service.overview(tokens.requireCurrentUserId(request)));
    }

    @PostMapping
    public ApiResponse<DentalCleaningVO> create(@Valid @RequestBody SaveDentalCleaningRequest body,
                                               HttpServletRequest request) {
        return ApiResponse.success(service.saveRecord(tokens.requireCurrentUserId(request), null, body));
    }

    @PutMapping("/{id}")
    public ApiResponse<DentalCleaningVO> update(@PathVariable Long id, @Valid @RequestBody SaveDentalCleaningRequest body,
                                               HttpServletRequest request) {
        return ApiResponse.success(service.saveRecord(tokens.requireCurrentUserId(request), id, body));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        service.deleteRecord(tokens.requireCurrentUserId(request), id);
        return ApiResponse.success();
    }

    @PutMapping("/reminder")
    public ApiResponse<DentalReminderVO> saveReminder(@Valid @RequestBody SaveDentalReminderRequest body,
                                                     HttpServletRequest request) {
        return ApiResponse.success(service.saveReminder(tokens.requireCurrentUserId(request), body));
    }
}
