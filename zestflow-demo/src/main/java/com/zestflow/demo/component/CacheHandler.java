package com.zestflow.demo.component;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestExecute;
import com.zestflow.executor.annotation.ZestParam;
import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 缓存操作类元件示例
 * 模拟 CACHE_READER 和 CACHE_WRITER 的使用场景
 */
@Slf4j
@ZestComponent("cache")
public class CacheHandler {

    // 模拟内存缓存
    private static final Map<String, Object> cacheStore = new HashMap<>();

    @ZestExecute(value = "getUserCache", name = "获取用户缓存")
    public Map<String, Object> getUserCache(@ZestParam(value = "userId") String userId) {
        String key = "user:" + userId;
        Object cached = cacheStore.get(key);
        if (cached != null) {
            log.info("命中用户缓存 userId={}", userId);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) cached;
            return result;
        }
        log.info("用户缓存未命中 userId={}", userId);
        return null;
    }

    @ZestExecute(value = "setUserCache", name = "设置用户缓存")
    public void setUserCache(
            @ZestParam(value = "userId") String userId,
            @ZestParam(value = "userName") String userName,
            @ZestParam(value = "userStatus", defaultValue = "ACTIVE") String userStatus) {
        String key = "user:" + userId;
        Map<String, Object> userData = Map.of(
                "userId", userId,
                "userName", userName,
                "userStatus", userStatus,
                "cacheTime", System.currentTimeMillis()
        );
        cacheStore.put(key, userData);
        log.info("设置用户缓存 userId={}", userId);
    }

    @ZestExecute(value = "getOrderCache", name = "获取订单缓存")
    public Map<String, Object> getOrderCache(@ZestParam(value = "orderId") String orderId) {
        String key = "order:" + orderId;
        Object cached = cacheStore.get(key);
        if (cached != null) {
            log.info("命中订单缓存 orderId={}", orderId);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) cached;
            return result;
        }
        log.info("订单缓存未命中 orderId={}", orderId);
        return null;
    }

    @ZestExecute(value = "setOrderCache", name = "设置订单缓存")
    public void setOrderCache(
            @ZestParam(value = "orderId") String orderId,
            @ZestParam(value = "status") String status,
            @ZestParam(value = "amount") double amount) {
        String key = "order:" + orderId;
        Map<String, Object> orderData = Map.of(
                "orderId", orderId,
                "status", status,
                "amount", amount,
                "cacheTime", System.currentTimeMillis()
        );
        cacheStore.put(key, orderData);
        log.info("设置订单缓存 orderId={}", orderId);
    }

    @ZestExecute(value = "getConfigCache", name = "获取配置缓存")
    public String getConfigCache(@ZestParam(value = "configKey") String configKey) {
        String key = "config:" + configKey;
        Object cached = cacheStore.get(key);
        if (cached != null) {
            log.info("命中配置缓存 key={}", configKey);
            return cached.toString();
        }
        log.info("配置缓存未命中 key={}", configKey);
        return null;
    }

    @ZestExecute(value = "setConfigCache", name = "设置配置缓存")
    public void setConfigCache(
            @ZestParam(value = "configKey") String configKey,
            @ZestParam(value = "configValue") String configValue) {
        String key = "config:" + configKey;
        cacheStore.put(key, configValue);
        log.info("设置配置缓存 key={}", configKey);
    }

    @ZestExecute(value = "removeCache", name = "删除缓存")
    public boolean removeCache(@ZestParam(value = "cacheKey") String cacheKey) {
        Object removed = cacheStore.remove(cacheKey);
        boolean existed = removed != null;
        log.info("删除缓存 key={} existed={}", cacheKey, existed);
        return existed;
    }

    @ZestExecute(value = "invalidateUserCache", name = "失效用户缓存")
    public void invalidateUserCache(@ZestParam(value = "userId") String userId) {
        String key = "user:" + userId;
        cacheStore.remove(key);
        log.info("失效用户缓存 userId={}", userId);
    }

    @ZestExecute(value = "invalidateOrderCache", name = "失效订单缓存")
    public void invalidateOrderCache(@ZestParam(value = "orderId") String orderId) {
        String key = "order:" + orderId;
        cacheStore.remove(key);
        log.info("失效订单缓存 orderId={}", orderId);
    }

    @ZestExecute(value = "checkCacheExists", name = "检查缓存是否存在")
    public boolean checkCacheExists(@ZestParam(value = "cacheKey") String cacheKey) {
        boolean exists = cacheStore.containsKey(cacheKey);
        log.info("检查缓存存在性 key={} exists={}", cacheKey, exists);
        return exists;
    }

    @ZestExecute(value = "getCacheSize", name = "获取缓存大小")
    public int getCacheSize() {
        int size = cacheStore.size();
        log.info("获取缓存大小 size={}", size);
        return size;
    }

    @ZestExecute(value = "clearAllCache", name = "清空所有缓存")
    public void clearAllCache() {
        int size = cacheStore.size();
        cacheStore.clear();
        log.info("清空所有缓存 cleared={}", size);
    }

    /**
     * 演示从上下文读取缓存键并获取缓存值
     */
    @ZestExecute(value = "readCacheFromContext", name = "从上下文读取缓存")
    public Object readCacheFromContext(ChainContext ctx) {
        String cacheKey = ctx.get("cacheKey", String.class);
        if (cacheKey != null) {
            Object value = cacheStore.get(cacheKey);
            if (value != null) {
                ctx.put("cacheValue", value);
            }
            log.info("从上下文读取缓存 key={} value={}", cacheKey, value);
            return value;
        }
        return null;
    }

    /**
     * 演示将结果写入缓存
     */
    @ZestExecute(value = "writeResultToCache", name = "将结果写入缓存")
    public void writeResultToCache(
            ChainContext ctx,
            @ZestParam(value = "cacheKey", required = false) String cacheKey) {
        if (cacheKey == null || cacheKey.isBlank()) {
            cacheKey = ctx.get("cacheKey", String.class);
        }
        Object result = ctx.get("result");
        if (cacheKey != null && result != null) {
            cacheStore.put(cacheKey, result);
            log.info("将结果写入缓存 key={}", cacheKey);
        }
    }
}