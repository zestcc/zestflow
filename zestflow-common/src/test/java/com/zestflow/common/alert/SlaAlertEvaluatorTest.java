package com.zestflow.common.alert;

import com.zestflow.common.protocol.EventStats;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaAlertEvaluatorTest {

    @Test
    void evaluate_lowSuccessRate() {
        EventStats stats = EventStats.builder()
                .executionCount(20)
                .successRate(80.0)
                .failCount(4)
                .p95CostMs(100L)
                .build();
        SlaAlertEvaluationInput input = SlaAlertEvaluationInput.builder()
                .eventStats(stats)
                .windowMinutes(60)
                .minExecutions(5)
                .successRateThreshold(95.0)
                .failCountThreshold(100)
                .p95CostMsThreshold(5000L)
                .scheduleFailThreshold(3)
                .alertNoOnlineExecutor(false)
                .hasRegisteredExecutors(true)
                .hasOnlineExecutor(true)
                .scheduleFailureCount(0)
                .build();

        List<SlaAlertCandidate> candidates = SlaAlertEvaluator.evaluate(input);
        assertTrue(candidates.stream().anyMatch(c -> c.getRule() == AlertRule.LOW_SUCCESS_RATE));
    }

    @Test
    void evaluate_noOnlineExecutor() {
        SlaAlertEvaluationInput input = SlaAlertEvaluationInput.builder()
                .eventStats(EventStats.builder().executionCount(0).build())
                .windowMinutes(60)
                .minExecutions(5)
                .successRateThreshold(95.0)
                .failCountThreshold(10)
                .p95CostMsThreshold(5000L)
                .scheduleFailThreshold(3)
                .alertNoOnlineExecutor(true)
                .hasRegisteredExecutors(true)
                .hasOnlineExecutor(false)
                .scheduleFailureCount(0)
                .build();

        List<SlaAlertCandidate> candidates = SlaAlertEvaluator.evaluate(input);
        assertTrue(candidates.stream().anyMatch(c -> c.getRule() == AlertRule.NO_ONLINE_EXECUTOR));
    }
}
