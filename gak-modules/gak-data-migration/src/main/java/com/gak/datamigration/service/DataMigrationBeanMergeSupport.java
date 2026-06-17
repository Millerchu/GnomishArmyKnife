package com.gak.datamigration.service;

import java.beans.PropertyDescriptor;
import java.time.LocalDateTime;
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

    public static boolean overwriteNewest(Object source, Object target, String... ignoreProperties) {
        if (!shouldApplySource(source, target)) {
            return false;
        }
        overwrite(source, target, ignoreProperties);
        return true;
    }

    public static boolean mergeNewestNonNull(Object source, Object target, String... ignoreProperties) {
        if (!shouldApplySource(source, target)) {
            return false;
        }
        mergeNonNull(source, target, ignoreProperties);
        return true;
    }

    private static boolean shouldApplySource(Object source, Object target) {
        LocalDateTime sourceTime = readVersionTime(source);
        LocalDateTime targetTime = readVersionTime(target);
        return sourceTime == null || targetTime == null || !sourceTime.isBefore(targetTime);
    }

    private static LocalDateTime readVersionTime(Object bean) {
        if (bean == null) {
            return null;
        }
        BeanWrapper wrapper = new BeanWrapperImpl(bean);
        for (String propertyName : new String[]{"updatedAt", "createdAt"}) {
            if (!wrapper.isReadableProperty(propertyName)) {
                continue;
            }
            Object value = wrapper.getPropertyValue(propertyName);
            if (value instanceof LocalDateTime localDateTime) {
                return localDateTime;
            }
        }
        return null;
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
