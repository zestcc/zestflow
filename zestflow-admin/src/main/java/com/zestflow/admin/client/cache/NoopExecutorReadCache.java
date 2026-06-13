package com.zestflow.admin.client.cache;

import java.util.Optional;

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
