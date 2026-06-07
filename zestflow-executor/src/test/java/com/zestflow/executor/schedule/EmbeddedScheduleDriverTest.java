package com.zestflow.executor.schedule;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddedScheduleDriverTest {

    @Test
    void shouldOwnWhenShardMatches() {
        SchedulePO schedule = SchedulePO.builder().id(10L).shardTotal(2).build();
        assertThat(EmbeddedScheduleDriver.shouldOwn(schedule, 0, 2)
                || EmbeddedScheduleDriver.shouldOwn(schedule, 1, 2)).isTrue();
    }

    @Test
    void onlyOneShardOwnsSchedule() {
        SchedulePO schedule = SchedulePO.builder().id(10L).shardTotal(4).build();
        int owners = 0;
        for (int i = 0; i < 4; i++) {
            if (EmbeddedScheduleDriver.shouldOwn(schedule, i, 4)) {
                owners++;
            }
        }
        assertThat(owners).isEqualTo(1);
    }
}
