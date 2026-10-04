package com.gak.travelrecord.controller;

import com.gak.framework.response.*;
import com.gak.personalbills.vo.PersonalBillVO;
import com.gak.travelrecord.dto.*;
import com.gak.travelrecord.service.*;
import com.gak.travelrecord.vo.*;
import com.gak.travelrecord.service.TravelAccessService;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** 当前登录用户的个人行程 API。 */
@RestController
@Validated
@RequestMapping("/travel-records")
public class TravelRecordController {
    private final TravelRecordService service;
    private final TravelBillSyncService bills;
    private final TravelCsvWriter csv;
    private final TravelAccessService tokens;
    public TravelRecordController(TravelRecordService service, TravelBillSyncService bills, TravelCsvWriter csv, TravelAccessService tokens) {
        this.service = service; this.bills = bills; this.csv = csv; this.tokens = tokens;
    }
    @GetMapping
    public ApiResponse<PagedResult<TravelRecordVO>> page(@Valid TravelRecordQueryRequest query, HttpServletRequest request) {
        return ApiResponse.success(service.page(tokens.requireCurrentUserId(request), query));
    }
    @GetMapping("/{id}")
    public ApiResponse<TravelRecordVO> detail(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.success(service.detail(tokens.requireCurrentUserId(request), id));
    }
    @PostMapping
    public ApiResponse<TravelRecordVO> create(@Valid @RequestBody SaveTravelRecordRequest body, HttpServletRequest request) {
        Long owner = tokens.requireCurrentUserId(request);
        requireBillAccess(owner, body);
        return ApiResponse.success(service.create(owner, body));
    }
    @PutMapping("/{id}")
    public ApiResponse<TravelRecordVO> update(@PathVariable Long id, @Valid @RequestBody SaveTravelRecordRequest body, HttpServletRequest request) {
        Long owner = tokens.requireCurrentUserId(request);
        requireBillAccess(owner, body);
        return ApiResponse.success(service.update(owner, id, body));
    }
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean deleteBills, HttpServletRequest request) {
        service.delete(tokens.requireCurrentUserId(request), id, deleteBills);
        return ApiResponse.success();
    }
    @GetMapping("/summary")
    public ApiResponse<TravelSummaryVO> summary(@Valid TravelRecordQueryRequest query, HttpServletRequest request) {
        return ApiResponse.success(service.summary(tokens.requireCurrentUserId(request), query));
    }
    @GetMapping("/bill-candidates")
    public ApiResponse<List<PersonalBillVO>> candidates(@RequestParam(defaultValue = "CNY") @Pattern(regexp = "[A-Z]{3}") String currency,
            @RequestParam(required = false) @Size(max = 96) String keyword,
            @RequestParam(required = false) java.time.LocalDate paymentDate,
            @RequestParam(required = false) java.math.BigDecimal amount, HttpServletRequest request) {
        Long owner = tokens.requireCurrentUserId(request);
        tokens.requireBillAccess(owner);
        return ApiResponse.success(bills.candidates(owner, currency, keyword, paymentDate, amount));
    }
    private void requireBillAccess(Long owner, SaveTravelRecordRequest body) {
        if (Boolean.TRUE.equals(body.getSyncBill()) && body.getPaidAmount() != null && body.getPaidAmount().signum() > 0) {
            tokens.requireBillAccess(owner);
        }
    }
    @GetMapping("/export")
    public void export(@Valid TravelRecordQueryRequest query, HttpServletRequest request, HttpServletResponse response) throws IOException {
        Long owner = tokens.requireCurrentUserId(request);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=travel-records.csv");
        csv.write(response.getWriter(), service.exportRecords(owner, query));
    }
}
