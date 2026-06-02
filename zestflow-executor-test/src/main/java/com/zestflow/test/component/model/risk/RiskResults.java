package com.zestflow.test.component.model.risk;

/** 风控域元件返回值。 */
public final class RiskResults {
    private RiskResults() {}

    public record RecordRiskEventResult(String eventId, String level) {}
    public record BlockUserResult(String userId, boolean blocked, String reason) {}
    public record ApplyAntiFraudResult(int riskScore, String suggestion) {}
    public record CalcRiskScoreResult(int score, String level, String[] factors) {}
    public record SubmitManualReviewResult(String reviewNo, String status) {}
}
