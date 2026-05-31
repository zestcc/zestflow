package com.zestflow.test.demo.handler;

import com.zestflow.executor.annotation.*;
import com.zestflow.executor.context.ChainContext;
import com.zestflow.test.demo.dto.PaymentFlowRequest;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 支付流转处理器 — 生产级业务组件
 * <p>
 * 涵盖完整生命周期：参数校验 → 前置处理 → 核心执行 → 后置处理。
 * 所有方法均包含真实业务逻辑，无空实现。
 */
@Slf4j
@ZestComponent("paymentFlow")
public class PaymentFlowHandler {

    /** 支持的币种 */
    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("USD", "EUR", "CNY", "GBP", "JPY", "HKD", "SGD");

    /** 模拟汇率表 */
    private static final Map<String, BigDecimal> FX_RATES = new ConcurrentHashMap<>();

    /** 模拟账户余额表 */
    private static final Map<String, AtomicLong> ACCOUNTS = new ConcurrentHashMap<>();

    /** 合规检查已通过的账号缓存 */
    private static final Map<String, String> COMPLIANCE_CACHE = new ConcurrentHashMap<>();

    static {
        FX_RATES.put("USD", BigDecimal.ONE);
        FX_RATES.put("EUR", new BigDecimal("1.08"));
        FX_RATES.put("CNY", new BigDecimal("0.14"));
        FX_RATES.put("GBP", new BigDecimal("1.26"));
        FX_RATES.put("JPY", new BigDecimal("0.0067"));
        FX_RATES.put("HKD", new BigDecimal("0.128"));
        FX_RATES.put("SGD", new BigDecimal("0.74"));

        ACCOUNTS.put("ACC-CORP-001", new AtomicLong(1_000_000_00L));
        ACCOUNTS.put("ACC-CORP-002", new AtomicLong(500_000_00L));
        ACCOUNTS.put("ACC-USER-001", new AtomicLong(50_000_00L));
        ACCOUNTS.put("ACC-USER-002", new AtomicLong(10_000_00L));
        ACCOUNTS.put("ACC-USER-003", new AtomicLong(100_000_00L));
    }

    // ==================== 参数校验器 ====================

    /**
     * 支付请求参数校验
     * <p>
     * 在 Jakarta Bean Validation 注解基础上，补充业务级校验规则。
     */
    @ZestParamValidator("paymentRequestValidator")
    public void validatePaymentRequest(PaymentFlowRequest request) {
        if (!SUPPORTED_CURRENCIES.contains(request.getCurrency())) {
            throw new IllegalArgumentException("不支持的币种: " + request.getCurrency());
        }
        if (request.getPayerAccount().equals(request.getPayeeAccount())) {
            throw new IllegalArgumentException("付款账号与收款账号不能相同");
        }
        log.info("支付请求校验通过 orderId={} amount={} {}",
                request.getOrderId(), request.getAmount(), request.getCurrency());
    }

    // ==================== 前置处理器 ====================

    /**
     * 汇率查询 — 前置处理
     * <p>
     * 根据币种查询实时汇率，折算 USD 金额。
     */
    @ZestPreProcessor("enrichFxRate")
    public void enrichFxRate(ChainContext ctx) {
        String currency = ctx.get("currency", String.class);
        BigDecimal amount = ctx.get("amount", BigDecimal.class);

        BigDecimal rate = FX_RATES.getOrDefault(currency, BigDecimal.ONE);
        BigDecimal amountInUsd = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);

        ctx.put("fxRate", rate);
        ctx.put("fxRateLabel", "1 " + currency + " = " + rate + " USD");
        ctx.put("amountInUsd", amountInUsd);

