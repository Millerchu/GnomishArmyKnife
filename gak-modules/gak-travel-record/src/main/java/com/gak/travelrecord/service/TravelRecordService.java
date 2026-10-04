package com.gak.travelrecord.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.gak.attachment.constant.AttachmentConstants;
import com.gak.attachment.service.AttachmentService;
import com.gak.framework.exception.BusinessException;
import com.gak.framework.response.PagedResult;
import com.gak.travelrecord.domain.*;
import com.gak.travelrecord.dto.*;
import com.gak.travelrecord.mapper.*;
import com.gak.travelrecord.vo.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/** 行程聚合事务边界：所有者校验、记录、账单和附件一起提交。 */
@Service
public class TravelRecordService {
    private static final int MAX_ATTACHMENTS = 9;
    private final TravelRecordMapper mapper;
    private final TravelGroupMapper groupMapper;
    private final TravelRecordValidator validator;
    private final TravelBillSyncService billSync;
    private final AttachmentService attachments;

    public TravelRecordService(TravelRecordMapper mapper, TravelGroupMapper groupMapper,
            TravelRecordValidator validator, TravelBillSyncService billSync, AttachmentService attachments) {
        this.mapper = mapper;
        this.groupMapper = groupMapper;
        this.validator = validator;
        this.billSync = billSync;
        this.attachments = attachments;
    }

    public PagedResult<TravelRecordVO> page(Long owner, TravelRecordQueryRequest request) {
        long total = mapper.selectCount(query(owner, request));
        long maxPage = Math.max(1, (total + request.getPageSize() - 1) / request.getPageSize());
        long offset = (Math.min(request.getPageNo(), maxPage) - 1) * request.getPageSize();
        List<TravelRecordVO> list = mapper.selectList(ordered(query(owner, request))
                .last("LIMIT " + request.getPageSize() + " OFFSET " + offset))
                .stream().map(this::basicView).toList();
        return new PagedResult<>(list, total);
    }

    public TravelRecordVO detail(Long owner, Long id) {
        TravelRecord record = owned(owner, id);
        TravelRecordVO view = basicView(record);
        view.setAttachments(attachments.listBusinessAttachments(AttachmentConstants.BUSINESS_TRAVEL_RECORD,
                id, AttachmentConstants.USAGE_ATTACHMENT));
        view.setBills(billSync.views(owner, id));
        return view;
    }

    @Transactional
    public TravelRecordVO create(Long owner, SaveTravelRecordRequest request) {
        lockOwner(owner);
        TravelRecord existing = mapper.selectOne(new QueryWrapper<TravelRecord>().eq("owner_user_id", owner)
                .eq("request_key", request.getRequestKey()));
        if (existing != null) { return detail(owner, existing.getId()); }
        validator.validate(request);
        validateRelations(owner, null, request);
        checkDuplicate(owner, null, request);
        TravelRecord record = new TravelRecord();
        apply(record, request);
        record.setOwnerUserId(owner);
        record.setVersion(0);
        record.setCreatedAt(LocalDateTime.now());
        mapper.insert(record);
        sync(record, request);
        return detail(owner, record.getId());
    }

    @Transactional
    public TravelRecordVO update(Long owner, Long id, SaveTravelRecordRequest request) {
        lockOwner(owner);
        TravelRecord record = owned(owner, id);
        if (!Objects.equals(record.getVersion(), request.getVersion())) {
            throw new BusinessException("TRAVEL_VERSION_CONFLICT", "记录已更新，请重新打开后编辑");
        }
        validator.validate(request);
        validateRelations(owner, id, request);
        boolean routeChanged = !Objects.equals(record.getDeparture(), request.getDeparture().trim())
                || !Objects.equals(record.getArrival(), request.getArrival().trim())
                || !Objects.equals(record.getDepartureDate(), request.getDepartureDate())
                || !Objects.equals(record.getTransportType(), request.getTransportType())
                || !Objects.equals(record.getServiceNumber(), trim(request.getServiceNumber()));
        if (routeChanged) { checkDuplicate(owner, id, request); }
        String requestKey = record.getRequestKey();
        apply(record, request);
        record.setRequestKey(requestKey);
        record.setVersion(record.getVersion() + 1);
        sync(record, request);
        return detail(owner, id);
    }

    private void sync(TravelRecord record, SaveTravelRecordRequest request) {
        billSync.sync(record, request.getExistingBillId());
        mapper.updateById(record);
        if (record.getPreviousRecordId() != null) {
            mapper.update(null, new UpdateWrapper<TravelRecord>().eq("owner_user_id", record.getOwnerUserId())
                    .eq("id", record.getPreviousRecordId()).ne("status", "CHANGED")
                    .set("status", "CHANGED").setSql("version = version + 1").set("updated_at", LocalDateTime.now()));
        }
        attachments.syncBusinessAttachments(record.getOwnerUserId(), AttachmentConstants.BUSINESS_TRAVEL_RECORD,
                record.getId(), AttachmentConstants.USAGE_ATTACHMENT,
                request.getAttachmentIds() == null ? List.of() : request.getAttachmentIds(), MAX_ATTACHMENTS);
    }

    private void apply(TravelRecord record, SaveTravelRecordRequest request) {
        BeanUtils.copyProperties(request, record, "id", "ownerUserId", "version", "createdAt", "updatedAt");
        record.setDeparture(request.getDeparture().trim());
        record.setArrival(request.getArrival().trim());
        record.setServiceNumber(trim(request.getServiceNumber()));
        record.setUpdatedAt(LocalDateTime.now());
    }

    private String trim(String value) { return StringUtils.hasText(value) ? value.trim() : null; }

