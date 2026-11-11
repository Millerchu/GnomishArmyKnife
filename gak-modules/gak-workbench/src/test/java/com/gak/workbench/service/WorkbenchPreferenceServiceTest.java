package com.gak.workbench.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import com.gak.workbench.domain.UserPreference;
import com.gak.workbench.dto.WorkbenchPreferenceRequest;
import com.gak.workbench.exception.WorkbenchPreferenceConflictException;
import com.gak.workbench.mapper.UserPreferenceMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 服务编排使用 mapper mock，真实 SQL/JSONB 另由显式开启的 PG 用例验证。 */
class WorkbenchPreferenceServiceTest {
    private UserPreferenceMapper mapper;
    private UserMapper userMapper;
    private WorkbenchPreferenceService service;

    @BeforeEach
    void setup() {
        mapper = mock(UserPreferenceMapper.class);
        userMapper = mock(UserMapper.class);
        when(userMapper.selectById(anyLong())).thenReturn(new User());
        service = new WorkbenchPreferenceService(mapper, new WorkbenchPayloadNormalizer(), userMapper);
    }

    @Test
    void returnsEmptyDocumentAndOnlyQueriesCurrentUserAndFixedKey() {
        var empty = service.get(10L);
        assertEquals(0, empty.version());
        assertEquals(1, empty.schemaVersion());
        assertNull(empty.payload());
        verify(mapper).find(10L, "workbench");
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void createsVersionOneWithAssignedIdAndNormalizedPayload() {
        when(mapper.insertIfAbsent(any())).thenReturn(1);
        var result = service.save(10L, request(0));
        assertEquals(1, result.version());
        assertNotNull(result.updatedAt());
        var captured = ArgumentCaptor.forClass(UserPreference.class);
        verify(mapper).insertIfAbsent(captured.capture());
        UserPreference stored = captured.getValue();
        assertNotNull(stored.getId());
        assertEquals(10L, stored.getUserId());
        assertEquals("workbench", stored.getPrefKey());
        assertEquals(1, stored.getSchemaVersion());
        assertTrue(stored.getPayload().has("cards"));
    }

    @Test
    void updateSendsExpectedVersionAndReturnsIncrementedVersion() {
        when(mapper.updateVersion(any())).thenAnswer(invocation -> {
            UserPreference update = invocation.getArgument(0);
            assertEquals(3L, update.getVersion());
            assertEquals(10L, update.getUserId());
            assertEquals("workbench", update.getPrefKey());
            return 1;
        });
        assertEquals(4, service.save(10L, request(3)).version());
        verify(mapper, never()).insertIfAbsent(any());
    }

    @Test
    void staleUpdateAndConcurrentCreationReturnLatestDocument() {
        UserPreference latest = row(4);
        when(mapper.find(10L, "workbench")).thenReturn(latest);
        for (long version : new long[]{0, 2}) {
            var conflict = assertThrows(WorkbenchPreferenceConflictException.class,
                    () -> service.save(10L, request(version)));
            assertEquals(4, conflict.getLatest().version());
            assertTrue(conflict.getLatest().payload().has("dock"));
            assertFalse(conflict.getMessage().contains("未登录"));
        }
    }

    @Test
    void deletedCurrentUserIsRejectedBeforeReadingOrValidatingWrites() {
        when(userMapper.selectById(10L)).thenReturn(null);
        ResponseStatusException read = assertThrows(ResponseStatusException.class, () -> service.get(10L));
        ResponseStatusException write = assertThrows(ResponseStatusException.class, () -> service.save(10L, null));
        for (ResponseStatusException exception : new ResponseStatusException[]{read, write}) {
            assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
            assertEquals("当前登录用户不存在", exception.getReason());
        }
        verifyNoInteractions(mapper);
    }

    @Test
    void futureStoredSchemaPreservesMetadataInReadAndBothConflictPaths() {
        UserPreference latest = row(4);
        latest.setSchemaVersion(2);
        when(mapper.find(10L, "workbench")).thenReturn(latest);
        var read = service.get(10L);
        assertEquals(4, read.version());
        assertEquals(2, read.schemaVersion());
        assertEquals(latest.getUpdatedAt(), read.updatedAt());
        assertNull(read.payload());
        for (long version : new long[]{0, 2}) {
            var conflict = assertThrows(WorkbenchPreferenceConflictException.class,
                    () -> service.save(10L, request(version)));
            assertEquals(read, conflict.getLatest());
        }
    }

    @Test
    void missingRowOnUpdateReturnsEmptyConflictAndDoesNotRecreate() {
        var conflict = assertThrows(WorkbenchPreferenceConflictException.class,
                () -> service.save(10L, request(7)));
        assertEquals(0, conflict.getLatest().version());
        assertNull(conflict.getLatest().payload());
        verify(mapper, never()).insertIfAbsent(any());
    }

    private WorkbenchPreferenceRequest request(long version) {
        return new WorkbenchPreferenceRequest(version, 1, JsonNodeFactory.instance.objectNode());
    }

    public static UserPreference row(long version) {
        UserPreference row = new UserPreference();
        row.setUserId(10L);
        row.setPrefKey("workbench");
        row.setVersion(version);
        row.setSchemaVersion(1);
        row.setPayload(JsonNodeFactory.instance.objectNode());
        row.setUpdatedAt(LocalDateTime.of(2026, 11, 11, 12, 0));
        return row;
    }
}
