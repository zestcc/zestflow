package com.zestflow.executor.schedule;

/**
 * 调度分片 — scheduleId 哈希取模，保证同 app 多 Executor 不重复触发。
 */
public final class ScheduleShardSupport {

    private ScheduleShardSupport() {}

    public static boolean ownsSchedule(long scheduleId, int shardTotal, int shardIndex) {
        if (shardTotal <= 1) {
            return true;
        }
        if (shardIndex < 0 || shardIndex >= shardTotal) {
            return false;
        }
        int mod = Math.floorMod(Long.hashCode(scheduleId), shardTotal);
        return mod == shardIndex;
    }

    public static int effectiveShardTotal(Integer configured, int executorShardTotal) {
        if (configured != null && configured > 0) {
            return configured;
        }
        return Math.max(1, executorShardTotal);
    }
}
