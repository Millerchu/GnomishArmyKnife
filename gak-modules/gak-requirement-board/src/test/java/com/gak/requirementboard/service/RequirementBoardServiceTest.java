package com.gak.requirementboard.service;

import com.gak.framework.exception.BusinessException;
import com.gak.requirementboard.domain.Requirement;
import com.gak.requirementboard.domain.RequirementProgressLog;
import com.gak.requirementboard.dto.CreateRequirementRequest;
import com.gak.requirementboard.dto.RequirementQueryRequest;
import com.gak.requirementboard.dto.UpdateRequirementProgressRequest;
import com.gak.requirementboard.dto.UpdateRequirementRequest;
import com.gak.requirementboard.mapper.RequirementAppMapper;
import com.gak.requirementboard.mapper.RequirementMapper;
import com.gak.requirementboard.mapper.RequirementProgressLogMapper;
import com.gak.requirementboard.vo.RequirementAppOptionVO;
import com.gak.requirementboard.vo.RequirementPageVO;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequirementBoardServiceTest {

    @Mock
    private RequirementMapper requirementMapper;

    @Mock
    private RequirementProgressLogMapper requirementProgressLogMapper;

    @Mock
    private RequirementAppMapper requirementAppMapper;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private RequirementBoardService requirementBoardService;

    @Test
    void createShouldPersistInitialPendingReviewTimeline() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L, "提交者", "USER"));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(buildUser(1L, "提交者", "USER")));
        when(requirementAppMapper.selectEnabledAppByCode("APP_TODO_LIST"))
                .thenReturn(buildApp("APP_TODO_LIST", "待办清单"));
        doAnswer(invocation -> {
            Requirement requirement = invocation.getArgument(0);
            requirement.setId(100L);
            return 1;
        }).when(requirementMapper).insert(any(Requirement.class));

        CreateRequirementRequest request = new CreateRequirementRequest();
        request.setAppCode("APP_TODO_LIST");
        request.setType("BUG");
        request.setTitle("支持导出需求列表");
        request.setDescription("希望能按状态导出当前看板。");
        request.setPriority("HIGH");

        requirementBoardService.create(1L, request);

        ArgumentCaptor<Requirement> requirementCaptor = ArgumentCaptor.forClass(Requirement.class);
        ArgumentCaptor<RequirementProgressLog> logCaptor = ArgumentCaptor.forClass(RequirementProgressLog.class);
        verify(requirementMapper).insert(requirementCaptor.capture());
        verify(requirementProgressLogMapper).insert(logCaptor.capture());
        assertEquals("PENDING_REVIEW", requirementCaptor.getValue().getStatus());
        assertEquals("APP_TODO_LIST", requirementCaptor.getValue().getAppCode());
        assertEquals("待办清单", requirementCaptor.getValue().getAppName());
        assertEquals("BUG", requirementCaptor.getValue().getType());
        assertEquals("HIGH", requirementCaptor.getValue().getPriority());
        assertEquals(1L, requirementCaptor.getValue().getVersion());
        assertEquals("提交Bug", logCaptor.getValue().getRemark());
        assertEquals("PENDING_REVIEW", logCaptor.getValue().getStatus());
    }

    @Test
    void updateProgressShouldAllowAnyLoggedInUserAndWriteTimeline() {
        Requirement requirement = buildRequirement(100L, 1L, "待处理需求", "PENDING_REVIEW", 3L);
        when(userMapper.selectById(2L)).thenReturn(buildUser(2L, "协作者", "USER"));
        when(requirementMapper.selectById(100L)).thenReturn(requirement);
        when(requirementMapper.update(any(Requirement.class), any())).thenReturn(1);
        when(requirementProgressLogMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(
                buildUser(1L, "提交者", "USER"), buildUser(2L, "协作者", "USER")
        ));

        UpdateRequirementProgressRequest request = new UpdateRequirementProgressRequest();
        request.setStatus("IN_PROGRESS");
        request.setRemark("已开始排期处理");
        request.setVersion(3L);

        requirementBoardService.updateProgress(2L, 100L, request);

        ArgumentCaptor<Requirement> requirementCaptor = ArgumentCaptor.forClass(Requirement.class);
        ArgumentCaptor<RequirementProgressLog> logCaptor = ArgumentCaptor.forClass(RequirementProgressLog.class);
        verify(requirementMapper).update(requirementCaptor.capture(), any());
        verify(requirementProgressLogMapper).insert(logCaptor.capture());
        assertEquals("IN_PROGRESS", requirementCaptor.getValue().getStatus());
        assertEquals(4L, requirementCaptor.getValue().getVersion());
        assertEquals(2L, logCaptor.getValue().getOperatorUserId());
        assertEquals("已开始排期处理", logCaptor.getValue().getRemark());
    }

    @Test
    void updateContentShouldRejectNonCreator() {
        when(userMapper.selectById(2L)).thenReturn(buildUser(2L, "协作者", "USER"));
        when(requirementMapper.selectById(100L)).thenReturn(buildRequirement(100L, 1L, "待处理需求", "PENDING_REVIEW", 1L));

        UpdateRequirementRequest request = new UpdateRequirementRequest();
        request.setTitle("尝试修改他人的需求");
        request.setVersion(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> requirementBoardService.updateContent(2L, 100L, request));

        assertEquals("REQUIREMENT_EDIT_FORBIDDEN", exception.getCode());
        verify(requirementMapper, never()).update(any(Requirement.class), any());
    }

    @Test
    void updateContentShouldAllowCreator() {
        Requirement requirement = buildRequirement(100L, 1L, "旧标题", "PENDING_REVIEW", 1L);
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L, "提交者", "USER"));
        when(requirementMapper.selectById(100L)).thenReturn(requirement);
        when(requirementMapper.update(any(Requirement.class), any())).thenReturn(1);
        when(requirementProgressLogMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(buildUser(1L, "提交者", "USER")));
        when(requirementAppMapper.selectEnabledAppByCode("APP_TODO_LIST"))
                .thenReturn(buildApp("APP_TODO_LIST", "待办清单"));

        UpdateRequirementRequest request = new UpdateRequirementRequest();
        request.setAppCode("APP_TODO_LIST");
        request.setType("REQUIREMENT");
        request.setTitle("新标题");
        request.setDescription("补充后的描述");
        request.setPriority("LOW");
        request.setVersion(1L);

        requirementBoardService.updateContent(1L, 100L, request);

        ArgumentCaptor<Requirement> captor = ArgumentCaptor.forClass(Requirement.class);
        verify(requirementMapper).update(captor.capture(), any());
        assertEquals("新标题", captor.getValue().getTitle());
        assertEquals("补充后的描述", captor.getValue().getDescription());
        assertEquals("APP_TODO_LIST", captor.getValue().getAppCode());
        assertEquals("REQUIREMENT", captor.getValue().getType());
        assertEquals("LOW", captor.getValue().getPriority());
        assertEquals(2L, captor.getValue().getVersion());
    }

    @Test
    void createShouldRejectInvalidPriority() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L, "提交者", "USER"));
        when(requirementAppMapper.selectEnabledAppByCode("APP_TODO_LIST"))
                .thenReturn(buildApp("APP_TODO_LIST", "待办清单"));

        CreateRequirementRequest request = new CreateRequirementRequest();
        request.setAppCode("APP_TODO_LIST");
        request.setType("REQUIREMENT");
        request.setTitle("非法优先级需求");
        request.setPriority("URGENT");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> requirementBoardService.create(1L, request));

        assertEquals("REQUIREMENT_PRIORITY_INVALID", exception.getCode());
        verify(requirementMapper, never()).insert(any(Requirement.class));
    }

    @Test
    void createShouldAllowFixedSystemApplication() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L, "提交者", "USER"));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(buildUser(1L, "提交者", "USER")));
        doAnswer(invocation -> {
            Requirement requirement = invocation.getArgument(0);
            requirement.setId(101L);
            return 1;
        }).when(requirementMapper).insert(any(Requirement.class));

        CreateRequirementRequest request = new CreateRequirementRequest();
        request.setAppCode("APP_USER_BOARD");
        request.setType("REQUIREMENT");
        request.setTitle("优化用户看板");
        request.setPriority("MEDIUM");

        requirementBoardService.create(1L, request);

        ArgumentCaptor<Requirement> captor = ArgumentCaptor.forClass(Requirement.class);
        verify(requirementMapper).insert(captor.capture());
        assertEquals("APP_USER_BOARD", captor.getValue().getAppCode());
        assertEquals("用户看板", captor.getValue().getAppName());
        verify(requirementAppMapper, never()).selectEnabledAppByCode(any());
    }

    @Test
    void createShouldRejectInvalidType() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L, "提交者", "USER"));
        when(requirementAppMapper.selectEnabledAppByCode("APP_TODO_LIST"))
                .thenReturn(buildApp("APP_TODO_LIST", "待办清单"));

        CreateRequirementRequest request = new CreateRequirementRequest();
        request.setAppCode("APP_TODO_LIST");
        request.setType("TASK");
        request.setTitle("非法类型条目");
        request.setPriority("MEDIUM");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> requirementBoardService.create(1L, request));

        assertEquals("REQUIREMENT_TYPE_INVALID", exception.getCode());
        verify(requirementMapper, never()).insert(any(Requirement.class));
    }

    @Test
    void listAppsShouldPreferFixedSystemApplicationAndMergeBusinessApps() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L, "提交者", "USER"));
        when(requirementAppMapper.selectEnabledApps()).thenReturn(List.of(
                buildApp("APP_DATA_DICTIONARY", "旧数据字典名称"),
                buildApp("APP_TODO_LIST", "待办清单")
        ));

        List<RequirementAppOptionVO> apps = requirementBoardService.listApps(1L);

        assertEquals(8, apps.size());
        assertEquals("用户管理", apps.get(0).getAppName());
        assertEquals("数据字典", apps.get(4).getAppName());
        assertEquals("新应用", apps.get(6).getAppName());
        assertEquals("待办清单", apps.get(7).getAppName());
    }

    @Test
    void updateProgressShouldRejectStaleVersion() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L, "提交者", "USER"));
        when(requirementMapper.selectById(100L)).thenReturn(buildRequirement(100L, 1L, "待处理需求", "PENDING_REVIEW", 1L));
        when(requirementMapper.update(any(Requirement.class), any())).thenReturn(0);

        UpdateRequirementProgressRequest request = new UpdateRequirementProgressRequest();
        request.setStatus("PLANNED");
        request.setVersion(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> requirementBoardService.updateProgress(1L, 100L, request));

        assertEquals("REQUIREMENT_VERSION_CONFLICT", exception.getCode());
        verify(requirementProgressLogMapper, never()).insert(any(RequirementProgressLog.class));
    }

    @Test
    void updateProgressShouldRejectInvalidStatus() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L, "提交者", "USER"));

        UpdateRequirementProgressRequest request = new UpdateRequirementProgressRequest();
        request.setStatus("ARCHIVED");
        request.setVersion(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> requirementBoardService.updateProgress(1L, 100L, request));

        assertEquals("REQUIREMENT_STATUS_INVALID", exception.getCode());
        verify(requirementMapper, never()).update(any(Requirement.class), any());
    }

    @Test
    void deleteShouldAllowCreatorAndAdminOnly() {
        Requirement requirement = buildRequirement(100L, 1L, "待处理需求", "PENDING_REVIEW", 1L);
        when(requirementMapper.selectById(100L)).thenReturn(requirement);
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L, "提交者", "USER"));
        when(requirementMapper.delete(any())).thenReturn(1);

        requirementBoardService.delete(1L, 100L, 1L);

        verify(requirementMapper).delete(any());

        when(userMapper.selectById(2L)).thenReturn(buildUser(2L, "协作者", "USER"));
        BusinessException exception = assertThrows(BusinessException.class,
                () -> requirementBoardService.delete(2L, 100L, 1L));
        assertEquals("REQUIREMENT_DELETE_FORBIDDEN", exception.getCode());

        when(userMapper.selectById(3L)).thenReturn(buildUser(3L, "管理员", "ADMIN"));
        requirementBoardService.delete(3L, 100L, 1L);
        verify(requirementMapper, org.mockito.Mockito.times(2)).delete(any());
    }

    @Test
    void pageShouldReturnSharedRequirementsAndAllStatusCounts() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L, "提交者", "USER"));
        when(requirementMapper.selectList(any())).thenReturn(List.of(
                buildRequirement(2L, 2L, "第二条需求", "PLANNED", 1L),
                buildRequirement(1L, 1L, "第一条需求", "PENDING_REVIEW", 1L)
        ));
        when(requirementMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(
                buildUser(1L, "提交者", "USER"), buildUser(2L, "协作者", "USER")
        ));

        RequirementQueryRequest request = new RequirementQueryRequest();
        request.setPageNo(1L);
        request.setPageSize(50L);
        request.setType("BUG");

        RequirementPageVO result = requirementBoardService.page(1L, request);

        assertEquals(2L, result.getTotal());
        assertEquals(2, result.getList().size());
        assertEquals(5, result.getStatusCounts().size());
        assertEquals("协作者", result.getList().get(0).getCreatorName());
        assertEquals("REQUIREMENT", result.getList().get(0).getType());
    }

    private Requirement buildRequirement(Long id, Long creatorUserId, String title, String status, Long version) {
        Requirement requirement = new Requirement();
        requirement.setId(id);
        requirement.setCreatorUserId(creatorUserId);
        requirement.setAppCode("APP_TODO_LIST");
        requirement.setAppName("待办清单");
        requirement.setType("REQUIREMENT");
        requirement.setTitle(title);
        requirement.setPriority("MEDIUM");
        requirement.setStatus(status);
        requirement.setVersion(version);
        requirement.setCreatedAt(LocalDateTime.now().minusDays(1));
        requirement.setUpdatedAt(LocalDateTime.now());
        return requirement;
    }

    private RequirementAppOptionVO buildApp(String appCode, String appName) {
        RequirementAppOptionVO app = new RequirementAppOptionVO();
        app.setAppCode(appCode);
        app.setAppName(appName);
        return app;
    }

    private User buildUser(Long id, String displayName, String roleCode) {
        User user = new User();
        user.setId(id);
        user.setUsername(displayName);
        user.setDisplayName(displayName);
        user.setRoleCode(roleCode);
        return user;
    }
}