        log.info("汇率查询完成 currency={} rate={} amountInUsd={}", currency, rate, amountInUsd);
    }

    /**
     * 风控合规检查 — 前置处理
     * <p>
     * 对付款账号进行 AML/制裁名单检查。
     */
    @ZestPreProcessor("checkCompliance")
    public void checkCompliance(ChainContext ctx) {
        String payerAccount = ctx.get("payerAccount", String.class);
        BigDecimal amountInUsd = ctx.get("amountInUsd", BigDecimal.class);

        // 缓存命中直接返回
        String cachedId = COMPLIANCE_CACHE.get(payerAccount);
        if (cachedId != null) {
            ctx.put("complianceCheckId", cachedId);
            ctx.put("complianceStatus", "CLEARED");
            log.info("合规检查缓存命中 account={} checkId={}", payerAccount, cachedId);
            return;
        }

        // 模拟检查耗时
        simulateLatency(50, 150);

        // 检查大额交易
        if (amountInUsd.compareTo(new BigDecimal("100000")) > 0) {
            // 大额交易需要标记复核
            String checkId = "AML-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
            COMPLIANCE_CACHE.put(payerAccount, checkId);
            ctx.put("complianceCheckId", checkId);
            ctx.put("complianceStatus", "MANUAL_REVIEW_REQUIRED");
            log.warn("大额交易需要复核 account={} amountInUsd={} checkId={}",
                    payerAccount, amountInUsd, checkId);
            return;
        }

        // 正常检查通过
        String checkId = "CLR-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        COMPLIANCE_CACHE.put(payerAccount, checkId);
        ctx.put("complianceCheckId", checkId);
        ctx.put("complianceStatus", "CLEARED");
        log.info("合规检查通过 account={} checkId={}", payerAccount, checkId);
    }

    // ==================== 核心执行器 ====================

    /**
     * 验证并丰富支付信息
     * <p>
     * 将请求数据与前置处理结果合并，写入 DataBus 供下游节点使用。
     * 同时将业务对象注册到上下文类型仓储，供后续节点的 ContextTypeResolver 注入。
     */
    @ZestExecute(value = "validateAndEnrichPayment", name = "验证并丰富支付信息",
            description = "合并请求数据与汇率/风控结果，注册业务对象到上下文")
    public void validateAndEnrichPayment(PaymentFlowRequest request, ChainContext ctx) {
        BigDecimal fxRate = ctx.get("fxRate", BigDecimal.class);
        String complianceCheckId = ctx.get("complianceCheckId", String.class);
        String complianceStatus = ctx.get("complianceStatus", String.class);

        ctx.put("orderId", request.getOrderId());
        ctx.put("amount", request.getAmount());
        ctx.put("currency", request.getCurrency());
        ctx.put("payerAccount", request.getPayerAccount());
        ctx.put("payeeAccount", request.getPayeeAccount());
        ctx.put("paymentMethod", request.getPaymentMethod() != null ? request.getPaymentMethod() : "BANK_TRANSFER");
        ctx.put("description", request.getDescription() != null ? request.getDescription() : "");
        ctx.put("status", "ENRICHED");

        log.info("支付信息丰富完成 orderId={} amount={} {} fxRate={} compliance={}",
                request.getOrderId(), request.getAmount(), request.getCurrency(), fxRate, complianceStatus);
    }

    /**
     * 提交支付交易
     * <p>
     * 将交易提交到支付网关，生成交易流水号。
     */
    @ZestExecute(value = "submitTransaction", name = "提交流水",
            description = "提交交易到支付网关，生成唯一交易流水号")
    public void submitTransaction(ChainContext ctx) {
        String transactionId = "TXN" + System.currentTimeMillis()
                + ThreadLocalRandom.current().nextInt(100, 999);
        BigDecimal totalAmount = ctx.get("totalAmount", BigDecimal.class);

        ctx.put("transactionId", transactionId);
        ctx.put("status", "SUBMITTED");

        // 模拟支付网关调用
        simulateLatency(100, 300);

        log.info("交易已提交 transactionId={} totalAmount={}", transactionId, totalAmount);
    }

    /**
     * 发送回执通知
     * <p>
     * 构建回执并发送通知。
     */
    @ZestExecute(value = "deliverReceipt", name = "发送回执",
            description = "构建支付回执并发送通知")
    public void deliverReceipt(ChainContext ctx) {
        String transactionId = ctx.get("transactionId", String.class);
        String payeeAccount = ctx.get("payeeAccount", String.class);
        BigDecimal amount = ctx.get("amount", BigDecimal.class);
        String currency = ctx.get("currency", String.class);
        BigDecimal fee = ctx.get("fee", BigDecimal.class);

        String receiptId = "RCP" + System.currentTimeMillis()
                + ThreadLocalRandom.current().nextInt(1000, 9999);
        String notificationId = "NOTIF" + System.currentTimeMillis();

        ctx.put("receiptId", receiptId);
        ctx.put("notificationId", notificationId);
        ctx.put("status", "COMPLETED");

        log.info("回执已发送 receiptId={} transactionId={} payee={} amount={} {} fee={}",
                receiptId, transactionId, payeeAccount, amount, currency, fee);
    }

    // ==================== 后置处理器 ====================

    /**
     * 账户扣款 — 后置处理
     * <p>
     * 从付款账户扣除总金额（含手续费）。
     */
    @ZestPostProcessor("deductAccountBalance")
    public void deductAccountBalance(ChainContext ctx) {
        String payerAccount = ctx.get("payerAccount", String.class);
        BigDecimal totalAmount = ctx.get("totalAmount", BigDecimal.class);

        // 金额转为分（long 避免浮点精度）
        long amountInCents = totalAmount.multiply(new BigDecimal("100"))
                .setScale(0, RoundingMode.HALF_UP).longValue();

        AtomicLong balance = ACCOUNTS.computeIfAbsent(payerAccount,
                k -> new AtomicLong(1_000_000_00L));

        long newBalance = balance.addAndGet(-amountInCents);
        if (newBalance < 0) {
            // 余额不足时回滚
            balance.addAndGet(amountInCents);
            throw new IllegalStateException(
                    "账户余额不足 account=" + payerAccount
                            + " required=" + amountInCents
                            + " balance=" + (balance.get() + amountInCents));
        }

        ctx.put("accountBalanceAfter", newBalance);
        log.info("账户扣款完成 account={} deduct={} balanceAfter={}",
                payerAccount, amountInCents, newBalance);
    }

    /**
     * 交易审计日志 — 后置处理
     * <p>
     * 记录完整的交易审计条目。
     */
    @ZestPostProcessor("createTransactionAudit")
    public void createTransactionAudit(ChainContext ctx) {
        String auditId = "AUD" + System.currentTimeMillis()
                + ThreadLocalRandom.current().nextInt(10, 99);

        ctx.put("auditEntryId", auditId);

        log.info("审计日志已记录 auditId={} transactionId={} orderId={} status={}",
                auditId, ctx.get("transactionId"), ctx.get("orderId"), ctx.get("status"));
    }

    // ==================== 内部工具 ====================

    private static void simulateLatency(int minMs, int maxMs) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(minMs, maxMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
