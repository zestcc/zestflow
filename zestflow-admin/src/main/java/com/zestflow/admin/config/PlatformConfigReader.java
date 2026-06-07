package com.zestflow.admin.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.model.entity.SysConfigPO;
import com.zestflow.admin.repository.SysConfigMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * 平台级 sys_config 运行时读取（租户 1，带缓存；DB 优先，yaml 兜底）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformConfigReader {

    public static final long PLATFORM_TENANT_ID = 1L;

    private final SysConfigMapper sysConfigMapper;

    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private volatile boolean loaded;

    @PostConstruct
    void warmUp() {
        reload();
    }

    public void invalidate() {
        loaded = false;
        cache.clear();
        reload();
    }

    public void reload() {
        cache.clear();
        sysConfigMapper.selectList(
                new LambdaQueryWrapper<SysConfigPO>()
                        .eq(SysConfigPO::getTenantId, PLATFORM_TENANT_ID)
                        .eq(SysConfigPO::getStatus, 1))
                .forEach(po -> {
                    if (StringUtils.hasText(po.getConfigKey())) {
                        cache.put(po.getConfigKey().trim(), po.getConfigValue());
                    }
                });
        loaded = true;
        log.debug("平台 sys_config 缓存已加载 entries={}", cache.size());
    }

    public String getString(String key, Supplier<String> yamlDefault) {
        String raw = cache.get(key);
        if (StringUtils.hasText(raw)) {
            return raw.trim();
        }
        return yamlDefault != null ? yamlDefault.get() : null;
    }

    public boolean getBoolean(String key, BooleanSupplier yamlDefault) {
        String raw = cache.get(key);
        if (StringUtils.hasText(raw)) {
            return parseBoolean(raw.trim());
        }
        return yamlDefault != null && yamlDefault.getAsBoolean();
    }

    public int getInt(String key, IntSupplier yamlDefault) {
        String raw = cache.get(key);
        if (StringUtils.hasText(raw)) {
            try {
                return Integer.parseInt(raw.trim());
            } catch (NumberFormatException e) {
                log.warn("平台配置 {} 非整数 value={}", key, raw);
            }
        }
        return yamlDefault != null ? yamlDefault.getAsInt() : 0;
    }

    public long getLong(String key, LongSupplier yamlDefault) {
        String raw = cache.get(key);
        if (StringUtils.hasText(raw)) {
            try {
                return Long.parseLong(raw.trim());
            } catch (NumberFormatException e) {
                log.warn("平台配置 {} 非长整数 value={}", key, raw);
            }
        }
        return yamlDefault != null ? yamlDefault.getAsLong() : 0L;
    }

    public double getDouble(String key, DoubleSupplier yamlDefault) {
        String raw = cache.get(key);
        if (StringUtils.hasText(raw)) {
            try {
                return Double.parseDouble(raw.trim());
            } catch (NumberFormatException e) {
                log.warn("平台配置 {} 非浮点数 value={}", key, raw);
            }
        }
        return yamlDefault != null ? yamlDefault.getAsDouble() : 0d;
    }

    private static boolean parseBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
    }
}
