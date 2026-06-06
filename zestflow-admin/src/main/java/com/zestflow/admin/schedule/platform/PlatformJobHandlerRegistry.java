package com.zestflow.admin.schedule.platform;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PlatformJobHandlerRegistry {

    private final Map<String, PlatformJobHandler> handlers = new ConcurrentHashMap<>();

    public void register(String jobKey, PlatformJobHandler handler) {
        handlers.put(jobKey, handler);
    }

    public PlatformJobHandler get(String jobKey) {
        return handlers.get(jobKey);
    }

    public boolean hasHandler(String jobKey) {
        return handlers.containsKey(jobKey);
    }
}
