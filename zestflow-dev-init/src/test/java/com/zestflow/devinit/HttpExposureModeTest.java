package com.zestflow.devinit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpExposureModeTest {

    @Test
    void parse_defaultsToMode3() {
        assertEquals(HttpExposureMode.MODE3, HttpExposureMode.parse(null));
        assertEquals(HttpExposureMode.MODE3, HttpExposureMode.parse("controller"));
    }

    @Test
    void parse_mode1And2() {
        assertEquals(HttpExposureMode.MODE1, HttpExposureMode.parse("1"));
        assertEquals(HttpExposureMode.MODE2, HttpExposureMode.parse("chain-route"));
    }

    @Test
    void mode3Section_mentionsZestChain() {
        assertTrue(HttpExposureMode.MODE3.architectureSection().contains("@ZestChain"));
    }
}
