package com.zestflow.admin.config;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlaygroundPlatformConfig {

    private final PlatformConfigReader platformConfig;
    private final Environment environment;

    public boolean isEnabled() {
        return platformConfig.getBoolean(SysConfigKeys.PLAYGROUND_ENABLED,
                () -> environment.getProperty("zestflow.playground.enabled", Boolean.class, Boolean.TRUE));
    }

    public int getExecuteTimeoutMs() {
        return platformConfig.getInt(SysConfigKeys.PLAYGROUND_EXECUTE_TIMEOUT_MS,
                () -> environment.getProperty("zestflow.playground.execute-timeout-ms", Integer.class, 30_000));
    }

    public int getRateLimit() {
        return platformConfig.getInt(SysConfigKeys.PLAYGROUND_RATE_LIMIT,
                () -> environment.getProperty("zestflow.playground.rate-limit", Integer.class, 30));
    }
}
