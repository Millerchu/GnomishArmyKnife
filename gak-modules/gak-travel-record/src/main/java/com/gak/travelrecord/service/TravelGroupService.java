package com.gak.travelrecord.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.gak.travelrecord.domain.*;
import com.gak.travelrecord.dto.SaveTravelGroupRequest;
import com.gak.travelrecord.mapper.*;
import com.gak.travelrecord.vo.TravelGroupVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 删除分组仅解除行程关联，不删除交通记录。 */
@Service
public class TravelGroupService {
    private final TravelGroupMapper groups;
    private final TravelRecordMapper records;
    private final TravelRecordService recordService;
    public TravelGroupService(TravelGroupMapper groups, TravelRecordMapper records, TravelRecordService recordService) {
        this.groups = groups; this.records = records; this.recordService = recordService;
    }
    public List<TravelGroupVO> list(Long owner) {
        return groups.selectList(new QueryWrapper<TravelGroup>().eq("owner_user_id", owner)
                .orderByDesc("created_at").orderByDesc("id")).stream().map(this::view).toList();
    }
    @Transactional
    public TravelGroupVO save(Long owner, Long id, SaveTravelGroupRequest request) {
        recordService.lockOwner(owner);
        TravelGroup group = id == null ? new TravelGroup() : owned(owner, id);
        group.setOwnerUserId(owner);
        group.setName(request.getName().trim());
        group.setNote(request.getNote());
        group.setUpdatedAt(LocalDateTime.now());
        if (id == null) { group.setCreatedAt(LocalDateTime.now()); groups.insert(group); }
        else { groups.updateById(group); }
        return view(group);
    }
    @Transactional
    public void delete(Long owner, Long id) {
        recordService.lockOwner(owner);
        owned(owner, id);
        records.update(null, new UpdateWrapper<TravelRecord>().eq("owner_user_id", owner).eq("group_id", id)
                .set("group_id", null).setSql("version = version + 1").set("updated_at", LocalDateTime.now()));
        groups.deleteById(id);
    }
    private TravelGroup owned(Long owner, Long id) {
        TravelGroup group = groups.selectById(id);
        if (group == null || !Objects.equals(group.getOwnerUserId(), owner)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "出行分组不存在");
        }
        return group;
    }
    private TravelGroupVO view(TravelGroup group) {
        TravelGroupVO view = new TravelGroupVO(); BeanUtils.copyProperties(group, view); return view;
    }
}
