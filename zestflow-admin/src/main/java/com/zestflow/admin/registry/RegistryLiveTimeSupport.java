package com.zestflow.admin.registry;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.OptionalLong;

/** 注册表 lastHeartbeat 展示 — 优先内存存活时间，其次 DB 字段。 */
public final class RegistryLiveTimeSupport {

    private RegistryLiveTimeSupport() {
    }

    public static LocalDateTime resolveLastHeartbeat(RegistryLiveStore liveStore,
                                                     String id,
                                                     LocalDateTime dbValue,
                                                     boolean executor) {
        OptionalLong epoch = executor
                ? liveStore.executorLastSeenEpochMs(id)
                : liveStore.collectorLastSeenEpochMs(id);
        if (epoch.isPresent()) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch.getAsLong()), ZoneId.systemDefault());
        }
        return dbValue;
    }
}
