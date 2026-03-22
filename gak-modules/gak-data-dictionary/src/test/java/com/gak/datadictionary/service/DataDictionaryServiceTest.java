package com.gak.datadictionary.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gak.datadictionary.domain.DataDictionary;
import com.gak.datadictionary.domain.DataDictionaryItem;
import com.gak.datadictionary.dto.DataDictionaryQueryRequest;
import com.gak.datadictionary.dto.SaveDataDictionaryItemRequest;
import com.gak.datadictionary.dto.SaveDataDictionaryRequest;
import com.gak.datadictionary.mapper.DataDictionaryItemMapper;
import com.gak.datadictionary.mapper.DataDictionaryMapper;
import com.gak.datadictionary.mapper.DataDictionaryUsageMapper;
import com.gak.datadictionary.vo.DataDictionaryVO;
import com.gak.framework.exception.BusinessException;
import com.gak.framework.response.PagedResult;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataDictionaryServiceTest {

    @Mock
    private DataDictionaryMapper dataDictionaryMapper;

    @Mock
    private DataDictionaryItemMapper dataDictionaryItemMapper;

    @Mock
    private DataDictionaryUsageMapper dataDictionaryUsageMapper;

    @Mock
    private UserMapper userMapper;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private DataDictionaryService dataDictionaryService;

    @Test
    void pageShouldReturnReferenceAppsAndItemCount() {
        when(userMapper.selectById(1L)).thenReturn(buildAdminUser(1L));
        when(dataDictionaryMapper.selectList(any())).thenReturn(List.of(
                buildDictionary(3001L, "WORK_LOG_TYPE", "工作日志类型"),
                buildDictionary(3002L, "APP_SECURITY_LEVEL", "应用密级")
        ));
        when(dataDictionaryItemMapper.selectList(any())).thenReturn(List.of(
                buildItem(3101L, 3001L, "DEVELOP", "DEVELOP", true),
                buildItem(3102L, 3001L, "MEETING", "MEETING", false),
                buildItem(3103L, 3002L, "PUBLIC", "PUBLIC", true)
        ));

        DataDictionaryQueryRequest request = new DataDictionaryQueryRequest();
        request.setPageNo(1L);
        request.setPageSize(10L);

        PagedResult<DataDictionaryVO> result = dataDictionaryService.page(1L, request);

        assertEquals(2, result.list().size());
        assertEquals(2, result.list().get(0).getItemCount());
        assertIterableEquals(List.of("工作日志"), result.list().get(0).getReferenceApps());
    }

    @Test
    void createShouldPersistDictionary() {
        when(userMapper.selectById(1L)).thenReturn(buildAdminUser(1L));
        when(dataDictionaryMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            DataDictionary dictionary = invocation.getArgument(0);
            dictionary.setId(3001L);
            return 1;
        }).when(dataDictionaryMapper).insert(any(DataDictionary.class));

        DataDictionaryVO result = dataDictionaryService.create(1L, buildSaveDictionaryRequest());

        ArgumentCaptor<DataDictionary> captor = ArgumentCaptor.forClass(DataDictionary.class);
        verify(dataDictionaryMapper).insert(captor.capture());
        assertEquals("WORK_LOG_TYPE", captor.getValue().getDictCode());
        assertEquals("系统管理员", captor.getValue().getCreatorName());
        assertIterableEquals(List.of("工作日志"), result.getReferenceApps());
    }

    @Test
    void updateShouldRejectDictCodeChange() {
        when(userMapper.selectById(1L)).thenReturn(buildAdminUser(1L));
        when(dataDictionaryMapper.selectOne(any())).thenReturn(buildDictionary(3001L, "WORK_LOG_TYPE", "工作日志类型"));

        SaveDataDictionaryRequest request = buildSaveDictionaryRequest();
        request.setDictCode("APP_SECURITY_LEVEL");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> dataDictionaryService.update(1L, 3001L, request));
        assertEquals("DICT_CODE_IMMUTABLE", exception.getCode());
    }

    @Test
    void deleteShouldRejectDictionaryInUse() {
        when(userMapper.selectById(1L)).thenReturn(buildAdminUser(1L));
        when(dataDictionaryMapper.selectOne(any())).thenReturn(buildDictionary(3001L, "WORK_LOG_TYPE", "工作日志类型"));
        when(dataDictionaryUsageMapper.selectCount(any())).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> dataDictionaryService.delete(1L, 3001L));
        assertEquals("DICT_IN_USE", exception.getCode());
    }

    @Test
    void createItemShouldClearPreviousDefault() {
        when(userMapper.selectById(1L)).thenReturn(buildAdminUser(1L));
        when(dataDictionaryMapper.selectOne(any())).thenReturn(buildDictionary(3001L, "WORK_LOG_TYPE", "工作日志类型"));
        when(dataDictionaryItemMapper.selectCount(any())).thenReturn(0L, 0L);
        doAnswer(invocation -> {
            DataDictionaryItem item = invocation.getArgument(0);
            item.setId(3101L);
            return 1;
        }).when(dataDictionaryItemMapper).insert(any(DataDictionaryItem.class));

        dataDictionaryService.createItem(1L, 3001L, buildSaveItemRequest());

        verify(dataDictionaryItemMapper).update(any(DataDictionaryItem.class), any());
        verify(dataDictionaryItemMapper).insert(any(DataDictionaryItem.class));
    }

    @Test
    void createItemShouldRejectDuplicateValue() {
        when(userMapper.selectById(1L)).thenReturn(buildAdminUser(1L));
        when(dataDictionaryMapper.selectOne(any())).thenReturn(buildDictionary(3001L, "WORK_LOG_TYPE", "工作日志类型"));
        when(dataDictionaryItemMapper.selectCount(any())).thenReturn(0L, 1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> dataDictionaryService.createItem(1L, 3001L, buildSaveItemRequest()));
        assertEquals("DICT_ITEM_VALUE_EXISTS", exception.getCode());
    }

    @Test
    void pageShouldRejectNonAdmin() {
        User user = new User();
        user.setId(2L);
        user.setRoleCode("USER");
        when(userMapper.selectById(2L)).thenReturn(user);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> dataDictionaryService.page(2L, new DataDictionaryQueryRequest()));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    private User buildAdminUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("admin");
        user.setDisplayName("系统管理员");
        user.setRoleCode("ADMIN");
        return user;
    }

    private DataDictionary buildDictionary(Long id, String dictCode, String dictName) {
        DataDictionary dictionary = new DataDictionary();
        dictionary.setId(id);
        dictionary.setDictCode(dictCode);
        dictionary.setDictName(dictName);
        dictionary.setStatus("ENABLED");
        dictionary.setReferenceAppsJson("[\"工作日志\"]");
        dictionary.setDescription("说明");
        dictionary.setCreatorName("系统管理员");
        dictionary.setCreatedAt(LocalDateTime.now());
        dictionary.setUpdatedAt(LocalDateTime.now());
        dictionary.setDeleted(false);
        return dictionary;
    }

    private DataDictionaryItem buildItem(Long id, Long dictionaryId, String itemCode, String itemValue, boolean isDefault) {
        DataDictionaryItem item = new DataDictionaryItem();
        item.setId(id);
        item.setDictionaryId(dictionaryId);
        item.setDictCode("WORK_LOG_TYPE");
        item.setItemCode(itemCode);
        item.setItemLabel(itemCode);
        item.setItemValue(itemValue);
        item.setSortNo(1);
        item.setStatus("ENABLED");
        item.setIsDefault(isDefault);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        item.setDeleted(false);
        return item;
    }

    private SaveDataDictionaryRequest buildSaveDictionaryRequest() {
        SaveDataDictionaryRequest request = new SaveDataDictionaryRequest();
        request.setDictCode("WORK_LOG_TYPE");
        request.setDictName("工作日志类型");
        request.setStatus("ENABLED");
        request.setEnabled(true);
        request.setReferenceApps(List.of("工作日志"));
        request.setDescription("工作日志类型选项");
        return request;
    }

    private SaveDataDictionaryItemRequest buildSaveItemRequest() {
        SaveDataDictionaryItemRequest request = new SaveDataDictionaryItemRequest();
        request.setItemCode("develop");
        request.setItemLabel("开发");
        request.setItemValue("DEVELOP");
        request.setSort(1);
        request.setStatus("ENABLED");
        request.setEnabled(true);
        request.setIsDefault(true);
        request.setDescription("默认项");
        return request;
    }
}
