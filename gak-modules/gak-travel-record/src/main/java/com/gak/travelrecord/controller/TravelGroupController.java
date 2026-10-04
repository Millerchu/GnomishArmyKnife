package com.gak.travelrecord.controller;

import com.gak.framework.response.ApiResponse;
import com.gak.travelrecord.dto.SaveTravelGroupRequest;
import com.gak.travelrecord.service.TravelGroupService;
import com.gak.travelrecord.vo.TravelGroupVO;
import com.gak.travelrecord.service.TravelAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

/** 可选出行分组 API。 */
@RestController
@RequestMapping("/travel-groups")
public class TravelGroupController {
    private final TravelGroupService service;
    private final TravelAccessService tokens;
    public TravelGroupController(TravelGroupService service, TravelAccessService tokens) { this.service = service; this.tokens = tokens; }
    @GetMapping
    public ApiResponse<List<TravelGroupVO>> list(HttpServletRequest request) {
        return ApiResponse.success(service.list(tokens.requireCurrentUserId(request)));
    }
    @PostMapping
    public ApiResponse<TravelGroupVO> create(@Valid @RequestBody SaveTravelGroupRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.save(tokens.requireCurrentUserId(request), null, body));
    }
    @PutMapping("/{id}")
    public ApiResponse<TravelGroupVO> update(@PathVariable Long id, @Valid @RequestBody SaveTravelGroupRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.save(tokens.requireCurrentUserId(request), id, body));
    }
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        service.delete(tokens.requireCurrentUserId(request), id);
        return ApiResponse.success();
    }
}
