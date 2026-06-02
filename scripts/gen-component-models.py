#!/usr/bin/env python3
"""Generate *Results.java holder classes for demo components."""
from pathlib import Path

BASE = Path(__file__).resolve().parents[1] / "zestflow-executor-test/src/main/java/com/zestflow/test/component/model"

DOMAINS = {
    "audit": [
        ("SubmitAuditResult", "String auditNo, String status"),
        ("AutoApproveResult", "boolean approved, String rule"),
        ("ManualAuditResult", "String auditor, String opinion, String level"),
        ("RejectAuditResult", "String result, String reason"),
        ("RecallAuditResult", "String result, String auditNo"),
        ("AssignAuditorResult", "int auditorId, String auditorName"),
        ("QueryAuditLogResult", "String[] logs"),
        ("EscalateAuditResult", "String fromLevel, String toLevel, String reason"),
        ("BatchAuditResult", "int total, int approved, int rejected"),
        ("AuditNotifyResult", "boolean notified, String channel"),
    ],
    "finance": [
        ("CreateInvoiceResult", "String invoiceNo, double amount"),
        ("VerifyInvoiceResult", "boolean valid, String taxCode"),
        ("CreateSettlementResult", "String settlementNo, double amount"),
        ("ApproveSettlementResult", "boolean approved, String approver"),
        ("TransferAccountResult", "String txNo, String status"),
        ("FreezeBalanceResult", "double frozenAmount, double remaining"),
        ("UnfreezeBalanceResult", "double unfrozenAmount, double remaining"),
        ("CalcTaxResult", "double taxRate, double taxAmount, String taxType"),
        ("GenerateReportResult", "String reportId, String period"),
        ("ReconcileAccountResult", "boolean matched, double systemAmount, double actualAmount"),
    ],
    "inventory": [
        ("CheckStockResult", "boolean available, int stock"),
        ("LockStockResult", "String lockId, int quantity"),
        ("UnlockStockResult", "boolean released, int quantity"),
        ("DeductStockResult", "String result, int remaining"),
        ("RestoreStockResult", "boolean restored, int quantity"),
        ("QueryStockDetailResult", "String warehouse, String shelfNo, int quantity"),
        ("TransferStockResult", "String fromWarehouse, String toWarehouse, int quantity"),
        ("CheckWarehouseResult", "double usageRate, boolean available"),
        ("StockWarningResult", "boolean warning, int minStock, int currentStock"),
        ("InventoryCountResult", "int expected, int actual, int diff"),
    ],
    "logistics": [
        ("CreateDeliveryResult", "String deliveryNo, String status"),
        ("AssignCourierResult", "String courierId, String courierName"),
        ("PrintWaybillResult", "String waybillNo, boolean printed"),
        ("PickupPackageResult", "String result, String pickupTime"),
        ("SortingCenterResult", "String sortingNode, String nextStop"),
        ("TransportDispatchResult", "String vehicleNo, String driver"),
        ("DeliveryConfirmResult", "String result, String signer"),
        ("ReturnProcessResult", "String returnNo, String status"),
        ("QueryLogisticsResult", "String currentNode, String nextNode"),
        ("EvaluateDeliveryResult", "int rating, String feedback"),
    ],
    "marketing": [
        ("CreateCampaignResult", "String campaignId, String type"),
        ("IssueCouponResult", "String couponId, double value"),
        ("CalcDiscountResult", "double originalPrice, double discountPrice"),
        ("ApplyPromotionResult", "String promotionType, double reducedAmount"),
        ("SendPushPromotionResult", "int sentCount, String channel"),
        ("CheckUserTagResult", "boolean matched, String[] tags"),
        ("CashbackResult", "double cashbackAmount, String rule"),
        ("PointsRedeemResult", "int pointsUsed, String reward"),
        ("GroupBuyProcessResult", "String groupId, int members"),
        ("SeckillValidateResult", "boolean available, int stock"),
    ],
    "message": [
        ("SendSmsResult", "String msgId, String status"),
        ("SendEmailResult", "String msgId, String status"),
        ("SendAppPushResult", "String msgId, String channel"),
        ("SendWechatMsgResult", "String msgId, String template"),
        ("SendDingtalkResult", "String msgId, String robot"),
        ("BatchSendSmsResult", "int totalSent, int failedCount"),
        ("QueryMsgStatusResult", "String status, String readAt"),
        ("BuildMsgTemplateResult", "String templateId, String params"),
        ("SubscribeMsgResult", "String userId, String[] topics"),
        ("CancelMsgResult", "String result, String[] cancelledIds"),
    ],
    "order": [
        ("OrderCreatedResult", "String orderId, double amount"),
        ("AfterSaleFoundResult", "String applyId, String orderId, double amount"),
        ("RefundOrderResult", "String orderId, Object refundAmount, String status"),
        ("CancelOrderResult", "String orderId, String status"),
        ("ReturnGoodsResult", "boolean done"),
        ("ExchangeOrderResult", "boolean done"),
        ("ShipExchangeResult", "boolean done"),
    ],
    "payment": [
        ("CreatePaymentResult", "String paymentNo, String status"),
        ("ProcessPaymentResult", "String result, String channel"),
        ("RefundPaymentResult", "String refundNo, double amount"),
        ("QueryPaymentResult", "String status, String paidAt"),
        ("ClosePaymentResult", "String result"),
        ("VerifySignatureResult", "boolean verified"),
        ("SplitAmountResult", "double sellerAmount, double platformAmount"),
        ("DepositWalletResult", "double balance, String txId"),
        ("WithdrawWalletResult", "String result, String arrivalDate"),
        ("QueryBillResult", "double totalIncome, double totalExpense"),
        ("PayFallbackResult", "String result, String channel"),
    ],
    "risk": [
        ("RecordRiskEventResult", "String eventId, String level"),
        ("BlockUserResult", "String userId, boolean blocked, String reason"),
        ("ApplyAntiFraudResult", "int riskScore, String suggestion"),
        ("CalcRiskScoreResult", "int score, String level, String[] factors"),
        ("SubmitManualReviewResult", "String reviewNo, String status"),
    ],
    "demo": [
        ("SeedNotifyItemsResult", "int count"),
        ("NoopStepResult", "int step"),
    ],
    "user": [
        ("ValidateUserResult", "String userId, boolean valid"),
        ("SendNotifyResult", "boolean sent, String channel, String userId"),
    ],
}


def write_results(domain: str, records: list) -> None:
    cls = domain[:1].upper() + domain[1:] + "Results"
    if domain == "inventory":
        cls = "InventoryResults"
    elif domain == "logistics":
        cls = "LogisticsResults"
    lines = [
        f"package com.zestflow.test.component.model.{domain};",
        "",
        f"/** {domain} 域元件返回值。 */",
        f"public final class {cls} {{",
        f"    private {cls}() {{}}",
        "",
    ]
    for name, fields in records:
        lines.append(f"    public record {name}({fields}) {{}}")
        lines.append("")
    lines.append("}")
    lines.append("")
    path = BASE / domain / f"{cls}.java"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines), encoding="utf-8")
    print("wrote", path)


def main():
    for domain, records in DOMAINS.items():
        write_results(domain, records)


if __name__ == "__main__":
    main()
