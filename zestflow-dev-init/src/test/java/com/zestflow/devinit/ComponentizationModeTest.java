package com.zestflow.devinit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ComponentizationModeTest {

    @Test
    void parse_defaultsToFull() {
        assertEquals(ComponentizationMode.FULL, ComponentizationMode.parse(null));
        assertEquals(ComponentizationMode.FULL, ComponentizationMode.parse(""));
    }

    @Test
    void parse_hybrid() {
        assertEquals(ComponentizationMode.HYBRID, ComponentizationMode.parse("hybrid"));
    }

    @Test
    void parse_unknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> ComponentizationMode.parse("strict"));
    }
}
