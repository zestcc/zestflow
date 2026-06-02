package com.zestflow.common.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LatencyPercentilesTest {

    @Test
    void computesPercentilesForKnownSamples() {
        LatencyPercentiles stats = LatencyPercentiles.fromMillis(
                List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 100L));

        assertEquals(10, stats.count());
        assertEquals(1, stats.minMs());
        assertEquals(100, stats.maxMs());
        assertEquals(6, stats.p50Ms());
        assertEquals(100, stats.p95Ms());
        assertEquals(100, stats.p99Ms());
        assertEquals(100, stats.p999Ms());
    }

    @Test
    void emptySamplesReturnZeroedStats() {
        LatencyPercentiles stats = LatencyPercentiles.fromMillis(List.of());
        assertTrue(stats.isEmpty());
        assertEquals(0, stats.p999Ms());
    }
}
