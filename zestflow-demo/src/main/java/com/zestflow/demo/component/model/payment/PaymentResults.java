package com.zestflow.demo.component.model.payment;

/** 支付域元件返回值。 */
public final class PaymentResults {
    private PaymentResults() {}

    public record CreatePaymentResult(String paymentNo, String status) {}
    public record ProcessPaymentResult(String result, String channel) {}
    public record RefundPaymentResult(String refundNo, double amount) {}
    public record QueryPaymentResult(String status, String paidAt) {}
    public record ClosePaymentResult(String result) {}
    public record VerifySignatureResult(boolean verified) {}
    public record SplitAmountResult(double sellerAmount, double platformAmount) {}
    public record DepositWalletResult(double balance, String txId) {}
    public record WithdrawWalletResult(String result, String arrivalDate) {}
    public record QueryBillResult(double totalIncome, double totalExpense) {}
    public record PayFallbackResult(String result, String channel) {}
}
