package com.zestflow.executor.context;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 链上下文（DataBus）
 * <p>
 * 存储链执行过程中的全部数据，节点间通过此上下文传递数据。
 * 对标 LiteFlow 的 DataBus + Slot，功能更丰富。
 * <p>
 * <ul>
 *   <li>data: 核心数据总线，节点间传递</li>
 *   <li>headers: 请求头（来源：Admin 调用时的请求头）</li>
 *   <li>metadata: 元数据（实例 ID、链编码、时间戳等，不序列化到事件）</li>
 * </ul>
 */
public class ChainContext {

    /** 执行实例 ID */
    private final String instanceId;

    /** 链编码 */
    private final String chainCode;

    /** 核心数据总线 */
    private final Map<String, Object> data;

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
        this.headers = new ConcurrentHashMap<>();
        this.metadata = new ConcurrentHashMap<>();
        this.startTime = System.currentTimeMillis();
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

    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    // ==================== 快照 ====================

    /**
     * 获取 DataBus 只读快照（用于事件发布给 Collector）
     */
    public Map<String, Object> snapshot() {
        return Collections.unmodifiableMap(new HashMap<>(data));
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
