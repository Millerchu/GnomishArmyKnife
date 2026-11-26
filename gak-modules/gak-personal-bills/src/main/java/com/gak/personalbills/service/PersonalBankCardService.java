package com.gak.personalbills.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gak.framework.exception.BusinessException;
import com.gak.personalbills.domain.BankCardType;
import com.gak.personalbills.domain.PersonalBankCard;
import com.gak.personalbills.dto.SaveBankCardRequest;
import com.gak.personalbills.mapper.PersonalBankCardMapper;
import com.gak.personalbills.mapper.PersonalCreditAccountMapper;
import com.gak.personalbills.vo.PersonalBankCardVO;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 维护单张卡片及其授信组归属。 */
@Service
public class PersonalBankCardService {
    private final PersonalBankCardMapper cards;
    private final PersonalCreditAccountMapper creditAccounts;

    public PersonalBankCardService(PersonalBankCardMapper cards, PersonalCreditAccountMapper creditAccounts) {
        this.cards = cards;
        this.creditAccounts = creditAccounts;
    }

    public List<PersonalBankCardVO> list(Long owner, boolean includeArchived) {
        return cards.selectList(new LambdaQueryWrapper<PersonalBankCard>()
                        .eq(PersonalBankCard::getOwnerUserId, owner)
                        .eq(!includeArchived, PersonalBankCard::isArchived, false)
                        .orderByAsc(PersonalBankCard::isArchived, PersonalBankCard::getCardType,
                                PersonalBankCard::getCardName, PersonalBankCard::getId))
                .stream().map(this::toView).toList();
    }

    @Transactional
    public PersonalBankCardVO save(Long owner, Long id, SaveBankCardRequest request) {
        PersonalBankCard card = id == null ? new PersonalBankCard() : requireOwnedForUpdate(owner, id);
        validateCreditAccount(owner, request);
        LocalDateTime now = LocalDateTime.now();
        card.setOwnerUserId(owner);
        card.setCardType(request.cardType());
        card.setCardName(request.cardName().trim());
        card.setInstitution(request.institution().trim());
        card.setLastFour(request.lastFour());
        card.setCreditAccountId(request.creditAccountId());
        card.setNote(request.note() == null || request.note().isBlank() ? null : request.note().trim());
        card.setUpdatedAt(now);
        if (id == null) {
            card.setCreatedAt(now);
            cards.insert(card);
        } else {
            cards.updateById(card);
        }
        return toView(card);
    }

    @Transactional
    public PersonalBankCardVO setArchived(Long owner, Long id, boolean archived) {
        PersonalBankCard card = requireOwnedForUpdate(owner, id);
        if (card.isArchived() != archived) {
            card.setArchived(archived);
            card.setUpdatedAt(LocalDateTime.now());
            cards.updateById(card);
        }
        return toView(card);
    }

    /** 锁定卡片直到账单落库，避免选卡校验与归档交错。 */
    public String snapshotForSelection(Long owner, Long id) {
        PersonalBankCard card = requireOwnedForUpdate(owner, id);
        if (card.isArchived()) {
            throw new BusinessException("PERSONAL_BILL_CARD_ARCHIVED", "银行卡已归档，请重新选择资金账户");
        }
        return card.getLastFour() == null ? card.getCardName() : card.getCardName() + "（尾号" + card.getLastFour() + "）";
    }

    private PersonalBankCard requireOwnedForUpdate(Long owner, Long id) {
        PersonalBankCard card = cards.selectOwnedForUpdate(id, owner);
        if (card == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "银行卡不存在");
        }
        return card;
    }

    private void validateCreditAccount(Long owner, SaveBankCardRequest request) {
        if (request.creditAccountId() == null) {
            return;
        }
        if (request.cardType() == BankCardType.DEBIT) {
            throw new BusinessException("PERSONAL_BILL_DEBIT_CARD_CREDIT_ACCOUNT", "储蓄卡不能关联授信组");
        }
        if (creditAccounts.selectOwnedForUpdate(request.creditAccountId(), owner) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "授信组不存在");
        }
    }

    private PersonalBankCardVO toView(PersonalBankCard card) {
        return new PersonalBankCardVO(card.getId(), card.getCardType(), card.getCardName(), card.getInstitution(),
                card.getLastFour(), card.getCreditAccountId(), card.getNote(), card.isArchived());
    }
}
