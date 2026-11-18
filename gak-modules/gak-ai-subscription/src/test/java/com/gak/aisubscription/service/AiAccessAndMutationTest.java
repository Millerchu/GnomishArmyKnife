package com.gak.aisubscription.service;

import com.gak.aisubscription.domain.*;
import com.gak.aisubscription.dto.*;
import com.gak.aisubscription.enums.AiPaymentType;
import com.gak.aisubscription.mapper.*;
import com.gak.aisubscription.sync.AiSyncRepository;
import com.gak.passwordmemo.service.PasswordMemoReferenceService;
import com.gak.user.service.user.TokenService;
import java.math.BigDecimal;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiAccessAndMutationTest {
 @Test void directApiCannotBypassAppPermission(){
  AiAccessMapper mapper=mock(AiAccessMapper.class);TokenService tokens=mock(TokenService.class);MockHttpServletRequest request=new MockHttpServletRequest();when(tokens.requireCurrentUserId(request)).thenReturn(1L);
  assertEquals(403,assertThrows(ResponseStatusException.class,()->new AiAccessService(mapper,tokens).requireCurrentUserId(request)).getStatusCode().value());
 }
 @Test void otherUserAccountIsIndistinguishableFromMissingAccount(){
  AiAccountMapper mapper=mock(AiAccountMapper.class);AiAccount foreign=new AiAccount();foreign.setId(2L);foreign.setOwnerUserId(2L);when(mapper.selectById(2L)).thenReturn(foreign);
  AiAccountService service=accounts(mapper,mock(AiAccessService.class),mock(PasswordMemoReferenceService.class),mock(AiSyncRepository.class));
  assertEquals(404,assertThrows(ResponseStatusException.class,()->service.requireOwned(1L,2L)).getStatusCode().value());
  assertEquals(404,assertThrows(ResponseStatusException.class,()->service.requireOwned(1L,3L)).getStatusCode().value());
 }
 @Test void archivePreservesAccountAndRevokesSync(){
  AiAccountMapper mapper=mock(AiAccountMapper.class);AiSyncRepository sync=mock(AiSyncRepository.class);AiAccount account=new AiAccount();account.setId(2L);account.setOwnerUserId(1L);when(mapper.selectById(2L)).thenReturn(account);
  accounts(mapper,mock(AiAccessService.class),mock(PasswordMemoReferenceService.class),sync).archive(1L,2L);
  assertTrue(account.getArchived());verify(mapper).updateById(account);verify(sync).revoke(1L,2L);verify(mapper,never()).deleteById(anyLong());
 }
 @Test void unavailableMemoCannotBeLinked(){
  AiAccountMapper mapper=mock(AiAccountMapper.class);PasswordMemoReferenceService memos=mock(PasswordMemoReferenceService.class);
  AiAccountService service=accounts(mapper,mock(AiAccessService.class),memos,mock(AiSyncRepository.class));
  assertThrows(ResponseStatusException.class,()->service.save(1L,null,new SaveAiAccountRequest("CHATGPT","Work","",99L)));verify(mapper,never()).insert(any(AiAccount.class));
 }
 @Test void retriedPaymentReturnsOriginalWithoutAnotherInsert(){
  AiPaymentMapper mapper=mock(AiPaymentMapper.class);AiPayment existing=new AiPayment();existing.setId(9L);existing.setAccountId(2L);existing.setType("TOPUP");existing.setAmount(new BigDecimal("10.00"));existing.setCurrency("CNY");existing.setPaymentDate(LocalDate.of(2026,11,1));existing.setRequestKey("same");when(mapper.selectOne(any())).thenReturn(existing);
  AiPaymentService service=new AiPaymentService(mapper,mock(AiAccountService.class),mock(AiSubscriptionService.class),mock(AiAccessService.class));
  SaveAiPaymentRequest request=new SaveAiPaymentRequest(2L,null,AiPaymentType.TOPUP,new BigDecimal("10"),"CNY",existing.getPaymentDate(),null,null,null,"same",null);
  assertEquals(9L,service.create(1L,request).id());verify(mapper,never()).insert(any(AiPayment.class));
  SaveAiPaymentRequest changed=new SaveAiPaymentRequest(2L,null,AiPaymentType.TOPUP,new BigDecimal("11"),"CNY",existing.getPaymentDate(),null,null,null,"same",null);
  assertThrows(ResponseStatusException.class,()->service.create(1L,changed));
 }
 private AiAccountService accounts(AiAccountMapper m,AiAccessService a,PasswordMemoReferenceService p,AiSyncRepository s){return new AiAccountService(m,a,p,s);}
}
