package com.zestflow.common.registry;

import com.zestflow.common.constant.RegistryConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeartbeatFailureTrackerTest {

    @Test
    void requiresThresholdBeforeReregister() {
        HeartbeatFailureTracker tracker = new HeartbeatFailureTracker();
        int threshold = RegistryConstants.HEARTBEAT_FAILURE_THRESHOLD_BEFORE_REREGISTER;

        for (int i = 1; i < threshold; i++) {
            assertFalse(tracker.onFailure());
        }
        assertTrue(tracker.onFailure());
    }

    @Test
    void successResetsCounter() {
        HeartbeatFailureTracker tracker = new HeartbeatFailureTracker();
        tracker.onFailure();
        tracker.onFailure();
        tracker.onSuccess();
        assertEquals(0, tracker.consecutiveFailures());
        assertFalse(tracker.onFailure());
    }
}
