package com.zestflow.admin.schedule;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Admin 调度触发幂等键 — 与 Executor {@code idempotencyKey} 对齐。
 */
public final class ScheduleIdempotencyKeys {

    private ScheduleIdempotencyKeys() {
    }

    /** cron 触发：同一 fire 时间在 Executor TTL 内只执行一次 */
    public static String forCronFire(long scheduleId, LocalDateTime fireTime) {
        long epochMs = fireTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return "schedule-" + scheduleId + "-cron-" + epochMs;
    }

    /** 手动触发：每次点击独立键，允许重复执行 */
    public static String forManualTrigger(long scheduleId) {
        return "schedule-" + scheduleId + "-manual-" + UUID.randomUUID();
    }
}
