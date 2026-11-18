package com.gak.aisubscription.service;
import com.gak.aisubscription.domain.AiAccount;
import com.gak.aisubscription.sync.AiSyncRepository;
import com.gak.aisubscription.dto.SaveAiAccountRequest;
import com.gak.aisubscription.mapper.AiAccountMapper;
import com.gak.aisubscription.vo.*;
import com.gak.passwordmemo.service.PasswordMemoReferenceService;
import com.gak.passwordmemo.vo.PasswordMemoReferenceVO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
/** 用户账号与安全备忘录引用；删除仅归档历史。 */
@Service
public class AiAccountService {
 private static final String MEMO_APP="APP_PASSWORD_MEMO";
 private final AiSyncRepository sync;
 private final AiAccountMapper mapper;
 private final AiAccessService access;
 private final PasswordMemoReferenceService memos;
 public AiAccountService(AiAccountMapper mapper,AiAccessService access,PasswordMemoReferenceService memos,AiSyncRepository sync){this.sync=sync;this.mapper=mapper;this.access=access;this.memos=memos;}
 public List<AiAccountVO> list(Long owner){return mapper.selectList(new QueryWrapper<AiAccount>().eq("owner_user_id",owner).orderByDesc("recorded_at").orderByDesc("id")).stream().map(a->toVO(owner,a)).toList();}
 public AiAccount requireOwned(Long owner,Long id){AiAccount a=mapper.selectById(id);if(a==null||!Objects.equals(owner,a.getOwnerUserId()))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"账号不可用");return a;}
 public AiAccount requireActive(Long owner,Long id){AiAccount a=requireOwned(owner,id);AiRules.check(!Boolean.TRUE.equals(a.getArchived()),"账号已归档");return a;}
 @Transactional public AiAccountVO save(Long owner,Long id,SaveAiAccountRequest request){
  access.lockOwner(owner);AiCatalog.requireTool(request.toolCode());
  AiAccount a=id==null?new AiAccount():requireActive(owner,id);
  if(request.passwordMemoId()!=null){access.requireApp(owner,MEMO_APP);AiRules.check(memos.find(owner,request.passwordMemoId())!=null,"关联不可用");}
  if(id!=null)AiRules.check(a.getToolCode().equals(request.toolCode()),"已有账号不可更换工具，请新增账号");
  a.setOwnerUserId(owner);a.setToolCode(request.toolCode());a.setAlias(request.alias().trim());a.setNote(request.note());a.setPasswordMemoId(request.passwordMemoId());a.setArchived(false);a.setRecordedAt(LocalDateTime.now());
  if(id==null)mapper.insert(a);else mapper.updateById(a);return toVO(owner,a);
 }
 @Transactional public void archive(Long owner,Long id){access.lockOwner(owner);AiAccount a=requireOwned(owner,id);a.setArchived(true);mapper.updateById(a);sync.revoke(owner,id);}
 public List<AiMemoOptionVO> memoOptions(Long owner){access.requireApp(owner,MEMO_APP);return memos.list(owner).stream().map(m->new AiMemoOptionVO(m.id(),summary(m))).toList();}
 private AiAccountVO toVO(Long owner,AiAccount a){
  PasswordMemoReferenceVO memo=a.getPasswordMemoId()!=null&&access.hasApp(owner,MEMO_APP)?memos.find(owner,a.getPasswordMemoId()):null;
  return new AiAccountVO(a.getId(),a.getToolCode(),a.getAlias(),a.getNote(),a.getPasswordMemoId(),a.getArchived(),a.getRecordedAt(),memo!=null,memo==null?null:summary(memo));
 }
 private String summary(PasswordMemoReferenceVO m){String username=m.username();return m.siteName()+(username==null||username.isBlank()?"":" · "+username.substring(0,1)+"***");}
}
