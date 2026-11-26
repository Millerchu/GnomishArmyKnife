package com.gak.personalbills.service;

import com.gak.personalbills.mapper.PersonalBankCardMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 校验个人账单应用授权。 */
@Service
public class PersonalBillAccessService {
    private final PersonalBankCardMapper mapper;

    public PersonalBillAccessService(PersonalBankCardMapper mapper) {
        this.mapper = mapper;
    }

    public void requireAccess(Long owner) {
        if (!mapper.hasBillAccess(owner)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "未获得个人账单权限");
        }
    }
}
