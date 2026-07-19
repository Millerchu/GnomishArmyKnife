package com.gak.knowledgebase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gak.attachment.service.AttachmentService;
import com.gak.framework.response.PagedResult;
import com.gak.knowledgebase.domain.KnowledgeEntry;
import com.gak.knowledgebase.dto.KnowledgeEntryQueryRequest;
import com.gak.knowledgebase.dto.KnowledgeHighlightQueryRequest;
import com.gak.knowledgebase.dto.ReviewKnowledgeEntryRequest;
import com.gak.knowledgebase.dto.SaveKnowledgeEntryRequest;
import com.gak.knowledgebase.mapper.KnowledgeEntryMapper;
import com.gak.knowledgebase.vo.KnowledgeEntryVO;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

/**
 * 经验库服务测试。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceTest {

    @Mock
    private KnowledgeEntryMapper knowledgeEntryMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private AttachmentService attachmentService;

    @InjectMocks
    private KnowledgeBaseService knowledgeBaseService;

    @BeforeEach
    void setUp() {
        lenient().when(userMapper.selectById(1L)).thenReturn(buildUser(1L, "USER"));
        lenient().when(userMapper.selectById(9L)).thenReturn(buildUser(9L, "ADMIN"));
        lenient().when(attachmentService.listBusinessAttachments(any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void shouldNormalizeTagsWhenCreateEntry() {
        SaveKnowledgeEntryRequest request = new SaveKnowledgeEntryRequest();
        request.setTitle("经验");
        request.setCategory("工作");
        request.setScenario("复盘");
        request.setSource("实践");
        request.setTags(List.of("沟通", " 沟通 ", "", "边界"));
        request.setSummary("摘要");
        request.setContent("内容");

        when(knowledgeEntryMapper.insert(any(KnowledgeEntry.class))).thenAnswer(invocation -> {
            KnowledgeEntry entry = invocation.getArgument(0);
            entry.setId(101L);
            return 1;
        });

        KnowledgeEntryVO result = knowledgeBaseService.create(1L, request);

        ArgumentCaptor<KnowledgeEntry> captor = ArgumentCaptor.forClass(KnowledgeEntry.class);
        verify(knowledgeEntryMapper).insert(captor.capture());
        assertThat(captor.getValue().getTagsText()).isEqualTo("沟通,边界");
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(result.getId()).isEqualTo(101L);
        assertThat(result.getTags()).containsExactly("沟通", "边界");
        assertThat(result.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void shouldPageOnlyPublishedEntriesByDefault() {
        KnowledgeEntry first = buildEntry(11L, 2L, "第一条", "PUBLISHED", LocalDateTime.of(2026, 5, 10, 12, 0));
        KnowledgeEntry second = buildEntry(12L, 1L, "第二条", "PENDING", LocalDateTime.of(2026, 5, 9, 12, 0));
        when(knowledgeEntryMapper.selectList(any())).thenReturn(List.of(first, second));

        KnowledgeEntryQueryRequest request = new KnowledgeEntryQueryRequest();
        request.setPageNo(1);
        request.setPageSize(10);

        PagedResult<KnowledgeEntryVO> result = knowledgeBaseService.page(1L, request);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.list()).hasSize(1);
        assertThat(result.list().get(0).getTitle()).isEqualTo("第一条");
        assertThat(result.list().get(0).getStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    void shouldReturnLimitedHighlights() {
        when(knowledgeEntryMapper.selectList(any())).thenReturn(List.of(
                buildEntry(11L, 2L, "A", "PUBLISHED", LocalDateTime.now()),
                buildEntry(12L, 2L, "B", "PUBLISHED", LocalDateTime.now()),
                buildEntry(13L, 2L, "C", "PUBLISHED", LocalDateTime.now()),
                buildEntry(14L, 2L, "D", "PUBLISHED", LocalDateTime.now())
        ));
        KnowledgeHighlightQueryRequest request = new KnowledgeHighlightQueryRequest();
        request.setSize(3);

        List<KnowledgeEntryVO> result = knowledgeBaseService.highlights(1L, request);
        assertThat(result).hasSize(3);
    }

    @Test
    void shouldReturnPublicHighlightsWithoutLoginUser() {
        when(knowledgeEntryMapper.selectList(any())).thenReturn(List.of(
                buildEntry(51L, 2L, "公共经验A", "PUBLISHED", LocalDateTime.now()),
                buildEntry(52L, 3L, "公共经验B", "PUBLISHED", LocalDateTime.now())
        ));
        KnowledgeHighlightQueryRequest request = new KnowledgeHighlightQueryRequest();
        request.setSize(2);

        List<KnowledgeEntryVO> result = knowledgeBaseService.publicHighlights(request);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(KnowledgeEntryVO::getStatus).containsOnly("PUBLISHED");
    }

    @Test
    void shouldRejectDetailWhenNormalUserReadsOthersPendingEntry() {
        KnowledgeEntry pendingEntry = buildEntry(21L, 2L, "待审核经验", "PENDING", LocalDateTime.now());
        when(knowledgeEntryMapper.selectById(21L)).thenReturn(pendingEntry);

        assertThrows(ResponseStatusException.class, () -> knowledgeBaseService.detail(1L, 21L));
    }

    @Test
    void shouldReturnPendingReviewEntriesForAdmin() {
        when(knowledgeEntryMapper.selectList(any())).thenReturn(List.of(
                buildEntry(31L, 1L, "我的待审核", "PENDING", LocalDateTime.now()),
                buildEntry(32L, 2L, "他人的待审核", "PENDING", LocalDateTime.now())
        ));

        KnowledgeEntryQueryRequest request = new KnowledgeEntryQueryRequest();
        request.setView("pending-review");

        PagedResult<KnowledgeEntryVO> result = knowledgeBaseService.page(9L, request);

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.list()).hasSize(2);
        assertThat(result.list()).extracting(KnowledgeEntryVO::getStatus).containsOnly("PENDING");
    }

    @Test
    void shouldPublishPendingEntryThroughAdminReview() {
        KnowledgeEntry pendingEntry = buildEntry(41L, 1L, "待发布经验", "PENDING", LocalDateTime.now());
        when(knowledgeEntryMapper.selectById(41L)).thenReturn(pendingEntry);
        when(knowledgeEntryMapper.selectList(any())).thenReturn(List.of(
                buildEntry(41L, 1L, "待发布经验", "PUBLISHED", LocalDateTime.now())
        ));

        ReviewKnowledgeEntryRequest request = new ReviewKnowledgeEntryRequest();
        request.setReviewRemark("内容可发布");

        KnowledgeEntryVO reviewed = knowledgeBaseService.publish(9L, 41L, request);
        List<KnowledgeEntryVO> highlights = knowledgeBaseService.highlights(1L, new KnowledgeHighlightQueryRequest());

        verify(knowledgeEntryMapper).updateById(any(KnowledgeEntry.class));
        assertThat(reviewed.getStatus()).isEqualTo("PUBLISHED");
        assertThat(highlights).extracting(KnowledgeEntryVO::getTitle).contains("待发布经验");
    }

    private KnowledgeEntry buildEntry(Long id, Long ownerUserId, String title, String status, LocalDateTime updatedAt) {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setId(id);
        entry.setOwnerUserId(ownerUserId);
        entry.setTitle(title);
        entry.setCategoryName("工作");
        entry.setScenario("场景");
        entry.setSourceName("来源");
        entry.setTagsText("经验,复盘");
        entry.setSummary("摘要");
        entry.setContent("内容");
        entry.setStatus(status);
        entry.setCreatedAt(updatedAt.minusDays(1));
        entry.setUpdatedAt(updatedAt);
        return entry;
    }

    private User buildUser(Long id, String roleCode) {
        User user = new User();
        user.setId(id);
        user.setRoleCode(roleCode);
        user.setEnabled(true);
        return user;
    }
}
