package com.gak.knowledgebase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 经验库服务测试。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceTest {

    @Mock
    private KnowledgeEntryMapper knowledgeEntryMapper;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private KnowledgeBaseService knowledgeBaseService;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(1L);
        when(userMapper.selectById(1L)).thenReturn(user);
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
        assertThat(result.getId()).isEqualTo(101L);
        assertThat(result.getTags()).containsExactly("沟通", "边界");
    }

    @Test
    void shouldPageEntriesByUpdatedTime() {
        KnowledgeEntry first = buildEntry(11L, "第一条", LocalDateTime.of(2026, 5, 10, 12, 0));
        KnowledgeEntry second = buildEntry(12L, "第二条", LocalDateTime.of(2026, 5, 9, 12, 0));
        when(knowledgeEntryMapper.selectList(any())).thenReturn(List.of(first, second));

        KnowledgeEntryQueryRequest request = new KnowledgeEntryQueryRequest();
        request.setPageNo(1);
        request.setPageSize(1);

        PagedResult<KnowledgeEntryVO> result = knowledgeBaseService.page(1L, request);
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.list()).hasSize(1);
        assertThat(result.list().get(0).getTitle()).isEqualTo("第一条");
    }

    @Test
    void shouldReturnLimitedHighlights() {
        when(knowledgeEntryMapper.selectList(any())).thenReturn(List.of(
                buildEntry(11L, "A", LocalDateTime.now()),
                buildEntry(12L, "B", LocalDateTime.now()),
                buildEntry(13L, "C", LocalDateTime.now()),
                buildEntry(14L, "D", LocalDateTime.now())
        ));
        KnowledgeHighlightQueryRequest request = new KnowledgeHighlightQueryRequest();
        request.setSize(3);

        List<KnowledgeEntryVO> result = knowledgeBaseService.highlights(1L, request);
        assertThat(result).hasSize(3);
    }

    private KnowledgeEntry buildEntry(Long id, String title, LocalDateTime updatedAt) {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setId(id);
        entry.setOwnerUserId(1L);
        entry.setTitle(title);
        entry.setCategoryName("工作");
        entry.setScenario("场景");
        entry.setSourceName("来源");
        entry.setTagsText("经验,复盘");
        entry.setSummary("摘要");
        entry.setContent("内容");
        entry.setCreatedAt(updatedAt.minusDays(1));
        entry.setUpdatedAt(updatedAt);
        return entry;
    }
}
