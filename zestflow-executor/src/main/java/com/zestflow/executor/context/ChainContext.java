package com.zestflow.executor.context;

import lombok.extern.slf4j.Slf4j;

import com.zestflow.common.constant.ChainConstants;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * 链上下文（DataBus）
 * <p>
 * 存储链执行过程中的全部数据，节点间通过此上下文传递数据。
 * 对标 LiteFlow 的 DataBus + Slot，功能更丰富。
 * <p>
 * <ul>
 *   <li>data: 核心数据总线，节点间传递</li>
 *   <li>typedData: 类型化数据，元件方法可按类型直接注入</li>
 *   <li>headers: 请求头（来源：Admin 调用时的请求头）</li>
 *   <li>metadata: 元数据（实例 ID、链编码、时间戳等，不序列化到事件）</li>
 * </ul>
 */
@Slf4j
public class ChainContext {

    /** 执行实例 ID */
    private final String instanceId;

    /** 链编码 */
    private final String chainCode;

    /** 核心数据总线 */
    private final Map<String, Object> data;

    /** 类型化数据（按 Class 索引，供元件参数注入） */
    private final Map<Class<?>, Object> typedData;

    /** 请求头 */
    private final Map<String, Object> headers;

    /** 元数据（不可序列化到事件） */
    private final Map<String, Object> metadata;

    /** 开始时间戳 */
    private final long startTime;

    public ChainContext(String instanceId, String chainCode, Map<String, Object> initialData) {
        this.instanceId = instanceId;
        this.chainCode = chainCode;
        this.data = new ConcurrentHashMap<>(initialData != null ? initialData : Map.of());
        this.typedData = new ConcurrentHashMap<>();
        this.headers = new ConcurrentHashMap<>();
        this.metadata = new ConcurrentHashMap<>();
        this.startTime = System.currentTimeMillis();
    }

    /** 并行层 fork：共享实例元信息，拷贝 DataBus 快照（避免同层并行写共享 Map） */
    private ChainContext(ChainContext parent) {
        this.instanceId = parent.instanceId;
        this.chainCode = parent.chainCode;
        this.startTime = parent.startTime;
        this.data = new ConcurrentHashMap<>(parent.data);
        this.typedData = new ConcurrentHashMap<>(parent.typedData);
        this.headers = new ConcurrentHashMap<>(parent.headers);
        this.metadata = new ConcurrentHashMap<>(parent.metadata);
    }

    /**
     * 为同层并行节点创建独立上下文副本。
     */
    public ChainContext fork() {
        return new ChainContext(this);
    }

    /**
     * 将 fork 分支上的 DataBus / 类型化数据合并回主上下文（层末归并）。
     */
    public void mergeFrom(ChainContext fork) {
        if (fork == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : fork.data.entrySet()) {
            String key = entry.getKey();
            Object newVal = entry.getValue();
            Object oldVal = data.get(key);
            if (oldVal != null && newVal != null && !Objects.equals(oldVal, newVal)) {
                log.debug("并行层 merge 覆盖 key={} old={} new={}", key, oldVal, newVal);
            }
            data.put(key, newVal);
        }
        fork.typedData.values().forEach(this::register);
    }

    // ==================== 类型化数据（供元件参数按类型注入） ====================

    /**
     * 注册类型化对象到上下文。
     * 元件方法声明对应类型参数时，由 ContextTypeResolver 自动注入。
     */
    public void register(Object bean) {
        if (bean != null) {
            typedData.put(bean.getClass(), bean);
        }
    }

    /**
     * 按类型获取注册的对象，支持超类/接口兜底匹配。
     */
    @SuppressWarnings("unchecked")
    public <T> T getTyped(Class<T> type) {
        Object val = typedData.get(type);
        if (val != null) return (T) val;
        // 超类/接口兜底
        for (Map.Entry<Class<?>, Object> entry : typedData.entrySet()) {
            if (type.isAssignableFrom(entry.getKey())) {
                return (T) entry.getValue();
            }
        }
        return null;
    }

    // ==================== 基础存取 ====================

    /**
     * 获取值
     */
    public Object get(String key) {
        return data.get(key);
    }

    /**
     * 获取值（类型安全）
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object val = data.get(key);
        if (type.isInstance(val)) {
            return (T) val;
        }
        return null;
    }

    /**
     * 设置值
     */
    public void put(String key, Object value) {
        data.put(key, value);
    }

    /**
     * 获取值（带默认值）
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(String key, Class<T> type, T defaultValue) {
        T val = get(key, type);
        return val != null ? val : defaultValue;
    }

    /**
     * 批量设置
     */
    public void putAll(Map<String, Object> map) {
        data.putAll(map);
    }

    /**
     * 是否包含键
     */
    public boolean contains(String key) {
        return data.containsKey(key);
    }

    /**
     * 移除键
     */
    public Object remove(String key) {
        return data.remove(key);
    }

    /**
     * 发布数据到 DataBus（供下游节点消费）
     */
    public void publish(String key, Object value) {
        data.put(key, value);
    }

    /**
     * 消费并移除数据
     */
    @SuppressWarnings("unchecked")
    public <T> T consume(String key, Class<T> type) {
        Object val = data.remove(key);
        if (type.isInstance(val)) {
            return (T) val;
        }
        return null;
    }

    // ==================== 请求头操作 ====================

    public void setHeader(String key, Object value) {
        headers.put(key, value);
    }

    public Object getHeader(String key) {
        return headers.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getHeader(String key, Class<T> type) {
        Object val = headers.get(key);
        if (type.isInstance(val)) {
            return (T) val;
        }
        return null;
    }

    // ==================== 元数据操作 ====================

    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    public void removeMetadata(String key) {
        metadata.remove(key);
    }

    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    /** 是否已被外部 stop() 终止（引擎在实例创建后注入 META_STOP_CHECK） */
    public boolean isExecutionStopped() {
        Object check = metadata.get(ChainConstants.META_STOP_CHECK);
        if (check instanceof BooleanSupplier supplier) {
            return supplier.getAsBoolean();
        }
        return false;
    }

    // ==================== 快照 ====================

    /**
     * 获取 DataBus 只读快照（用于事件发布给 Collector）
     */
    public Map<String, Object> snapshot() {
        return Collections.unmodifiableMap(new HashMap<>(data));
    }

    /**
     * 获取类型化数据快照（用于执行结果回传）
     */
    public Map<Class<?>, Object> typedSnapshot() {
        return new HashMap<>(typedData);
    }

    /**
     * 获取完整上下文快照（含元数据）
     */
    public Map<String, Object> fullSnapshot() {
        Map<String, Object> full = new HashMap<>();
        full.put("instanceId", instanceId);
        full.put("chainCode", chainCode);
        full.put("data", new HashMap<>(data));
        full.put("startTime", startTime);
        full.put("elapsed", System.currentTimeMillis() - startTime);
        return Collections.unmodifiableMap(full);
    }

    // ==================== getters ====================

    public String getInstanceId() {
        return instanceId;
    }

    public String getChainCode() {
        return chainCode;
    }

    public long getStartTime() {
        return startTime;
    }

    /**
     * 获取已执行时长（毫秒）
     */
    public long getElapsedMs() {
        return System.currentTimeMillis() - startTime;
    }
}
