package com.gak.knowledgebase.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.attachment.constant.AttachmentConstants;
import com.gak.attachment.service.AttachmentService;
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
import java.util.Comparator;
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
        List<KnowledgeEntry> allEntries = listEntriesByView(currentUser, normalizeView(request.getView()));
        int pageSize = request.getPageSize();
        long total = allEntries.size();
        int pageNo = Math.max(MIN_PAGE_NO, request.getPageNo());
        long maxPageNo = Math.max(MIN_PAGE_NO, (total + pageSize - 1) / pageSize);
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
        return buildHighlights(request);
    }

    /**
     * 匿名访问的公共经验推荐。
     */
    public List<KnowledgeEntryVO> publicHighlights(KnowledgeHighlightQueryRequest request) {
        return buildHighlights(request).stream().map(this::withoutAttachments).toList();
    }

    private List<KnowledgeEntryVO> buildHighlights(KnowledgeHighlightQueryRequest request) {
        List<KnowledgeEntry> allEntries = listPublishedEntries();
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

    private List<KnowledgeEntry> listEntriesByView(User currentUser, String view) {
        if (VIEW_MY_SUBMISSIONS.equals(view)) {
            return listMySubmissionEntries(currentUser.getId());
        }
        if (VIEW_PENDING_REVIEW.equals(view)) {
            ensureAdmin(currentUser);
            return listPendingReviewEntries();
        }
        return listPublishedEntries();
    }

    private List<KnowledgeEntry> listMySubmissionEntries(Long currentUserId) {
        QueryWrapper<KnowledgeEntry> wrapper = new QueryWrapper<>();
        wrapper.eq("owner_user_id", currentUserId)
                .orderByDesc("updated_at")
                .orderByDesc("id");
        return knowledgeEntryMapper.selectList(wrapper).stream()
                .filter(entry -> currentUserId.equals(entry.getOwnerUserId()))
                .sorted(entryOrderComparator())
                .toList();
    }

    private List<KnowledgeEntry> listPendingReviewEntries() {
        QueryWrapper<KnowledgeEntry> wrapper = new QueryWrapper<>();
        wrapper.eq("status", KnowledgeEntryStatus.PENDING.name())
                .orderByDesc("updated_at")
                .orderByDesc("id");
        return knowledgeEntryMapper.selectList(wrapper).stream()
                .filter(entry -> KnowledgeEntryStatus.PENDING.name().equals(entry.getStatus()))
                .sorted(entryOrderComparator())
                .toList();
    }

    private List<KnowledgeEntry> listPublishedEntries() {
        QueryWrapper<KnowledgeEntry> wrapper = new QueryWrapper<>();
        wrapper.eq("status", KnowledgeEntryStatus.PUBLISHED.name())
                .orderByDesc("updated_at")
                .orderByDesc("id");
        return knowledgeEntryMapper.selectList(wrapper).stream()
                .filter(entry -> KnowledgeEntryStatus.PUBLISHED.name().equals(entry.getStatus()))
                .sorted(entryOrderComparator())
                .toList();
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
        vo.setAttachments(attachmentService.listBusinessAttachments(
                AttachmentConstants.BUSINESS_KNOWLEDGE_ENTRY, entry.getId(), AttachmentConstants.USAGE_IMAGE));
        return vo;
    }

    private KnowledgeEntryVO withoutAttachments(KnowledgeEntryVO source) {
        source.setAttachments(List.of());
        return source;
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

    private Comparator<KnowledgeEntry> entryOrderComparator() {
        return Comparator.comparing(KnowledgeEntry::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(KnowledgeEntry::getId, Comparator.nullsLast(Comparator.reverseOrder()));
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
