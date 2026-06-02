package com.zestflow.test.component;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestExecute;
import com.zestflow.executor.annotation.ZestParam;
import com.zestflow.test.component.model.audit.AuditResults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ZestComponent("audit")
public class AuditHandler {

    @ZestExecute(value = "submitAudit", name = "提交审核")
    public AuditResults.SubmitAuditResult submitAudit() {
        log.info("审核-提交审核");
        return new AuditResults.SubmitAuditResult("AUD" + System.currentTimeMillis(), "PENDING");
    }

    @ZestExecute(value = "autoApprove", name = "自动审批")
    public AuditResults.AutoApproveResult autoApprove() {
        log.info("审核-自动审批");
        return new AuditResults.AutoApproveResult(true, "AMOUNT_LT_1000");
    }

    @ZestExecute(value = "manualAudit", name = "人工审核")
    public AuditResults.ManualAuditResult manualAudit() {
        log.info("审核-人工审核");
        return new AuditResults.ManualAuditResult("管理员", "通过", "L2");
    }

    @ZestExecute(value = "rejectAudit", name = "驳回审核")
    public AuditResults.RejectAuditResult rejectAudit() {
        log.info("审核-驳回");
        return new AuditResults.RejectAuditResult("rejected", "资料不完整");
    }

    @ZestExecute(value = "recallAudit", name = "撤回审核")
    public AuditResults.RecallAuditResult recallAudit(@ZestParam(value = "auditNo", required = false) String auditNo) {
        log.info("审核-撤回 auditNo={}", auditNo);
        return new AuditResults.RecallAuditResult("recalled", auditNo);
    }

    @ZestExecute(value = "assignAuditor", name = "分配审核人")
    public AuditResults.AssignAuditorResult assignAuditor() {
        log.info("审核-分配审核人");
        return new AuditResults.AssignAuditorResult(2001, "钱七");
    }

    @ZestExecute(value = "queryAuditLog", name = "查询审核日志")
    public AuditResults.QueryAuditLogResult queryAuditLog() {
        log.info("审核-查询日志");
        return new AuditResults.QueryAuditLogResult(
                new String[]{"提交审核 2026-05-30 10:00", "审批通过 2026-05-30 11:00"});
    }

    @ZestExecute(value = "escalateAudit", name = "升级审核")
    public AuditResults.EscalateAuditResult escalateAudit() {
        log.info("审核-升级处理");
        return new AuditResults.EscalateAuditResult("L1", "L3", "金额超限");
    }

    @ZestExecute(value = "batchAudit", name = "批量审核")
    public AuditResults.BatchAuditResult batchAudit() {
        log.info("审核-批量审核");
        return new AuditResults.BatchAuditResult(20, 18, 2);
    }

    @ZestExecute(value = "auditNotify", name = "审核结果通知")
    public AuditResults.AuditNotifyResult auditNotify() {
        log.info("审核-结果通知");
        return new AuditResults.AuditNotifyResult(true, "EMAIL");
    }
}
