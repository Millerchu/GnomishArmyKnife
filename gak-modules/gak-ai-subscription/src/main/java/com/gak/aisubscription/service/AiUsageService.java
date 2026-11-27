package com.gak.aisubscription.service;
import com.gak.aisubscription.domain.AiUsageRecord;
import com.gak.aisubscription.sync.AiOfficialCost;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.gak.aisubscription.dto.SaveAiUsageRequest;
import com.gak.aisubscription.enums.AiUsageKind;
import com.gak.aisubscription.mapper.AiUsageRecordMapper;
import com.gak.aisubscription.vo.AiUsageRecordVO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/** 原始观测保留；累计取最新，部分重叠整条待核对，禁止按时间比例拆分数量。 */
@Service
public class AiUsageService {
 private static final String INCLUDED="INCLUDED", REVIEW="REVIEW", SUPERSEDED="SUPERSEDED", OFFICIAL="OFFICIAL", MANUAL="MANUAL", CUMULATIVE="CUMULATIVE";
 private final AiUsageRecordMapper mapper;private final AiAccountService accounts;private final AiAccessService access;
 public AiUsageService(AiUsageRecordMapper mapper,AiAccountService accounts,AiAccessService access){this.mapper=mapper;this.accounts=accounts;this.access=access;}
 public List<AiUsageRecord> records(Long owner){return mapper.selectList(new QueryWrapper<AiUsageRecord>().eq("owner_user_id",owner).orderByDesc("recorded_at").orderByDesc("id"));}
 public List<AiUsageRecordVO> list(Long owner){return records(owner).stream().map(this::toVO).toList();}
 @Transactional public AiUsageRecordVO create(Long owner,SaveAiUsageRequest r){
  access.lockOwner(owner);accounts.requireActive(owner,r.accountId());AiRules.interval(r.startDate(),r.endDate());AiRules.zone(r.zoneId());
  AiRules.check(r.kind()!=AiUsageKind.PACKAGE||r.cost()==null||r.cost().signum()==0,"套餐内用量不额外计费；超额费用请单独记录 API 费用");
  AiUsageRecord u=new AiUsageRecord();u.setOwnerUserId(owner);u.setAccountId(r.accountId());u.setKind(r.kind().name());u.setMode(r.mode().name());u.setPeriodKey(r.periodKey().trim());u.setZoneId(r.zoneId());u.setStartDate(r.startDate());u.setEndDate(r.endDate());u.setMetric(r.metric().trim());u.setUnit(r.unit().trim());u.setDimension(r.dimension()==null?"":r.dimension().trim());u.setQuantity(r.quantity());u.setCost(r.cost());u.setCurrency(r.currency());u.setResolution("NORMAL");u.setSource(MANUAL);u.setStatus(INCLUDED);u.setReason("");u.setRecordedAt(LocalDateTime.now());
  mapper.insert(u);reconcile(owner);return toVO(mapper.selectById(u.getId()));
 }
 /** 在同步事务内替换完整回查窗口，仅在全部桶写入后重新归类。 */
 @Transactional
 public void replaceOfficialCostWindow(Long owner, Long accountId, LocalDate start, LocalDate end,
                                       String prefix, List<AiOfficialCost> costs) {
  access.lockOwner(owner);
  accounts.requireActive(owner, accountId);
  AiRules.interval(start,end);
  List<AiUsageRecord> previous = mapper.selectList(new QueryWrapper<AiUsageRecord>()
          .eq("owner_user_id",owner).eq("account_id",accountId).eq("source",OFFICIAL)
          .ge("start_date",start).le("end_date",end));
  Map<String, AiUsageRecord> byKey = previous.stream()
          .collect(Collectors.toMap(AiUsageRecord::getExternalKey, Function.identity()));
  for (AiOfficialCost cost : costs) {
   AiRules.interval(cost.startDate(),cost.endDate());
   AiRules.check(!cost.startDate().isBefore(start) && !cost.endDate().isAfter(end)
           && cost.amount() != null && cost.amount().signum() >= 0, "官方费用超出回查窗口或金额非法");
   String key = prefix + cost.startDate() + ":" + cost.currency();
   AiUsageRecord record = byKey.remove(key);
   if (record == null) {
    record = new AiUsageRecord();
    record.setOwnerUserId(owner);
    record.setAccountId(accountId);
    record.setKind("API");
    record.setMode(CUMULATIVE);
    record.setPeriodKey(key);
    record.setZoneId("UTC");
    record.setStartDate(cost.startDate());
    record.setMetric("COST");
    record.setUnit(cost.currency());
    record.setDimension("");
    record.setQuantity(BigDecimal.ZERO);
    record.setCurrency(cost.currency());
    record.setResolution("NORMAL");
    record.setSource(OFFICIAL);
    record.setExternalKey(key);
   } else if (record.getCost() != null && record.getCost().compareTo(cost.amount()) == 0
           && Objects.equals(record.getEndDate(), cost.endDate()) && !"WITHDRAWN".equals(record.getStatus())) {
    continue;
   }
   record.setEndDate(cost.endDate());
   record.setCost(cost.amount());
   record.setStatus(INCLUDED);
   record.setReason("");
   record.setRecordedAt(LocalDateTime.now());
   if (record.getId() == null) {
    mapper.insert(record);
   } else {
    mapper.updateById(record);
   }
  }
  for (AiUsageRecord withdrawn : byKey.values()) {
   if (!"WITHDRAWN".equals(withdrawn.getStatus())) {
    withdrawn.setStatus("WITHDRAWN");
    withdrawn.setReason("官方回查窗口已撤回该费用桶");
    mapper.updateById(withdrawn);
   }
  }
  reconcile(owner);
 }
 @Transactional public AiUsageRecordVO resolve(Long owner,Long id,com.gak.aisubscription.enums.AiUsageResolution action){
  access.lockOwner(owner);AiUsageRecord record=mapper.selectById(id);
  if(record==null||!Objects.equals(record.getOwnerUserId(),owner))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"用量记录不可用");
  AiRules.check(MANUAL.equals(record.getSource()),"官方数据请通过重新同步修正");record.setResolution(action.name());mapper.updateById(record);reconcile(owner);return toVO(mapper.selectById(id));
 }
 private void reconcile(Long owner){
  List<AiUsageRecord> records = records(owner);
  List<String> statuses = records.stream().map(AiUsageRecord::getStatus).toList();
  List<String> reasons = records.stream().map(AiUsageRecord::getReason).toList();
  classify(records);
  for (int index = 0; index < records.size(); index++) {
   AiUsageRecord record = records.get(index);
   if (!Objects.equals(statuses.get(index), record.getStatus())
           || !Objects.equals(reasons.get(index), record.getReason())) {
    mapper.update(null, new UpdateWrapper<AiUsageRecord>()
            .eq("id", record.getId()).eq("owner_user_id", owner)
            .set("status", record.getStatus()).set("reason", record.getReason()));
   }
  }
 }
 /** 列表必须按观测时间、ID 降序排列；测试独立验证计量规则。 */
 public static void classify(List<AiUsageRecord> records){
  for(AiUsageRecord u:records){if(!"WITHDRAWN".equals(u.getStatus())){u.setStatus("EXCLUDE".equals(u.getResolution())?"EXCLUDED":INCLUDED);u.setReason("EXCLUDE".equals(u.getResolution())?"用户已排除原始观测":"");}}
  for(int i=0;i<records.size();i++){
   AiUsageRecord older=records.get(i);if("EXCLUDED".equals(older.getStatus())||"WITHDRAWN".equals(older.getStatus())||!CUMULATIVE.equals(older.getMode()))continue;
   for(int j=0;j<i;j++)if(!"EXCLUDED".equals(records.get(j).getStatus())&&!"WITHDRAWN".equals(records.get(j).getStatus())&&sameSnapshot(older,records.get(j))){older.setStatus(SUPERSEDED);older.setReason("同周期累计已被较新观测覆盖");break;}
  }
  for(AiUsageRecord manual:records){
   if("INDEPENDENT".equals(manual.getResolution())||"EXCLUDED".equals(manual.getStatus())||!MANUAL.equals(manual.getSource())||SUPERSEDED.equals(manual.getStatus()))continue;
   for(AiUsageRecord other:records){
    if("INDEPENDENT".equals(other.getResolution())||"EXCLUDED".equals(other.getStatus())||manual==other||"WITHDRAWN".equals(other.getStatus())||SUPERSEDED.equals(other.getStatus())||!Objects.equals(manual.getAccountId(),other.getAccountId())||!Objects.equals(manual.getKind(),other.getKind()))continue;
    if(!overlaps(manual,other))continue;
    boolean officialCost=OFFICIAL.equals(other.getSource())&&"COST".equals(other.getMetric())&&manual.getCost()!=null&&Objects.equals(manual.getCurrency(),other.getCurrency());
    boolean sameMetric=Objects.equals(manual.getMetric(),other.getMetric())&&Objects.equals(manual.getUnit(),other.getUnit())&&Objects.equals(manual.getDimension(),other.getDimension());
    if(officialCost||(sameMetric&&(OFFICIAL.equals(other.getSource())||CUMULATIVE.equals(manual.getMode())||CUMULATIVE.equals(other.getMode())))){
     manual.setStatus(REVIEW);manual.setReason(OFFICIAL.equals(other.getSource())?"与官方覆盖区间重叠，保留记录并排除合计":"累计与其他观测区间重叠，待核对后重新按互斥区间记录");break;
    }
   }
  }
 }
 private static boolean overlaps(AiUsageRecord left, AiUsageRecord right) {
  java.time.ZoneId leftZone = java.time.ZoneId.of(left.getZoneId()), rightZone = java.time.ZoneId.of(right.getZoneId());
  return left.getStartDate().atStartOfDay(leftZone).toInstant().isBefore(right.getEndDate().atStartOfDay(rightZone).toInstant())
      && right.getStartDate().atStartOfDay(rightZone).toInstant().isBefore(left.getEndDate().atStartOfDay(leftZone).toInstant());
 }
 private static boolean sameSnapshot(AiUsageRecord a,AiUsageRecord b){return CUMULATIVE.equals(b.getMode())&&Objects.equals(a.getAccountId(),b.getAccountId())&&Objects.equals(a.getKind(),b.getKind())&&Objects.equals(a.getSource(),b.getSource())&&Objects.equals(a.getPeriodKey(),b.getPeriodKey())&&Objects.equals(a.getZoneId(),b.getZoneId())&&Objects.equals(a.getStartDate(),b.getStartDate())&&Objects.equals(a.getMetric(),b.getMetric())&&Objects.equals(a.getUnit(),b.getUnit())&&Objects.equals(a.getDimension(),b.getDimension())&&Objects.equals(a.getCurrency(),b.getCurrency());}
 private AiUsageRecordVO toVO(AiUsageRecord u){return new AiUsageRecordVO(u.getId(),u.getAccountId(),u.getKind(),u.getMode(),u.getPeriodKey(),u.getZoneId(),u.getStartDate(),u.getEndDate(),u.getMetric(),u.getUnit(),u.getDimension(),u.getQuantity(),u.getCost(),u.getCurrency(),u.getResolution(),u.getSource(),u.getStatus(),u.getReason(),u.getRecordedAt());}
}
