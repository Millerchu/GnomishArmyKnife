package com.gak.datadictionary.support;

import com.gak.datadictionary.cache.DataDictionaryCacheSupport;
import com.gak.framework.dictionary.DataDictionarySupport;
import com.gak.framework.dictionary.vo.DictionaryUsageBindingVO;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataDictionaryUsageSupportImplTest {

    @Mock
    private com.gak.datadictionary.mapper.DataDictionaryUsageMapper dataDictionaryUsageMapper;

    @Mock
    private DataDictionarySupport dataDictionarySupport;

    @Mock
    private DataDictionaryCacheSupport dataDictionaryCacheSupport;

    @InjectMocks
    private DataDictionaryUsageSupportImpl dataDictionaryUsageSupport;

    @Test
    void normalizeMultiValueByUsageShouldUseBoundDictionary() {
        DictionaryUsageBindingVO bindingVO = new DictionaryUsageBindingVO();
        bindingVO.setDictCode("TODO_STATUS");
        bindingVO.setValueMode("ITEM_VALUE");
        when(dataDictionaryCacheSupport.getOrLoadUsageBinding(eq("APP_TODO_LIST|TODO_ITEM|status"), any()))
                .thenAnswer(invocation -> {
                    Supplier<DictionaryUsageBindingVO> loader = invocation.getArgument(1);
                    return bindingVO != null ? bindingVO : loader.get();
                });
        when(dataDictionarySupport.normalizeItemValue("TODO_STATUS", "completed", true)).thenReturn("COMPLETED");
        when(dataDictionarySupport.normalizeItemValue("TODO_STATUS", "todo", true)).thenReturn("TODO");

        List<String> result = dataDictionaryUsageSupport.normalizeMultiValueByUsage(
                "APP_TODO_LIST",
                "TODO_ITEM",
                "status",
                List.of("completed", "todo", "completed"),
                true
        );

        assertEquals(List.of("COMPLETED", "TODO"), result);
    }
}
