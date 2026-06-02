package com.zestflow.test.component.model.finance;

/** 财务域元件返回值。 */
public final class FinanceResults {
    private FinanceResults() {}

    public record CreateInvoiceResult(String invoiceNo, double amount) {}
    public record VerifyInvoiceResult(boolean valid, String taxCode) {}
    public record CreateSettlementResult(String settlementNo, double amount) {}
    public record ApproveSettlementResult(boolean approved, String approver) {}
    public record TransferAccountResult(String txNo, String status) {}
    public record FreezeBalanceResult(double frozenAmount, double remaining) {}
    public record UnfreezeBalanceResult(double unfrozenAmount, double remaining) {}
    public record CalcTaxResult(double taxRate, double taxAmount, String taxType) {}
    public record GenerateReportResult(String reportId, String period) {}
    public record ReconcileAccountResult(boolean matched, double systemAmount, double actualAmount) {}
}
