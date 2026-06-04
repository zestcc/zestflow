package com.zestflow.demo.component.model.audit;

/** 审核域元件返回值。 */
public final class AuditResults {
    private AuditResults() {}

    public record SubmitAuditResult(String auditNo, String status) {}
    public record AutoApproveResult(boolean approved, String rule) {}
    public record ManualAuditResult(String auditor, String opinion, String level) {}
    public record RejectAuditResult(String result, String reason) {}
    public record RecallAuditResult(String result, String auditNo) {}
    public record AssignAuditorResult(int auditorId, String auditorName) {}
    public record QueryAuditLogResult(String[] logs) {}
    public record EscalateAuditResult(String fromLevel, String toLevel, String reason) {}
    public record BatchAuditResult(int total, int approved, int rejected) {}
    public record AuditNotifyResult(boolean notified, String channel) {}
}
