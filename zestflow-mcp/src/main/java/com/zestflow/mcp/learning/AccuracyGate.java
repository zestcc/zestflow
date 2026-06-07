package com.zestflow.mcp.learning;

import java.util.List;
import java.util.Objects;

/**
 * 晋升/沉淀门禁 — 对标 RLHF/RAG 策展：仅高置信样本进入 Pattern 库。
 * <p>
 * 目标：沉淀样本业务准确率 ≥97%（通过多重硬条件逼近，非 LLM 自评）。
 */
public final class AccuracyGate {

    /** 晋升所需最低置信分（0～1） */
    public static final double PROMOTION_SCORE_THRESHOLD = 0.97;

    private AccuracyGate() {
    }

    public static GateResult evaluate(LearningEvent event) {
        if (event == null) {
            return GateResult.reject("event 为空");
        }
        if (!Boolean.TRUE.equals(event.validatePassed())) {
            return GateResult.reject("validate_chain 未通过");
        }
        if (event.validateRounds() != null && event.validateRounds() > 2) {
            return GateResult.reject("validate 修复轮次 > 2");
        }
        boolean outcome = Boolean.TRUE.equals(event.adopted())
                || Boolean.TRUE.equals(event.playgroundSuccess());
        if (!outcome) {
            return GateResult.reject("未采纳且 Playground 未成功");
        }
        if (event.intent() == null || event.intent().isBlank()) {
            return GateResult.reject("缺少 intent");
        }
        if (event.feature() == null || event.feature().isBlank()) {
            return GateResult.reject("缺少 feature");
        }
        double score = score(event);
        if (score < PROMOTION_SCORE_THRESHOLD) {
            return GateResult.reject("置信分 " + score + " < " + PROMOTION_SCORE_THRESHOLD);
        }
        return GateResult.accept(score);
    }

    public static double score(LearningEvent event) {
        double s = 0.70;
        if (Boolean.TRUE.equals(event.validatePassed())) {
            s += 0.12;
        }
        if (event.validateRounds() != null && event.validateRounds() <= 1) {
            s += 0.05;
        } else if (event.validateRounds() != null && event.validateRounds() == 2) {
            s += 0.02;
        }
        if (Boolean.TRUE.equals(event.adopted())) {
            s += 0.08;
        }
        if (Boolean.TRUE.equals(event.playgroundSuccess())) {
            s += 0.05;
        }
        if (event.userCorrection() == null || event.userCorrection().isBlank()) {
            s += 0.03;
        }
        if (event.httpMode() != null && event.httpMode() >= 1 && event.httpMode() <= 3) {
            s += 0.02;
        }
        List<String> created = event.createdComponents();
        if (created != null && !created.isEmpty()) {
            s += Math.min(0.03, created.size() * 0.01);
        }
        return Math.min(1.0, s);
    }

    public record GateResult(boolean passed, double score, String reason) {
        static GateResult accept(double score) {
            return new GateResult(true, score, "ok");
        }

        static GateResult reject(String reason) {
            return new GateResult(false, 0, reason);
        }
    }
}
