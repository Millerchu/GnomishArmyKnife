package com.gak.datadictionary.controller;

import com.gak.datadictionary.dto.DataDictionaryQueryRequest;
import com.gak.datadictionary.dto.DictionaryUsageOptionQueryRequest;
import com.gak.datadictionary.dto.SaveDataDictionaryItemRequest;
import com.gak.datadictionary.dto.SaveDataDictionaryRequest;
import com.gak.datadictionary.dto.UpdateDataDictionaryItemStatusRequest;
import com.gak.datadictionary.dto.UpdateDataDictionaryStatusRequest;
import com.gak.datadictionary.service.DataDictionaryService;
import com.gak.datadictionary.vo.DataDictionaryItemListVO;
import com.gak.datadictionary.vo.DataDictionaryItemVO;
import com.gak.datadictionary.vo.DataDictionaryVO;
import com.gak.framework.dictionary.DataDictionarySupport;
import com.gak.framework.dictionary.DataDictionaryUsageSupport;
import com.gak.framework.dictionary.vo.DictionaryOptionVO;
import com.gak.framework.response.ApiResponse;
import com.gak.framework.response.PagedResult;
import com.gak.user.service.user.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据字典控制器。
 */
@RestController
@RequestMapping("/system/dictionaries")
public class DataDictionaryController {

    private final DataDictionaryService dataDictionaryService;
    private final DataDictionarySupport dataDictionarySupport;
    private final DataDictionaryUsageSupport dataDictionaryUsageSupport;
    private final TokenService tokenService;

    public DataDictionaryController(DataDictionaryService dataDictionaryService,
                                    DataDictionarySupport dataDictionarySupport,
                                    DataDictionaryUsageSupport dataDictionaryUsageSupport,
                                    TokenService tokenService) {
        this.dataDictionaryService = dataDictionaryService;
        this.dataDictionarySupport = dataDictionarySupport;
        this.dataDictionaryUsageSupport = dataDictionaryUsageSupport;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ApiResponse<PagedResult<DataDictionaryVO>> page(@Valid DataDictionaryQueryRequest request,
                                                           HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(dataDictionaryService.page(currentUserId, request));
    }

    @PostMapping
    public ApiResponse<DataDictionaryVO> create(@Valid @RequestBody SaveDataDictionaryRequest request,
                                                HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(dataDictionaryService.create(currentUserId, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<DataDictionaryVO> update(@PathVariable Long id,
                                                @Valid @RequestBody SaveDataDictionaryRequest request,
                                                HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(dataDictionaryService.update(currentUserId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        dataDictionaryService.delete(currentUserId, id);
        return ApiResponse.success();
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<DataDictionaryVO> updateStatus(@PathVariable Long id,
                                                      @Valid @RequestBody UpdateDataDictionaryStatusRequest request,
                                                      HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(dataDictionaryService.updateStatus(currentUserId, id, request));
    }

    @GetMapping("/options/{dictCode}")
    public ApiResponse<List<DictionaryOptionVO>> listOptions(@PathVariable String dictCode,
                                                             HttpServletRequest httpServletRequest) {
        tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(dataDictionarySupport.listEnabledOptions(dictCode));
    }

    @GetMapping("/options/by-usage")
    public ApiResponse<List<DictionaryOptionVO>> listOptionsByUsage(@Valid DictionaryUsageOptionQueryRequest request,
                                                                    HttpServletRequest httpServletRequest) {
        tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(dataDictionaryUsageSupport.listEnabledOptionsByUsage(
                request.getAppCode(),
                request.getModuleCode(),
                request.getBizFieldCode()
        ));
    }

    @GetMapping("/{dictionaryId}/items")
    public ApiResponse<DataDictionaryItemListVO> listItems(@PathVariable Long dictionaryId,
                                                           HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(dataDictionaryService.listItems(currentUserId, dictionaryId));
    }

    @PostMapping("/{dictionaryId}/items")
    public ApiResponse<DataDictionaryItemVO> createItem(@PathVariable Long dictionaryId,
                                                        @Valid @RequestBody SaveDataDictionaryItemRequest request,
                                                        HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(dataDictionaryService.createItem(currentUserId, dictionaryId, request));
    }

    @PutMapping("/{dictionaryId}/items/{itemId}")
    public ApiResponse<DataDictionaryItemVO> updateItem(@PathVariable Long dictionaryId,
                                                        @PathVariable Long itemId,
                                                        @Valid @RequestBody SaveDataDictionaryItemRequest request,
                                                        HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(dataDictionaryService.updateItem(currentUserId, dictionaryId, itemId, request));
    }

    @DeleteMapping("/{dictionaryId}/items/{itemId}")
    public ApiResponse<Void> deleteItem(@PathVariable Long dictionaryId,
                                        @PathVariable Long itemId,
                                        HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        dataDictionaryService.deleteItem(currentUserId, dictionaryId, itemId);
        return ApiResponse.success();
    }

    @PatchMapping("/{dictionaryId}/items/{itemId}/status")
    public ApiResponse<DataDictionaryItemVO> updateItemStatus(@PathVariable Long dictionaryId,
                                                              @PathVariable Long itemId,
                                                              @Valid @RequestBody UpdateDataDictionaryItemStatusRequest request,
                                                              HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(dataDictionaryService.updateItemStatus(currentUserId, dictionaryId, itemId, request));
    }
}
