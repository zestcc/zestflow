package com.zestflow.admin.client.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@ConditionalOnProperty(prefix = "zestflow.admin.executor-read-cache", name = "enabled", havingValue = "false")
public class NoopExecutorReadCache implements ExecutorReadCache {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public Optional<Entry> get(String cacheKey) {
        return Optional.empty();
    }

    @Override
    public void put(String cacheKey, String json) {
        // no-op
    }

    @Override
    public void invalidateApp(String appCode) {
        // no-op
    }
}
