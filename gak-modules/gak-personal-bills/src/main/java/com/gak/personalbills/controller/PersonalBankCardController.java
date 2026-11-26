package com.gak.personalbills.controller;

import com.gak.framework.response.ApiResponse;
import com.gak.personalbills.dto.SaveBankCardRequest;
import com.gak.personalbills.service.PersonalBankCardService;
import com.gak.personalbills.service.PersonalBillAccessService;
import com.gak.personalbills.vo.PersonalBankCardVO;
import com.gak.user.service.user.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理当前用户的银行卡。 */
@RestController
@RequestMapping("/personal-bills/bank-cards")
public class PersonalBankCardController {
    private final PersonalBankCardService service;
    private final PersonalBillAccessService access;
    private final TokenService tokens;

    public PersonalBankCardController(PersonalBankCardService service, PersonalBillAccessService access,
                                      TokenService tokens) {
        this.service = service;
        this.access = access;
        this.tokens = tokens;
    }

    @GetMapping
    public ApiResponse<List<PersonalBankCardVO>> list(@RequestParam(defaultValue = "false") boolean includeArchived,
                                                      HttpServletRequest http) {
        return ApiResponse.success(service.list(requireOwner(http), includeArchived));
    }

    @PostMapping
    public ApiResponse<PersonalBankCardVO> create(@Valid @RequestBody SaveBankCardRequest request,
                                                  HttpServletRequest http) {
        return ApiResponse.success(service.save(requireOwner(http), null, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<PersonalBankCardVO> update(@PathVariable Long id, @Valid @RequestBody SaveBankCardRequest request,
                                                  HttpServletRequest http) {
        return ApiResponse.success(service.save(requireOwner(http), id, request));
    }

    @PatchMapping("/{id}/archive")
    public ApiResponse<PersonalBankCardVO> archive(@PathVariable Long id, @RequestParam boolean archived,
                                                   HttpServletRequest http) {
        return ApiResponse.success(service.setArchived(requireOwner(http), id, archived));
    }

    private Long requireOwner(HttpServletRequest http) {
        Long owner = tokens.requireCurrentUserId(http);
        access.requireAccess(owner);
        return owner;
    }
}