    private void validateRelations(Long owner, Long id, SaveTravelRecordRequest request) {
        if (request.getExistingBillId() != null) {
            QueryWrapper<TravelRecord> reserved = new QueryWrapper<TravelRecord>().eq("owner_user_id", owner)
                    .and(part -> part.eq("detached_payment_bill_id", request.getExistingBillId())
                            .or().eq("detached_refund_bill_id", request.getExistingBillId()));
            if (id != null) { reserved.ne("id", id); }
            if (mapper.selectCount(reserved) > 0) {
                throw new BusinessException("TRAVEL_BILL_RESERVED", "该账单由另一行程保留，请在原行程中重新关联");
            }
        }
        if (request.getGroupId() != null) {
            TravelGroup group = groupMapper.selectById(request.getGroupId());
            if (group == null || !Objects.equals(group.getOwnerUserId(), owner)) { throw missing(); }
        }
        Long previous = request.getPreviousRecordId();
        Set<Long> seen = new HashSet<>();
        while (previous != null) {
            if (Objects.equals(previous, id) || !seen.add(previous)) {
                throw new BusinessException("TRAVEL_CHANGE_CYCLE", "改签关联不能形成循环");
            }
            TravelRecord original = owned(owner, previous);
            previous = original.getPreviousRecordId();
        }
    }

    private void checkDuplicate(Long owner, Long id, SaveTravelRecordRequest request) {
        if (Boolean.TRUE.equals(request.getDuplicateConfirmed())) { return; }
        QueryWrapper<TravelRecord> query = new QueryWrapper<TravelRecord>().eq("owner_user_id", owner)
                .eq("transport_type", request.getTransportType()).eq("departure_date", request.getDepartureDate())
                .eq("departure", request.getDeparture().trim()).eq("arrival", request.getArrival().trim());
        if (id != null) { query.ne("id", id); }
        String number = trim(request.getServiceNumber());
        if (number == null) { query.isNull("service_number"); } else { query.eq("service_number", number); }
        if (mapper.selectCount(query) > 0) {
            throw new BusinessException("TRAVEL_DUPLICATE", "存在相同日期、车次和起终点的记录，请确认是否仍要保存");
        }
    }

    @Transactional
    public void delete(Long owner, Long id, boolean deleteBills) {
        lockOwner(owner);
        owned(owner, id);
        billSync.detachOrDelete(owner, id, deleteBills);
        attachments.deleteByBusiness(AttachmentConstants.BUSINESS_TRAVEL_RECORD, id);
        mapper.update(null, new UpdateWrapper<TravelRecord>().eq("owner_user_id", owner)
                .eq("previous_record_id", id).set("previous_record_id", null)
                .setSql("version = version + 1").set("updated_at", LocalDateTime.now()));
        mapper.deleteById(id);
    }

    public TravelSummaryVO summary(Long owner, TravelRecordQueryRequest request) {
        List<TravelRecord> records = mapper.selectList(ordered(query(owner, request)));
        Map<String, Long> types = new LinkedHashMap<>();
        Map<String, Long> routes = new LinkedHashMap<>();
        Map<String, BigDecimal> amounts = new TreeMap<>();
        long completed = 0;
        long unknown = 0;
        for (TravelRecord record : records) {
            if ("COMPLETED".equals(record.getStatus())) {
                completed++;
                types.merge(record.getTransportType(), 1L, Long::sum);
                routes.merge(record.getDeparture() + " → " + record.getArrival(), 1L, Long::sum);
            }
            if (record.getPaidAmount() == null) { unknown++; continue; }
            BigDecimal refund = record.getRefundAmount() == null ? BigDecimal.ZERO : record.getRefundAmount();
            amounts.merge(record.getCurrency(), record.getPaidAmount().subtract(refund), BigDecimal::add);
        }
        return new TravelSummaryVO(completed, unknown, types, routes, amounts);
    }

    public List<TravelRecord> exportRecords(Long owner, TravelRecordQueryRequest request) {
        return mapper.selectList(ordered(query(owner, request)));
    }

    private QueryWrapper<TravelRecord> query(Long owner, TravelRecordQueryRequest request) {
        QueryWrapper<TravelRecord> query = new QueryWrapper<TravelRecord>().eq("owner_user_id", owner);
        if (request.getYear() != null) {
            query.ge("departure_date", LocalDate.of(request.getYear(), 1, 1))
                    .le("departure_date", LocalDate.of(request.getYear(), 12, 31));
        }
        if (StringUtils.hasText(request.getTransportType())) { query.eq("transport_type", request.getTransportType()); }
        if (StringUtils.hasText(request.getStatus())) { query.eq("status", request.getStatus()); }
        if (request.getGroupId() != null) { query.eq("group_id", request.getGroupId()); }
        if (StringUtils.hasText(request.getKeyword())) {
            String keyword = request.getKeyword().trim();
            query.and(part -> part.like("departure", keyword).or().like("arrival", keyword)
                    .or().like("service_number", keyword).or().like("note", keyword)
                    .or().like("carrier", keyword).or().like("order_number", keyword));
        }
        return query;
    }

    private QueryWrapper<TravelRecord> ordered(QueryWrapper<TravelRecord> query) {
        return query.orderByDesc("departure_date").orderByDesc("id");
    }

    private TravelRecordVO basicView(TravelRecord record) {
        TravelRecordVO view = new TravelRecordVO();
        BeanUtils.copyProperties(record, view);
        return view;
    }

    public TravelRecord owned(Long owner, Long id) {
        TravelRecord record = mapper.selectById(id);
        if (record == null || !Objects.equals(record.getOwnerUserId(), owner)) { throw missing(); }
        return record;
    }

    public void lockOwner(Long owner) {
        if (mapper.lockOwner(owner) == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "当前用户不存在");
        }
    }

    private ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "行程或分组不存在");
    }
}
