package com.gak.knowledgebase.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.attachment.constant.AttachmentConstants;
import com.gak.attachment.service.AttachmentService;
import com.gak.attachment.vo.AttachmentVO;
import com.gak.framework.response.PagedResult;
import com.gak.knowledgebase.domain.KnowledgeEntry;
import com.gak.knowledgebase.dto.KnowledgeEntryQueryRequest;
import com.gak.knowledgebase.dto.KnowledgeHighlightQueryRequest;
import com.gak.knowledgebase.dto.ReviewKnowledgeEntryRequest;
import com.gak.knowledgebase.dto.SaveKnowledgeEntryRequest;
import com.gak.knowledgebase.enums.KnowledgeEntryStatus;
import com.gak.knowledgebase.mapper.KnowledgeEntryMapper;
import com.gak.knowledgebase.vo.KnowledgeEntryVO;
import com.gak.user.constant.UserSecurityConstants;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.time.Duration;
import java.util.Map;
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
    private static final String VIEW_PUBLISHED = "published";
    private static final String VIEW_MY_SUBMISSIONS = "my-submissions";
    private static final String VIEW_PENDING_REVIEW = "pending-review";
    private static final int MIN_PAGE_NO = 1;
    private static final int MAX_HIGHLIGHTS = 12;
    private static final long HIGHLIGHT_CACHE_NANOS = Duration.ofSeconds(30).toNanos();

    private List<Long> publicHighlightIds = List.of();
    private long nextPublicSampleAt = System.nanoTime();

    private final KnowledgeEntryMapper knowledgeEntryMapper;
    private final UserMapper userMapper;
    private final AttachmentService attachmentService;

    public KnowledgeBaseService(KnowledgeEntryMapper knowledgeEntryMapper,
                                UserMapper userMapper,
                                AttachmentService attachmentService) {
        this.knowledgeEntryMapper = knowledgeEntryMapper;
        this.userMapper = userMapper;
        this.attachmentService = attachmentService;
    }

    /**
     * 分页查询经验条目。
     */
    public PagedResult<KnowledgeEntryVO> page(Long currentUserId, KnowledgeEntryQueryRequest request) {
        User currentUser = requireCurrentUser(currentUserId);
        QueryWrapper<KnowledgeEntry> query = entriesByView(currentUser, normalizeView(request.getView()));
        long total = knowledgeEntryMapper.selectCount(query);
        if (total == 0) {
            return new PagedResult<>(List.of(), 0);
        }
        int pageSize = request.getPageSize();
        long maxPageNo = Math.max(MIN_PAGE_NO, (total + pageSize - 1) / pageSize);
        long pageNo = Math.min(Math.max(MIN_PAGE_NO, request.getPageNo()), maxPageNo);
        List<KnowledgeEntry> entries = knowledgeEntryMapper.selectList(query.orderByDesc("updated_at", "id")
                .last("LIMIT " + pageSize + " OFFSET " + ((pageNo - 1) * pageSize)));
        return new PagedResult<>(withAttachments(entries), total);
    }

    /**
     * 查询经验详情。
     */
    public KnowledgeEntryVO detail(Long currentUserId, Long id) {
        User currentUser = requireCurrentUser(currentUserId);
        return toVO(getAccessibleEntryOrThrow(currentUser, id));
    }

    /**
     * 新增经验条目。
     */
    @Transactional
    public KnowledgeEntryVO create(Long currentUserId, SaveKnowledgeEntryRequest request) {
        User currentUser = requireCurrentUser(currentUserId);
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setOwnerUserId(currentUserId);
        applySaveRequest(entry, request);
        LocalDateTime now = LocalDateTime.now();
        entry.setCreatedAt(now);
        entry.setUpdatedAt(now);
        initializeCreateStatus(entry, currentUser);
        knowledgeEntryMapper.insert(entry);
        attachmentService.syncBusinessAttachments(currentUserId,
                AttachmentConstants.BUSINESS_KNOWLEDGE_ENTRY, entry.getId(), AttachmentConstants.USAGE_IMAGE,
                request.getAttachmentIds(), 9);
        return toVO(entry);
    }

    /**
     * 更新经验条目。
     */
    @Transactional
    public KnowledgeEntryVO update(Long currentUserId, Long id, SaveKnowledgeEntryRequest request) {
        User currentUser = requireCurrentUser(currentUserId);
        KnowledgeEntry current = getEditableEntryOrThrow(currentUser, id);
        applySaveRequest(current, request);
        resetReviewStateWhenNeeded(current, currentUser);
        current.setUpdatedAt(LocalDateTime.now());
        knowledgeEntryMapper.updateById(current);
        attachmentService.syncBusinessAttachments(current.getOwnerUserId(),
                AttachmentConstants.BUSINESS_KNOWLEDGE_ENTRY, current.getId(), AttachmentConstants.USAGE_IMAGE,
                request.getAttachmentIds(), 9);
        return toVO(current);
    }

    /**
     * 删除经验条目。
     */
    @Transactional
    public void delete(Long currentUserId, Long id) {
        User currentUser = requireCurrentUser(currentUserId);
        KnowledgeEntry current = getDeletableEntryOrThrow(currentUser, id);
        attachmentService.deleteByBusiness(AttachmentConstants.BUSINESS_KNOWLEDGE_ENTRY, current.getId());
        knowledgeEntryMapper.deleteById(current.getId());
    }

    /**
     * 发布待审核经验。
     */
    @Transactional
    public KnowledgeEntryVO publish(Long currentUserId, Long id, ReviewKnowledgeEntryRequest request) {
        User currentUser = requireCurrentUser(currentUserId);
        ensureAdmin(currentUser);
        KnowledgeEntry current = getEntryForReviewOrThrow(id);
        applyReviewResult(current, currentUserId, KnowledgeEntryStatus.PUBLISHED, request.getReviewRemark());
        knowledgeEntryMapper.updateById(current);
        return toVO(current);
    }

    /**
     * 驳回待审核经验。
     */
    @Transactional
    public KnowledgeEntryVO reject(Long currentUserId, Long id, ReviewKnowledgeEntryRequest request) {
        User currentUser = requireCurrentUser(currentUserId);
        ensureAdmin(currentUser);
        KnowledgeEntry current = getEntryForReviewOrThrow(id);
        applyReviewResult(current, currentUserId, KnowledgeEntryStatus.REJECTED, request.getReviewRemark());
        knowledgeEntryMapper.updateById(current);
        return toVO(current);
    }

    /**
     * 随机推荐经验条目。
     */
    public List<KnowledgeEntryVO> highlights(Long currentUserId, KnowledgeHighlightQueryRequest request) {
        requireCurrentUser(currentUserId);
        return withAttachments(loadHighlights(samplePublishedIds(request.getSize()), request.getSize()));
    }

    /** 短期缓存样本 ID，每次仍按已发布状态过滤，避免继续展示撤回或删除的内容。 */
    public List<KnowledgeEntryVO> publicHighlights(KnowledgeHighlightQueryRequest request) {
        return loadHighlights(publicSample(), request.getSize()).stream().map(this::toBaseVO).toList();
    }

    private synchronized List<Long> publicSample() {
        long now = System.nanoTime();
        if (now - nextPublicSampleAt >= 0) {
            publicHighlightIds = samplePublishedIds(MAX_HIGHLIGHTS);
            nextPublicSampleAt = now + HIGHLIGHT_CACHE_NANOS;
        }
        return publicHighlightIds;
    }

    private List<Long> samplePublishedIds(int size) {
        List<Long> bounds = knowledgeEntryMapper.selectPublishedBounds();
        if (bounds.isEmpty()) {
            return List.of();
        }
        long first = bounds.getFirst();
        long last = bounds.getLast();
        long pivot = first == last ? first : ThreadLocalRandom.current().nextLong(first, last);
        List<Long> result = new ArrayList<>(sampleFrom(pivot, size, true));
        if (result.size() < size && pivot > first) {
            result.addAll(sampleFrom(pivot, size - result.size(), false));
        }
        return List.copyOf(result);
    }

    private List<Long> sampleFrom(long pivot, int limit, boolean fromPivot) {
        QueryWrapper<KnowledgeEntry> query = publishedEntries().select("id");
        if (fromPivot) {
            query.ge("id", pivot);
        } else {
            query.lt("id", pivot);
        }
        return knowledgeEntryMapper.selectList(query.orderByAsc("id").last("LIMIT " + limit))
                .stream().map(KnowledgeEntry::getId).toList();
    }

    private List<KnowledgeEntry> loadHighlights(List<Long> ids, int size) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, KnowledgeEntry> byId = knowledgeEntryMapper.selectList(publishedEntries().in("id", ids))
                .stream().collect(java.util.stream.Collectors.toMap(KnowledgeEntry::getId, entry -> entry));
        return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).limit(size).toList();
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

    private QueryWrapper<KnowledgeEntry> entriesByView(User currentUser, String view) {
        if (VIEW_MY_SUBMISSIONS.equals(view)) {
            return new QueryWrapper<KnowledgeEntry>().eq("owner_user_id", currentUser.getId());
        }
        if (VIEW_PENDING_REVIEW.equals(view)) {
            ensureAdmin(currentUser);
            return new QueryWrapper<KnowledgeEntry>().eq("status", KnowledgeEntryStatus.PENDING.name());
        }
        return publishedEntries();
    }

    private QueryWrapper<KnowledgeEntry> publishedEntries() {
        // 固定条件与 PostgreSQL 部分索引一致，查询按索引从随机游标开始读取少量 ID。
        return new QueryWrapper<KnowledgeEntry>().apply("status = 'PUBLISHED'");
    }

    private KnowledgeEntry getAccessibleEntryOrThrow(User currentUser, Long id) {
        KnowledgeEntry current = knowledgeEntryMapper.selectById(id);
        if (current == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "经验条目不存在");
        }
        if (KnowledgeEntryStatus.PUBLISHED.name().equals(current.getStatus())) {
            return current;
        }
        if (isAdmin(currentUser) || currentUser.getId().equals(current.getOwnerUserId())) {
            return current;
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "经验条目不存在");
    }

    private KnowledgeEntry getEditableEntryOrThrow(User currentUser, Long id) {
        KnowledgeEntry current = knowledgeEntryMapper.selectById(id);
        if (current == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "经验条目不存在");
        }
        if (isAdmin(currentUser)) {
            return current;
        }
        boolean ownerMatched = currentUser.getId().equals(current.getOwnerUserId());
        boolean editableStatus = KnowledgeEntryStatus.PENDING.name().equals(current.getStatus())
                || KnowledgeEntryStatus.REJECTED.name().equals(current.getStatus());
        if (ownerMatched && editableStatus) {
            return current;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前经验状态不允许编辑");
    }

    private KnowledgeEntry getDeletableEntryOrThrow(User currentUser, Long id) {
        KnowledgeEntry current = knowledgeEntryMapper.selectById(id);
        if (current == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "经验条目不存在");
        }
        if (isAdmin(currentUser)) {
            return current;
        }
        boolean ownerMatched = currentUser.getId().equals(current.getOwnerUserId());
        boolean deletableStatus = !KnowledgeEntryStatus.PUBLISHED.name().equals(current.getStatus());
        if (ownerMatched && deletableStatus) {
            return current;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前经验状态不允许删除");
    }

    private KnowledgeEntry getEntryForReviewOrThrow(Long id) {
        KnowledgeEntry current = knowledgeEntryMapper.selectById(id);
        if (current == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "经验条目不存在");
        }
        if (!KnowledgeEntryStatus.PENDING.name().equals(current.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只有待审核经验可以执行审核操作");
        }
        return current;
    }

    private KnowledgeEntryVO toVO(KnowledgeEntry entry) {
        KnowledgeEntryVO vo = toBaseVO(entry);
        vo.setAttachments(attachmentService.listBusinessAttachments(
                AttachmentConstants.BUSINESS_KNOWLEDGE_ENTRY, entry.getId(), AttachmentConstants.USAGE_IMAGE));
        return vo;
    }

    private List<KnowledgeEntryVO> withAttachments(List<KnowledgeEntry> entries) {
        if (entries.isEmpty()) {
            return List.of();
        }
        Map<Long, List<AttachmentVO>> attachments = attachmentService.listBusinessAttachmentsByIds(
                AttachmentConstants.BUSINESS_KNOWLEDGE_ENTRY,
                entries.stream().map(KnowledgeEntry::getId).toList(), AttachmentConstants.USAGE_IMAGE);
        return entries.stream().map(entry -> {
            KnowledgeEntryVO vo = toBaseVO(entry);
            vo.setAttachments(attachments.getOrDefault(entry.getId(), List.of()));
            return vo;
        }).toList();
    }

    private KnowledgeEntryVO toBaseVO(KnowledgeEntry entry) {
        KnowledgeEntryVO vo = new KnowledgeEntryVO();
        vo.setId(entry.getId());
        vo.setOwnerUserId(entry.getOwnerUserId());
        vo.setTitle(entry.getTitle());
        vo.setCategory(entry.getCategoryName());
        vo.setScenario(entry.getScenario());
        vo.setSource(entry.getSourceName());
        vo.setTags(splitTags(entry.getTagsText()));
        vo.setSummary(entry.getSummary());
        vo.setContent(entry.getContent());
        vo.setStatus(entry.getStatus());
        vo.setReviewedBy(entry.getReviewedBy());
        vo.setReviewedAt(entry.getReviewedAt());
        vo.setReviewRemark(entry.getReviewRemark());
        vo.setCreatedAt(entry.getCreatedAt());
        vo.setUpdatedAt(entry.getUpdatedAt());
        vo.setAttachments(List.of());
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

    private User requireCurrentUser(Long currentUserId) {
        User currentUser = userMapper.selectById(currentUserId);
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        return currentUser;
    }

    private void initializeCreateStatus(KnowledgeEntry entry, User currentUser) {
        if (isAdmin(currentUser)) {
            entry.setStatus(KnowledgeEntryStatus.PUBLISHED.name());
            entry.setReviewedBy(currentUser.getId());
            entry.setReviewedAt(LocalDateTime.now());
            entry.setReviewRemark("管理员直接发布");
            return;
        }
        entry.setStatus(KnowledgeEntryStatus.PENDING.name());
        clearReviewFields(entry);
    }

    private void resetReviewStateWhenNeeded(KnowledgeEntry entry, User currentUser) {
        if (isAdmin(currentUser)) {
            return;
        }
        entry.setStatus(KnowledgeEntryStatus.PENDING.name());
        clearReviewFields(entry);
    }

    private void applyReviewResult(KnowledgeEntry entry,
                                   Long reviewerId,
                                   KnowledgeEntryStatus targetStatus,
                                   String reviewRemark) {
        entry.setStatus(targetStatus.name());
        entry.setReviewedBy(reviewerId);
        entry.setReviewedAt(LocalDateTime.now());
        entry.setReviewRemark(trimToNull(reviewRemark));
        entry.setUpdatedAt(LocalDateTime.now());
    }

    private void clearReviewFields(KnowledgeEntry entry) {
        entry.setReviewedBy(null);
        entry.setReviewedAt(null);
        entry.setReviewRemark(null);
    }

    private String normalizeView(String view) {
        String normalized = trimToNull(view);
        if (normalized == null) {
            return VIEW_PUBLISHED;
        }
        if (VIEW_MY_SUBMISSIONS.equals(normalized) || VIEW_PENDING_REVIEW.equals(normalized)) {
            return normalized;
        }
        return VIEW_PUBLISHED;
    }

    private void ensureAdmin(User currentUser) {
        if (!isAdmin(currentUser)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前用户没有审核权限");
        }
    }

    private boolean isAdmin(User currentUser) {
        return UserSecurityConstants.ADMIN_ROLE_CODE.equals(currentUser.getRoleCode());
    }
}
