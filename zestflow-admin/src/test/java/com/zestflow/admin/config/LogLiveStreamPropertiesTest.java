package com.zestflow.admin.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogLiveStreamPropertiesTest {

    @Test
    void defaults_matchProductionBaseline() {
        LogLiveStreamProperties props = new LogLiveStreamProperties();

        assertThat(props.getPollIntervalMs()).isEqualTo(2_000L);
        assertThat(props.getSseTimeoutMs()).isEqualTo(600_000L);
        assertThat(props.getPoolSize()).isGreaterThanOrEqualTo(1);
    }
}
