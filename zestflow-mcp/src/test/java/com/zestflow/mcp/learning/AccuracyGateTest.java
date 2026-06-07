package com.zestflow.mcp.learning;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AccuracyGateTest {

    @Test
    void highQualityEvent_passesPromotion() {
        LearningEvent e = new LearningEvent(
                "1", null, "COMPOSE_CHAIN", "userRegister", "demo-app", "CHN_USER_REGISTER",
                1, List.of("sendNotify"), List.of("createUser"),
                1, true, true, true, null, null);
        AccuracyGate.GateResult r = AccuracyGate.evaluate(e);
        assertTrue(r.passed());
        assertTrue(r.score() >= AccuracyGate.PROMOTION_SCORE_THRESHOLD);
    }

    @Test
    void validateFailed_rejected() {
        LearningEvent e = new LearningEvent(
                "1", null, "COMPOSE_CHAIN", "userRegister", "demo-app", null,
                null, null, null, 2, false, true, true, null, null);
        assertFalse(AccuracyGate.evaluate(e).passed());
    }
}
