package com.gak.knowledgebase.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.framework.response.PagedResult;
import com.gak.knowledgebase.domain.KnowledgeEntry;
import com.gak.knowledgebase.dto.KnowledgeEntryQueryRequest;
import com.gak.knowledgebase.dto.KnowledgeHighlightQueryRequest;
import com.gak.knowledgebase.dto.SaveKnowledgeEntryRequest;
import com.gak.knowledgebase.mapper.KnowledgeEntryMapper;
import com.gak.knowledgebase.vo.KnowledgeEntryVO;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * 经验库服务。
 */
@Service
public class KnowledgeBaseService {

    private static final String TAG_SEPARATOR = ",";

    private final KnowledgeEntryMapper knowledgeEntryMapper;
    private final UserMapper userMapper;

    public KnowledgeBaseService(KnowledgeEntryMapper knowledgeEntryMapper, UserMapper userMapper) {
        this.knowledgeEntryMapper = knowledgeEntryMapper;
        this.userMapper = userMapper;
    }

    /**
     * 分页查询经验条目。
     */
    public PagedResult<KnowledgeEntryVO> page(Long currentUserId, KnowledgeEntryQueryRequest request) {
        ensureCurrentUserExists(currentUserId);
        List<KnowledgeEntry> allEntries = listOwnedEntries(currentUserId);
        int pageSize = request.getPageSize();
        long total = allEntries.size();
        int pageNo = Math.max(1, request.getPageNo());
        long maxPageNo = Math.max(1, (total + pageSize - 1) / pageSize);
        pageNo = (int) Math.min(pageNo, maxPageNo);
        int fromIndex = Math.max(0, (pageNo - 1) * pageSize);
        int toIndex = Math.min(allEntries.size(), fromIndex + pageSize);
        List<KnowledgeEntryVO> list = fromIndex >= toIndex
                ? List.of()
                : allEntries.subList(fromIndex, toIndex).stream().map(this::toVO).toList();
        return new PagedResult<>(list, total);
    }

    /**
     * 查询经验详情。
     */
    public KnowledgeEntryVO detail(Long currentUserId, Long id) {
        ensureCurrentUserExists(currentUserId);
        return toVO(getOwnedEntryOrThrow(currentUserId, id));
    }

    /**
     * 新增经验条目。
     */
    @Transactional
    public KnowledgeEntryVO create(Long currentUserId, SaveKnowledgeEntryRequest request) {
        ensureCurrentUserExists(currentUserId);
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setOwnerUserId(currentUserId);
        applySaveRequest(entry, request);
        LocalDateTime now = LocalDateTime.now();
        entry.setCreatedAt(now);
        entry.setUpdatedAt(now);
        knowledgeEntryMapper.insert(entry);
        return toVO(entry);
    }

    /**
     * 更新经验条目。
     */
    @Transactional
    public KnowledgeEntryVO update(Long currentUserId, Long id, SaveKnowledgeEntryRequest request) {
        ensureCurrentUserExists(currentUserId);
        KnowledgeEntry current = getOwnedEntryOrThrow(currentUserId, id);
        applySaveRequest(current, request);
        current.setUpdatedAt(LocalDateTime.now());
        knowledgeEntryMapper.updateById(current);
        return toVO(current);
    }

    /**
     * 删除经验条目。
     */
    @Transactional
    public void delete(Long currentUserId, Long id) {
        ensureCurrentUserExists(currentUserId);
        KnowledgeEntry current = getOwnedEntryOrThrow(currentUserId, id);
        knowledgeEntryMapper.deleteById(current.getId());
    }

    /**
     * 随机推荐经验条目。
     */
    public List<KnowledgeEntryVO> highlights(Long currentUserId, KnowledgeHighlightQueryRequest request) {
        ensureCurrentUserExists(currentUserId);
        List<KnowledgeEntry> allEntries = listOwnedEntries(currentUserId);
        if (allEntries.isEmpty()) {
            return List.of();
        }
        int size = Math.min(request.getSize(), allEntries.size());
        List<KnowledgeEntry> shuffled = new ArrayList<>(allEntries);
        shuffle(shuffled);
        return shuffled.subList(0, size).stream().map(this::toVO).toList();
    }

    private void applySaveRequest(KnowledgeEntry entry, SaveKnowledgeEntryRequest request) {
        entry.setTitle(request.getTitle().trim());
        entry.setCategoryName(request.getCategory().trim());
        entry.setScenario(request.getScenario().trim());
        entry.setSourceName(trimToNull(request.getSource()));
        entry.setTagsText(String.join(TAG_SEPARATOR, normalizeTags(request.getTags())));
        entry.setSummary(request.getSummary().trim());
        entry.setContent(request.getContent().trim());
    }

    private List<KnowledgeEntry> listOwnedEntries(Long currentUserId) {
        QueryWrapper<KnowledgeEntry> wrapper = new QueryWrapper<>();
        wrapper.eq("owner_user_id", currentUserId)
                .orderByDesc("updated_at")
                .orderByDesc("id");
        return knowledgeEntryMapper.selectList(wrapper);
    }

    private KnowledgeEntry getOwnedEntryOrThrow(Long currentUserId, Long id) {
        KnowledgeEntry current = knowledgeEntryMapper.selectById(id);
        if (current == null || !currentUserId.equals(current.getOwnerUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "经验条目不存在");
        }
        return current;
    }

    private KnowledgeEntryVO toVO(KnowledgeEntry entry) {
        KnowledgeEntryVO vo = new KnowledgeEntryVO();
        vo.setId(entry.getId());
        vo.setTitle(entry.getTitle());
        vo.setCategory(entry.getCategoryName());
        vo.setScenario(entry.getScenario());
        vo.setSource(entry.getSourceName());
        vo.setTags(splitTags(entry.getTagsText()));
        vo.setSummary(entry.getSummary());
        vo.setContent(entry.getContent());
        vo.setCreatedAt(entry.getCreatedAt());
        vo.setUpdatedAt(entry.getUpdatedAt());
        return vo;
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            String cleaned = trimToNull(tag);
            if (cleaned != null) {
                normalized.add(cleaned);
            }
        }
        return List.copyOf(normalized);
    }

    private List<String> splitTags(String tagsText) {
        if (!StringUtils.hasText(tagsText)) {
            return List.of();
        }
        String[] parts = tagsText.split(TAG_SEPARATOR);
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String cleaned = trimToNull(part);
            if (cleaned != null) {
                result.add(cleaned);
            }
        }
        return result;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private void ensureCurrentUserExists(Long currentUserId) {
        User currentUser = userMapper.selectById(currentUserId);
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
    }

    private void shuffle(List<KnowledgeEntry> entries) {
        for (int index = entries.size() - 1; index > 0; index -= 1) {
            int swapIndex = ThreadLocalRandom.current().nextInt(index + 1);
            KnowledgeEntry current = entries.get(index);
            entries.set(index, entries.get(swapIndex));
            entries.set(swapIndex, current);
        }
    }
}
