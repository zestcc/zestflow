package com.zestflow.executor.component.builtin;

import com.zestflow.executor.annotation.*;
import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 内置数据处理组件库。
 * <p>
 * 提供开箱即用的数据转换、过滤、聚合、拆分等功能，
 * 无需用户编写代码即可在流程编排中使用。
 */
@Slf4j
@Component
@ZestComponent("builtin-data")
public class BuiltinDataComponents {

    /**
     * Map 转 POJO —— 从上下文获取数据并转换为目标类型
     */
    @ZestTransformer("transform-mapToBean")
    public Map<String, Object> mapToBean(ChainContext ctx) {
        String sourceKey = (String) ctx.get("_sourceKey");
        String targetKey = (String) ctx.get("_targetKey");
        if (sourceKey == null || targetKey == null) {
            throw new IllegalArgumentException("sourceKey 和 targetKey 不能为空");
        }
        Object source = ctx.get(sourceKey);
        if (source instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = new LinkedHashMap<>((Map<String, Object>) source);
            ctx.put(targetKey, result);
            log.debug("mapToBean 转换完成 sourceKey={} targetKey={}", sourceKey, targetKey);
            return result;
        }
        throw new IllegalArgumentException("数据源不是 Map 类型 sourceKey=" + sourceKey);
    }

    /**
     * 列表过滤 —— 根据条件过滤集合元素
     */
    @ZestFilter("filter-listFilter")
    public List<Object> listFilter(ChainContext ctx) {
        String sourceKey = (String) ctx.get("_sourceKey");
        String filterKey = (String) ctx.get("_filterKey");
        String filterValue = (String) ctx.get("_filterValue");

        if (sourceKey == null) {
            throw new IllegalArgumentException("sourceKey 不能为空");
        }

        Object source = ctx.get(sourceKey);
        if (!(source instanceof Collection)) {
            throw new IllegalArgumentException("数据源不是集合类型 sourceKey=" + sourceKey);
        }

        @SuppressWarnings("unchecked")
        Collection<Object> collection = (Collection<Object>) source;
        if (filterKey == null || filterValue == null) {
            return new ArrayList<>(collection);
        }

        List<Object> filtered = collection.stream()
                .filter(item -> {
                    if (item instanceof Map) {
                        Object val = ((Map<?, ?>) item).get(filterKey);
                        return filterValue.equals(String.valueOf(val));
                    }
                    return false;
                })
                .collect(Collectors.toList());

        log.debug("listFilter 过滤完成 sourceKey={} size={} filtered={}",
                sourceKey, collection.size(), filtered.size());
        return filtered;
    }

    /**
     * 列表去重
     */
    @ZestFilter("filter-distinct")
    public List<Object> distinct(ChainContext ctx) {
        String sourceKey = (String) ctx.get("_sourceKey");
        if (sourceKey == null) {
            throw new IllegalArgumentException("sourceKey 不能为空");
        }

        Object source = ctx.get(sourceKey);
        if (!(source instanceof Collection)) {
            throw new IllegalArgumentException("数据源不是集合类型 sourceKey=" + sourceKey);
        }

        @SuppressWarnings("unchecked")
        Collection<Object> collection = (Collection<Object>) source;
        List<Object> result = new ArrayList<>(new LinkedHashSet<>(collection));

        log.debug("distinct 去重完成 sourceKey={} original={} distinct={}",
                sourceKey, collection.size(), result.size());
        return result;
    }

    /**
     * 聚合器 —— 合并多个数据源
     */
    @ZestAggregator("aggregator-merge")
    public List<Object> merge(ChainContext ctx) {
        String sourceKeysStr = (String) ctx.get("_sourceKeys");
        String targetKey = (String) ctx.get("_targetKey");

        if (sourceKeysStr == null) {
            throw new IllegalArgumentException("sourceKeys 不能为空");
        }

        String[] sourceKeys = sourceKeysStr.split(",");
        List<Object> merged = new ArrayList<>();

        for (String key : sourceKeys) {
            String trimmedKey = key.trim();
            Object value = ctx.get(trimmedKey);
            if (value instanceof Collection) {
                @SuppressWarnings("unchecked")
                Collection<Object> col = (Collection<Object>) value;
                merged.addAll(col);
            } else if (value != null) {
                merged.add(value);
            }
        }

        if (targetKey != null && !targetKey.isEmpty()) {
            ctx.put(targetKey, merged);
        }
        log.debug("aggregator-merge 聚合完成 sourceKeys={} size={}", sourceKeysStr, merged.size());
        return merged;
    }

    /**
     * 拆分器 —— 按指定大小拆分集合
     */
    @ZestSplitter("splitter-chunk")
    public List<List<Object>> chunk(ChainContext ctx) {
        String sourceKey = (String) ctx.get("_sourceKey");
        Object chunkSizeObj = ctx.get("_chunkSize");
        int chunkSize = chunkSizeObj instanceof Number ? ((Number) chunkSizeObj).intValue() : 100;

        if (sourceKey == null) {
            throw new IllegalArgumentException("sourceKey 不能为空");
        }

        Object source = ctx.get(sourceKey);
        if (!(source instanceof List)) {
            throw new IllegalArgumentException("数据源不是 List 类型 sourceKey=" + sourceKey);
        }

        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) source;
        List<List<Object>> chunks = new ArrayList<>();

        for (int i = 0; i < list.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, list.size());
            chunks.add(new ArrayList<>(list.subList(i, end)));
        }

        log.debug("splitter-chunk 拆分完成 sourceKey={} total={} chunks={} chunkSize={}",
                sourceKey, list.size(), chunks.size(), chunkSize);
        return chunks;
    }
}