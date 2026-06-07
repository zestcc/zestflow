package com.zestflow.executor.schedule;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleShardSupportTest {

    @Test
    void singleShardOwnsAll() {
        assertThat(ScheduleShardSupport.ownsSchedule(42L, 1, 0)).isTrue();
    }

    @Test
    void distributesByScheduleId() {
        int total = 3;
        int owner = -1;
        for (int i = 0; i < total; i++) {
            if (ScheduleShardSupport.ownsSchedule(100L, total, i)) {
                owner = i;
            }
        }
        assertThat(owner).isGreaterThanOrEqualTo(0);
    }

    @Test
    void rejectsInvalidShardIndex() {
        assertThat(ScheduleShardSupport.ownsSchedule(1L, 3, 3)).isFalse();
    }
}
