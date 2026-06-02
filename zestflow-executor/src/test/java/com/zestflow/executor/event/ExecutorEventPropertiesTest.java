package com.zestflow.executor.event;

import com.zestflow.collector.async.AsyncCollectorSettings;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutorEventPropertiesTest {

    @Test
    void toSettings_mapsAllFieldsIncludingDrainWorkers() {
        ExecutorEventProperties props = new ExecutorEventProperties();
        props.setBatchSize(100);
        props.setBatchMaxWaitMs(250);
        props.setQueueCapacity(4096);
        props.setDiskFallbackEnabled(true);
        props.setDiskFallbackDir("/tmp/fallback");
        props.setCircuitBreakerThreshold(5);
        props.setCircuitBreakerCooldownMs(15_000);
        props.setShutdownTimeoutMs(8000);
        props.setDiskReplayIntervalMs(3000);
        props.setDrainWorkerCount(3);

        AsyncCollectorSettings settings = props.toSettings();

        assertThat(settings.batchSize()).isEqualTo(100);
        assertThat(settings.batchMaxWaitMs()).isEqualTo(250);
        assertThat(settings.queueCapacity()).isEqualTo(4096);
        assertThat(settings.diskFallbackEnabled()).isTrue();
        assertThat(settings.diskFallbackDir()).isEqualTo("/tmp/fallback");
        assertThat(settings.circuitBreakerThreshold()).isEqualTo(5);
        assertThat(settings.circuitBreakerCooldownMs()).isEqualTo(15_000);
        assertThat(settings.shutdownTimeoutMs()).isEqualTo(8000);
        assertThat(settings.diskReplayIntervalMs()).isEqualTo(3000);
        assertThat(settings.drainWorkerCount()).isEqualTo(3);
    }

    @Test
    void toSettings_normalizesZeroDrainWorkersToOne() {
        ExecutorEventProperties props = new ExecutorEventProperties();
        props.setDrainWorkerCount(0);

        assertThat(props.toSettings().drainWorkerCount()).isEqualTo(1);
    }
}
