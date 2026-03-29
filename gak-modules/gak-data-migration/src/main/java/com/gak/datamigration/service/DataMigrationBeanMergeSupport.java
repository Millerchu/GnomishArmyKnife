package com.gak.datamigration.service;

import java.beans.PropertyDescriptor;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

/**
 * 迁移导入时的对象合并工具。
 */
public final class DataMigrationBeanMergeSupport {

    private DataMigrationBeanMergeSupport() {
    }

    public static void overwrite(Object source, Object target, String... ignoreProperties) {
        BeanUtils.copyProperties(source, target, ignoreProperties);
    }

    public static void mergeNonNull(Object source, Object target, String... ignoreProperties) {
        BeanUtils.copyProperties(source, target, resolveNullAndIgnoredProperties(source, ignoreProperties));
    }

    private static String[] resolveNullAndIgnoredProperties(Object source, String... ignoreProperties) {
        BeanWrapper wrapper = new BeanWrapperImpl(source);
        Set<String> ignoreSet = new HashSet<>();
        if (ignoreProperties != null) {
            for (String ignoreProperty : ignoreProperties) {
                if (Objects.nonNull(ignoreProperty)) {
                    ignoreSet.add(ignoreProperty);
                }
            }
        }
        for (PropertyDescriptor descriptor : wrapper.getPropertyDescriptors()) {
            String name = descriptor.getName();
            if (wrapper.getPropertyValue(name) == null) {
                ignoreSet.add(name);
            }
        }
        return ignoreSet.toArray(String[]::new);
    }
}
