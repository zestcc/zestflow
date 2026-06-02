package com.zestflow.collector.async;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AsyncCollectorSettingsTest {

    @Test
    void compactConstructor_clampsDrainWorkerCountToAtLeastOne() {
        AsyncCollectorSettings settings = new AsyncCollectorSettings(
                10, 50, 100, false, "./fb", 3, 1000, 5000, 5000, 0);
        assertEquals(1, settings.drainWorkerCount());
    }
}
