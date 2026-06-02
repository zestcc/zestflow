package com.zestflow.collector.jdbc.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CollectorPropertiesAsyncSettingsTest {

    @Test
    void toAsyncSettings_mapsPoolSizeToDrainWorkerCount() {
        CollectorProperties props = new CollectorProperties();
        props.setPoolSize(4);
        props.setBatchSize(100);

        assertThat(props.toAsyncSettings().drainWorkerCount()).isEqualTo(4);
        assertThat(props.toAsyncSettings().batchSize()).isEqualTo(100);
    }
}
