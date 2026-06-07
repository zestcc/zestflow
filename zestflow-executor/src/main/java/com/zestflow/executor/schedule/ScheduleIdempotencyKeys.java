package com.zestflow.executor.schedule;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** Cron 幂等键 — 与 Admin v0.1 {@code ScheduleIdempotencyKeys} 对齐 */
public final class ScheduleIdempotencyKeys {

    private ScheduleIdempotencyKeys() {}

    public static String forCronFire(long scheduleId, LocalDateTime fireTime) {
        long epochMs = fireTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return "schedule-" + scheduleId + "-cron-" + epochMs;
    }

    public static String forManualTrigger(long scheduleId) {
        return "schedule-" + scheduleId + "-manual-" + System.nanoTime();
    }
}
