package com.zestflow.demo.component;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestExecute;
import com.zestflow.executor.annotation.ZestParam;
import com.zestflow.executor.annotation.ZestParamBinder;
import com.zestflow.executor.context.ChainContext;
import com.zestflow.demo.component.model.payment.PaymentResults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ZestComponent("payment")
public class PaymentHandler {

    @ZestExecute(value = "createPayment", name = "创建支付单")
    public PaymentResults.CreatePaymentResult createPayment(
            @ZestParam(value = "orderId", required = false) String orderId) {
        log.info("支付-创建支付单 orderId={}", orderId);
        return new PaymentResults.CreatePaymentResult("PAY" + System.currentTimeMillis(), "PENDING");
    }

    @ZestExecute(value = "processPayment", name = "执行支付")
    public PaymentResults.ProcessPaymentResult processPayment(
            @ZestParam(value = "orderId", required = false) String orderId,
            @ZestParam(value = "amount", defaultValue = "0") double amount) {
        log.info("支付-执行支付 orderId={} amount={}", orderId, amount);
        return new PaymentResults.ProcessPaymentResult("paid", "balance");
    }

    @ZestExecute(value = "refundPayment", name = "发起退款")
    public PaymentResults.RefundPaymentResult refundPayment(
            @ZestParam(value = "amount", defaultValue = "50") double amount) {
        log.info("支付-退款处理 amount={}", amount);
        return new PaymentResults.RefundPaymentResult("RFD" + System.currentTimeMillis(), amount);
    }

    @ZestExecute(value = "queryPayment", name = "查询支付状态")
    public PaymentResults.QueryPaymentResult queryPayment() {
        log.info("支付-查询状态");
        return new PaymentResults.QueryPaymentResult("SUCCESS", "2026-05-30 10:00:00");
    }

    @ZestExecute(value = "closePayment", name = "关闭支付单")
    public PaymentResults.ClosePaymentResult closePayment() {
        log.info("支付-关闭支付单");
        return new PaymentResults.ClosePaymentResult("closed");
    }

    @ZestExecute(value = "verifySignature", name = "验签处理")
    public PaymentResults.VerifySignatureResult verifySignature() {
        log.info("支付-验签");
        return new PaymentResults.VerifySignatureResult(true);
    }

    @ZestExecute(value = "splitAmount", name = "分账处理")
    public PaymentResults.SplitAmountResult splitAmount(
            @ZestParam(value = "amount", defaultValue = "100") double amount) {
        log.info("支付-分账 amount={}", amount);
        return new PaymentResults.SplitAmountResult(amount * 0.9, amount * 0.1);
    }

    @ZestExecute(value = "depositWallet", name = "钱包充值")
    public PaymentResults.DepositWalletResult depositWallet() {
        log.info("支付-钱包充值");
        return new PaymentResults.DepositWalletResult(500.0, "TXN" + System.currentTimeMillis());
    }

    @ZestExecute(value = "withdrawWallet", name = "钱包提现")
    public PaymentResults.WithdrawWalletResult withdrawWallet() {
        log.info("支付-钱包提现");
        return new PaymentResults.WithdrawWalletResult("submitted", "2026-06-01");
    }

    @ZestExecute(value = "queryBill", name = "账单查询")
    public PaymentResults.QueryBillResult queryBill() {
        log.info("支付-账单查询");
        return new PaymentResults.QueryBillResult(9999.0, 2000.0);
    }

    @ZestExecute(value = "payFallback", name = "支付降级兜底")
    public PaymentResults.PayFallbackResult payFallback(
            @ZestParam(value = "orderId", required = false) String orderId) {
        log.warn("支付降级兜底执行 orderId={}", orderId);
        return new PaymentResults.PayFallbackResult("fallback_ok", "offline");
    }

    @ZestParamBinder(value = "bindPayParam", name = "绑定支付参数")
    public void bindPayParam(ChainContext ctx) {
        if (ctx.get("payAmount") == null) {
            ctx.put("payAmount", ctx.get("amount"));
        }
        if (ctx.get("orderId") == null) {
            ctx.put("orderId", "ORD-BIND-PAY");
        }
    }
}
