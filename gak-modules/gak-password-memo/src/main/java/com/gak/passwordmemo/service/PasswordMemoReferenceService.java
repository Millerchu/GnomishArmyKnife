package com.gak.passwordmemo.service;
import com.gak.passwordmemo.mapper.PasswordMemoMapper;
import com.gak.passwordmemo.domain.PasswordMemo;
import com.gak.passwordmemo.vo.PasswordMemoReferenceVO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.List;
import org.springframework.stereotype.Service;
/** 只查询必要列的受控引用服务，调用方负责校验功能授权。 */
@Service
public class PasswordMemoReferenceService {
 private final PasswordMemoMapper mapper;
 public PasswordMemoReferenceService(PasswordMemoMapper mapper){this.mapper=mapper;}
 public List<PasswordMemoReferenceVO> list(Long owner) {
  return mapper.selectList(new QueryWrapper<PasswordMemo>().select("id","site_name","username").eq("owner_user_id",owner).orderByDesc("updated_at").orderByDesc("id"))
   .stream().map(m->new PasswordMemoReferenceVO(m.getId(),m.getSiteName(),m.getUsername())).toList();
 }
 public PasswordMemoReferenceVO find(Long owner,Long id) {
  PasswordMemo memo=mapper.selectOne(new QueryWrapper<PasswordMemo>().select("id","site_name","username").eq("owner_user_id",owner).eq("id",id));
  return memo==null?null:new PasswordMemoReferenceVO(memo.getId(),memo.getSiteName(),memo.getUsername());
 }
}
