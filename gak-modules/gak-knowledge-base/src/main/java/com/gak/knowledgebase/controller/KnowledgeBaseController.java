package com.gak.knowledgebase.controller;

import com.gak.framework.response.ApiResponse;
import com.gak.framework.response.PagedResult;
import com.gak.knowledgebase.dto.KnowledgeEntryQueryRequest;
import com.gak.knowledgebase.dto.KnowledgeHighlightQueryRequest;
import com.gak.knowledgebase.dto.ReviewKnowledgeEntryRequest;
import com.gak.knowledgebase.dto.SaveKnowledgeEntryRequest;
import com.gak.knowledgebase.service.KnowledgeBaseService;
import com.gak.knowledgebase.vo.KnowledgeEntryVO;
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
 * 经验库控制器。
 */
@RestController
@RequestMapping("/knowledge-base")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final TokenService tokenService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService, TokenService tokenService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.tokenService = tokenService;
    }

    @GetMapping("/entries")
    public ApiResponse<PagedResult<KnowledgeEntryVO>> page(@Valid KnowledgeEntryQueryRequest request,
                                                           HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(knowledgeBaseService.page(currentUserId, request));
    }

    @GetMapping("/entries/{id}")
    public ApiResponse<KnowledgeEntryVO> detail(@PathVariable Long id, HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(knowledgeBaseService.detail(currentUserId, id));
    }

    @PostMapping("/entries")
    public ApiResponse<KnowledgeEntryVO> create(@Valid @RequestBody SaveKnowledgeEntryRequest request,
                                                HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(knowledgeBaseService.create(currentUserId, request));
    }

    @PutMapping("/entries/{id}")
    public ApiResponse<KnowledgeEntryVO> update(@PathVariable Long id,
                                                @Valid @RequestBody SaveKnowledgeEntryRequest request,
                                                HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(knowledgeBaseService.update(currentUserId, id, request));
    }

    @DeleteMapping("/entries/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        knowledgeBaseService.delete(currentUserId, id);
        return ApiResponse.success();
    }

    @PutMapping("/entries/{id}/publish")
    public ApiResponse<KnowledgeEntryVO> publish(@PathVariable Long id,
                                                 @Valid @RequestBody ReviewKnowledgeEntryRequest request,
                                                 HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(knowledgeBaseService.publish(currentUserId, id, request));
    }

    @PutMapping("/entries/{id}/reject")
    public ApiResponse<KnowledgeEntryVO> reject(@PathVariable Long id,
                                                @Valid @RequestBody ReviewKnowledgeEntryRequest request,
                                                HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(knowledgeBaseService.reject(currentUserId, id, request));
    }

    @GetMapping("/highlights")
    public ApiResponse<List<KnowledgeEntryVO>> highlights(@Valid KnowledgeHighlightQueryRequest request,
                                                          HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(knowledgeBaseService.highlights(currentUserId, request));
    }

    @GetMapping("/public-highlights")
    public ApiResponse<List<KnowledgeEntryVO>> publicHighlights(@Valid KnowledgeHighlightQueryRequest request) {
        return ApiResponse.success(knowledgeBaseService.publicHighlights(request));
    }
}
