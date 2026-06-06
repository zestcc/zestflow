package com.zestflow.demo.service;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.model.dto.ChainDefinitionDTO;
import com.zestflow.common.model.dto.ChainEdgeDTO;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.common.model.dto.ChainNodeDTO;
import com.zestflow.executor.chain.ChainDefinitionBuilder;
import com.zestflow.executor.chain.ChainManager;
import com.zestflow.executor.engine.ChainExecutionEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 链条示例生成器 - 生成100+示例链条，覆盖不同业务场景
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChainExamplesGenerator {

    private final ChainDefinitionBuilder chainDefinitionBuilder;
    private final ChainManager chainManager;
    private final ChainExecutionEngine chainExecutionEngine;

    // ==================== 节点构建工具方法 ====================

    private static ChainNodeDTO normal(String id, String component) {
        return ChainNodeDTO.builder().id(id).label(id).type(ChainConstants.NODE_TYPE_NORMAL).component(component).build();
    }

    private static ChainNodeDTO normal(String id, String component, Map<String, Object> config) {
        return ChainNodeDTO.builder().id(id).label(id).type(ChainConstants.NODE_TYPE_NORMAL).component(component).config(config).build();
    }

    private static ChainNodeDTO condition(String id, String condition) {
        return ChainNodeDTO.builder().id(id).label(id).type(ChainConstants.NODE_TYPE_CONDITION)
                .config(Map.of("condition", condition)).build();
    }

    private static ChainNodeDTO selector(String id, String component) {
        return ChainNodeDTO.builder().id(id).label(id).type(ChainConstants.NODE_TYPE_SELECTOR).component(component).build();
    }

    private static ChainNodeDTO script(String id, String script) {
        return ChainNodeDTO.builder().id(id).label(id).type(ChainConstants.NODE_TYPE_SCRIPT).script(script).build();
    }

    private static ChainNodeDTO subChain(String id, String subChainCode) {
        return ChainNodeDTO.builder().id(id).label(id).type(ChainConstants.NODE_TYPE_SUB_CHAIN).subChainCode(subChainCode).build();
    }

    private static ChainNodeDTO iterator(String id, String dataSource) {
        return ChainNodeDTO.builder().id(id).label(id).type(ChainConstants.NODE_TYPE_ITERATOR)
                .config(Map.of("dataSource", dataSource)).build();
    }

    private static ChainNodeDTO fork(String id) {
        return ChainNodeDTO.builder().id(id).label(id).type(ChainConstants.NODE_TYPE_FORK).build();
    }

    private static ChainNodeDTO join(String id) {
        return ChainNodeDTO.builder().id(id).label(id).type(ChainConstants.NODE_TYPE_JOIN).build();
    }

    private static ChainNodeDTO tryCatch(String id) {
        return ChainNodeDTO.builder().id(id).label(id).type(ChainConstants.NODE_TYPE_TRY_CATCH).build();
    }

    private static ChainNodeDTO whileNode(String id, String condition) {
        return ChainNodeDTO.builder().id(id).label(id).type(ChainConstants.NODE_TYPE_WHILE)
                .config(Map.of("condition", condition)).build();
    }

    private static ChainNodeDTO logger(String id) {
        return ChainNodeDTO.builder().id(id).label(id).type(ChainConstants.NODE_TYPE_LOGGER).build();
    }

    private static ChainNodeDTO delay(String id, long ms) {
        return ChainNodeDTO.builder().id(id).label(id).type(ChainConstants.NODE_TYPE_DELAY)
                .config(Map.of("delayMs", ms)).build();
    }

    private static ChainEdgeDTO edge(String source, String target) {
        return ChainEdgeDTO.builder().source(source).target(target).build();
    }

    private static ChainEdgeDTO edge(String source, String target, String condition) {
        return ChainEdgeDTO.builder().source(source).target(target).condition(condition).build();
    }

    private ChainExecuteResultDTO execute(String code, List<ChainNodeDTO> nodes, List<ChainEdgeDTO> edges, Map<String, Object> params) {
        ChainDefinitionDTO dto = ChainDefinitionDTO.builder()
                .code(code).version(1).nodes(nodes).edges(edges)
                .config(Map.of())
                .build();
        chainManager.load(chainDefinitionBuilder.build(dto));
        log.debug("链已注册 code={} nodes={}", code, nodes.size());
        return chainExecutionEngine.execute(code, params != null ? params : Map.of());
    }

    // ==================== 订单场景链条 (1-20) ====================

    public ChainExecuteResultDTO orderCreateSimple(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("create", "createOrder"),
                normal("pay", "processPayment"),
                normal("notify", "sendNotify")
        ), List.of(edge("create", "pay"), edge("pay", "notify")), params);
    }

    public ChainExecuteResultDTO orderCreateWithValidation(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("validate", "validateUser"),
                normal("create", "createOrder"),
                normal("stock", "checkStock"),
                normal("pay", "processPayment"),
                normal("notify", "sendNotify")
        ), List.of(edge("validate", "create"), edge("create", "stock"),
                edge("stock", "pay"), edge("pay", "notify")), params);
    }

    public ChainExecuteResultDTO orderCreateParallel(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("create", "createOrder"),
                normal("pay", "processPayment"),
                normal("stock", "checkStock"),
                normal("notify", "sendNotify")
        ), List.of(edge("create", "pay"), edge("create", "stock"),
                edge("pay", "notify"), edge("stock", "notify")), params);
    }

    public ChainExecuteResultDTO orderCancel(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("check", "validateUser"),
                condition("canCancel", "${orderStatus} == 'PENDING'"),
                normal("cancel", "cancelOrder"),
                normal("refund", "refundPayment"),
                normal("notify", "sendNotify")
        ), List.of(edge("check", "canCancel"),
                edge("canCancel", "cancel", "True"),
                edge("cancel", "refund"), edge("refund", "notify")), params);
    }

    public ChainExecuteResultDTO orderRefund(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("find", "findAfterSale"),
                normal("audit", "auditAfterSale"),
                condition("approved", "${auditResult} == 'APPROVED'"),
                normal("refund", "refundOrder"),
                normal("notify", "sendNotify")
        ), List.of(edge("find", "audit"), edge("audit", "approved"),
                edge("approved", "refund", "True"),
                edge("refund", "notify")), params);
    }

    public ChainExecuteResultDTO orderBatchCreate(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("seed", "seedNotifyItems"),
                iterator("loop", "items"),
                normal("create", "createOrder"),
                normal("done", "sendNotify")
        ), List.of(edge("seed", "loop"), edge("loop", "create"), edge("create", "done")), params);
    }

    public ChainExecuteResultDTO orderWithSubChain(String code, Map<String, Object> params) {
        String subCode = code + "-sub";
        execute(subCode, List.of(normal("pack", "printWaybill"), normal("deliver", "deliveryConfirm")),
                List.of(edge("pack", "deliver")), params);
        return execute(code, List.of(
                normal("create", "createOrder"),
                subChain("ship", subCode),
                normal("notify", "sendNotify")
        ), List.of(edge("create", "ship"), edge("ship", "notify")), params);
    }

    public ChainExecuteResultDTO orderWithScript(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("create", "createOrder"),
                script("calc", "ctx.put('amount', ctx.get('price') * ctx.get('quantity'))"),
                normal("pay", "processPayment"),
                normal("notify", "sendNotify")
        ), List.of(edge("create", "calc"), edge("calc", "pay"), edge("pay", "notify")), params);
    }

    public ChainExecuteResultDTO orderWithRetry(String code, Map<String, Object> params) {
        Map<String, Object> retryConfig = Map.of("retryCount", 3, "retryIntervalMs", 1000);
        return execute(code, List.of(
                normal("create", "createOrder"),
                normal("pay", "processPayment", retryConfig),
                normal("notify", "sendNotify")
        ), List.of(edge("create", "pay"), edge("pay", "notify")), params);
    }

    public ChainExecuteResultDTO orderWithCache(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("getCache", "getUserCache"),
                condition("cached", "${userCache} != null"),
                normal("create", "createOrder"),
                normal("setCache", "setUserCache"),
                normal("notify", "sendNotify")
        ), List.of(edge("getCache", "cached"),
                edge("cached", "create", "False"),
                edge("cached", "notify", "True"),
                edge("create", "setCache"), edge("setCache", "notify")), params);
    }

    // ==================== 支付场景链条 (21-40) ====================

    public ChainExecuteResultDTO paymentSimple(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("create", "createPayment"),
                normal("process", "processPayment"),
                normal("notify", "sendNotify")
        ), List.of(edge("create", "process"), edge("process", "notify")), params);
    }

    public ChainExecuteResultDTO paymentWithRisk(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("validate", "validateUser"),
                normal("risk", "riskCheckAmount"),
                condition("pass", "${riskPass} == true"),
                normal("create", "createPayment"),
                normal("process", "processPayment"),
                normal("notify", "sendNotify")
        ), List.of(edge("validate", "risk"), edge("risk", "pass"),
                edge("pass", "create", "True"),
                edge("create", "process"), edge("process", "notify")), params);
    }

    public ChainExecuteResultDTO paymentSplit(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("create", "createPayment"),
                normal("process", "processPayment"),
                normal("split", "splitAmount"),
                normal("notify", "sendNotify")
        ), List.of(edge("create", "process"), edge("process", "split"), edge("split", "notify")), params);
    }

    public ChainExecuteResultDTO paymentRefund(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("query", "queryPayment"),
                condition("canRefund", "${status} == 'SUCCESS'"),
                normal("refund", "refundPayment"),
                normal("notify", "sendNotify")
        ), List.of(edge("query", "canRefund"),
                edge("canRefund", "refund", "True"),
                edge("refund", "notify")), params);
    }

    public ChainExecuteResultDTO paymentWallet(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("check", "validateUser"),
                normal("deposit", "depositWallet"),
                normal("process", "processPayment"),
                normal("notify", "sendNotify")
        ), List.of(edge("check", "deposit"), edge("deposit", "process"), edge("process", "notify")), params);
    }

    public ChainExecuteResultDTO paymentWithdraw(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("check", "validateUser"),
                normal("freeze", "freezeBalance"),
                normal("withdraw", "withdrawWallet"),
                normal("notify", "sendNotify")
        ), List.of(edge("check", "freeze"), edge("freeze", "withdraw"), edge("withdraw", "notify")), params);
    }

    // ==================== 库存场景链条 (41-60) ====================

    public ChainExecuteResultDTO inventoryCheck(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("check", "checkStock"),
                condition("enough", "${stock} >= ${quantity}"),
                normal("lock", "lockStock"),
                normal("notify", "sendNotify")
        ), List.of(edge("check", "enough"),
                edge("enough", "lock", "True"),
                edge("lock", "notify")), params);
    }

    public ChainExecuteResultDTO inventoryDeduct(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("check", "checkStock"),
                normal("lock", "lockStock"),
                normal("deduct", "deductStock"),
                normal("notify", "sendNotify")
        ), List.of(edge("check", "lock"), edge("lock", "deduct"), edge("deduct", "notify")), params);
    }

    public ChainExecuteResultDTO inventoryTransfer(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("check", "checkStock"),
                normal("deduct", "deductStock"),
                normal("transfer", "transferStock"),
                normal("notify", "sendNotify")
        ), List.of(edge("check", "deduct"), edge("deduct", "transfer"), edge("transfer", "notify")), params);
    }

    public ChainExecuteResultDTO inventoryRestore(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("unlock", "unlockStock"),
                normal("restore", "restoreStock"),
                normal("notify", "sendNotify")
        ), List.of(edge("unlock", "restore"), edge("restore", "notify")), params);
    }

    // ==================== 营销场景链条 (61-80) ====================

    public ChainExecuteResultDTO marketingCoupon(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("check", "checkUserTag"),
                condition("eligible", "${eligible} == true"),
                normal("issue", "issueCoupon"),
                normal("notify", "sendNotify")
        ), List.of(edge("check", "eligible"),
                edge("eligible", "issue", "True"),
                edge("issue", "notify")), params);
    }

    public ChainExecuteResultDTO marketingDiscount(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("calc", "calcDiscount"),
                normal("apply", "applyPromotion"),
                normal("notify", "sendNotify")
        ), List.of(edge("calc", "apply"), edge("apply", "notify")), params);
    }

    public ChainExecuteResultDTO marketingPoints(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("check", "checkUserTag"),
                normal("redeem", "redeemPoints"),
                normal("notify", "sendNotify")
        ), List.of(edge("check", "redeem"), edge("redeem", "notify")), params);
    }

    public ChainExecuteResultDTO marketingCashback(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("calc", "calcCashback"),
                normal("apply", "applyPromotion"),
                normal("notify", "sendNotify")
        ), List.of(edge("calc", "apply"), edge("apply", "notify")), params);
    }

    // ==================== 物流场景链条 (81-100) ====================

    public ChainExecuteResultDTO logisticsCreate(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("create", "createDelivery"),
                normal("assign", "assignCourier"),
                normal("print", "printWaybill"),
                normal("notify", "sendNotify")
        ), List.of(edge("create", "assign"), edge("assign", "print"), edge("print", "notify")), params);
    }

    public ChainExecuteResultDTO logisticsDelivery(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("pickup", "pickupPackage"),
                normal("sort", "sortingCenter"),
                normal("dispatch", "transportDispatch"),
                normal("deliver", "deliveryConfirm"),
                normal("notify", "sendNotify")
        ), List.of(edge("pickup", "sort"), edge("sort", "dispatch"),
                edge("dispatch", "deliver"), edge("deliver", "notify")), params);
    }

    public ChainExecuteResultDTO logisticsReturn(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("create", "createDelivery"),
                normal("return", "returnProcess"),
                normal("notify", "sendNotify")
        ), List.of(edge("create", "return"), edge("return", "notify")), params);
    }

    // ==================== 复合场景链条 (101-120) ====================

    public ChainExecuteResultDTO fullOrderFlow(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("validate", "validateUser"),
                normal("create", "createOrder"),
                normal("pay", "processPayment"),
                normal("stock", "checkStock"),
                normal("deduct", "deductStock"),
                normal("ship", "createDelivery"),
                normal("notify", "sendNotify")
        ), List.of(edge("validate", "create"), edge("create", "pay"), edge("pay", "stock"),
                edge("stock", "deduct"), edge("deduct", "ship"), edge("ship", "notify")), params);
    }

    public ChainExecuteResultDTO complexDagFlow(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("start", "validateUser"),
                normal("A", "processPayment"),
                normal("B", "checkStock"),
                normal("C", "createDelivery"),
                normal("D", "sendNotify"),
                normal("E", "printWaybill")
        ), List.of(edge("start", "A"), edge("start", "B"),
                edge("A", "C"), edge("B", "C"),
                edge("C", "D"), edge("C", "E")), params);
    }

    public ChainExecuteResultDTO withForkJoin(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("start", "validateUser"),
                fork("fork"),
                normal("pay", "processPayment"),
                normal("stock", "checkStock"),
                join("join"),
                normal("end", "sendNotify")
        ), List.of(edge("start", "fork"), edge("fork", "pay"), edge("fork", "stock"),
                edge("pay", "join"), edge("stock", "join"), edge("join", "end")), params);
    }

    public ChainExecuteResultDTO withTryCatch(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("start", "validateUser"),
                tryCatch("try"),
                normal("risky", "processPayment"),
                normal("fallback", "payFallback"),
                normal("end", "sendNotify")
        ), List.of(edge("start", "try"), edge("try", "risky"),
                edge("risky", "end"), edge("try", "fallback")), params);
    }

    public ChainExecuteResultDTO withWhile(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("start", "validateUser"),
                whileNode("loop", "${count} < 5"),
                normal("process", "noopStep"),
                normal("end", "sendNotify")
        ), List.of(edge("start", "loop"), edge("loop", "process"),
                edge("process", "loop"), edge("loop", "end")), params);
    }

    public ChainExecuteResultDTO withLogger(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("start", "validateUser"),
                logger("log1"),
                normal("process", "processPayment"),
                logger("log2"),
                normal("end", "sendNotify")
        ), List.of(edge("start", "log1"), edge("log1", "process"),
                edge("process", "log2"), edge("log2", "end")), params);
    }

    public ChainExecuteResultDTO withDelay(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("start", "validateUser"),
                delay("wait", 1000),
                normal("process", "processPayment"),
                normal("end", "sendNotify")
        ), List.of(edge("start", "wait"), edge("wait", "process"), edge("process", "end")), params);
    }

    // ==================== 用户中心场景链条 (35-50) ====================

    public ChainExecuteResultDTO userRegister(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("validate", "validateUser"),
                normal("register", "registerUser"),
                normal("notify", "sendNotify")
        ), List.of(edge("validate", "register"), edge("register", "notify")), params);
    }

    public ChainExecuteResultDTO userLogin(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("validate", "validateUser"),
                normal("login", "loginUser"),
                normal("notify", "sendNotify")
        ), List.of(edge("validate", "login"), edge("login", "notify")), params);
    }

    public ChainExecuteResultDTO userProfile(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("get", "getUserProfile"),
                normal("transform", "transformUserInfo"),
                normal("notify", "sendNotify")
        ), List.of(edge("get", "transform"), edge("transform", "notify")), params);
    }

    public ChainExecuteResultDTO userTag(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("get", "getUserTag"),
                normal("check", "checkUserTag"),
                normal("notify", "sendNotify")
        ), List.of(edge("get", "check"), edge("check", "notify")), params);
    }

    // ==================== 审批场景链条 (51-65) ====================

    public ChainExecuteResultDTO approvalSimple(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("create", "createApproval"),
                normal("audit", "auditAfterSale"),
                condition("pass", "${auditResult} == 'APPROVED'"),
                normal("notify", "sendNotify")
        ), List.of(edge("create", "audit"), edge("audit", "pass"),
                edge("pass", "notify", "True")), params);
    }

    public ChainExecuteResultDTO approvalMulti(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("create", "createApproval"),
                normal("audit1", "auditAfterSale"),
                condition("pass1", "${auditResult} == 'APPROVED'"),
                normal("audit2", "auditAfterSale"),
                condition("pass2", "${auditResult} == 'APPROVED'"),
                normal("notify", "sendNotify")
        ), List.of(edge("create", "audit1"), edge("audit1", "pass1"),
                edge("pass1", "audit2", "True"), edge("audit2", "pass2"),
                edge("pass2", "notify", "True")), params);
    }

    // ==================== 通知场景链条 (66-80) ====================

    public ChainExecuteResultDTO notifySms(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("create", "createNotification"),
                normal("send", "sendSms"),
                normal("notify", "sendNotify")
        ), List.of(edge("create", "send"), edge("send", "notify")), params);
    }

    public ChainExecuteResultDTO notifyEmail(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("create", "createNotification"),
                normal("send", "sendEmail"),
                normal("notify", "sendNotify")
        ), List.of(edge("create", "send"), edge("send", "notify")), params);
    }

    public ChainExecuteResultDTO notifyPush(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("create", "createNotification"),
                normal("send", "sendPush"),
                normal("notify", "sendNotify")
        ), List.of(edge("create", "send"), edge("send", "notify")), params);
    }

    // ==================== 数据处理场景链条 (81-95) ====================

    public ChainExecuteResultDTO dataTransform(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("load", "loadData"),
                normal("transform", "transformUserInfo"),
                normal("save", "saveData"),
                normal("notify", "sendNotify")
        ), List.of(edge("load", "transform"), edge("transform", "save"), edge("save", "notify")), params);
    }

    public ChainExecuteResultDTO dataFilter(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("load", "loadData"),
                normal("filter", "filterValidUsers"),
                normal("save", "saveData"),
                normal("notify", "sendNotify")
        ), List.of(edge("load", "filter"), edge("filter", "save"), edge("save", "notify")), params);
    }

    public ChainExecuteResultDTO dataAggregate(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("load", "loadData"),
                normal("aggregate", "aggregateOrderAmounts"),
                normal("save", "saveData"),
                normal("notify", "sendNotify")
        ), List.of(edge("load", "aggregate"), edge("aggregate", "save"), edge("save", "notify")), params);
    }

    public ChainExecuteResultDTO dataSplit(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("load", "loadData"),
                normal("split", "splitOrdersByStatus"),
                normal("save", "saveData"),
                normal("notify", "sendNotify")
        ), List.of(edge("load", "split"), edge("split", "save"), edge("save", "notify")), params);
    }

    // ==================== API集成场景链条 (96-110) ====================

    public ChainExecuteResultDTO apiHttp(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("http", "httpGet"),
                normal("parse", "parseHttpResponse"),
                normal("notify", "sendNotify")
        ), List.of(edge("http", "parse"), edge("parse", "notify")), params);
    }

    public ChainExecuteResultDTO apiPost(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("build", "buildHttpRequest"),
                normal("http", "httpPost"),
                normal("parse", "parseHttpResponse"),
                normal("notify", "sendNotify")
        ), List.of(edge("build", "http"), edge("http", "parse"), edge("parse", "notify")), params);
    }

    // ==================== 缓存场景链条 (111-120) ====================

    public ChainExecuteResultDTO cacheRead(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("read", "getUserCache"),
                condition("exist", "${userCache} != null"),
                normal("notify", "sendNotify")
        ), List.of(edge("read", "exist"), edge("exist", "notify", "True")), params);
    }

    public ChainExecuteResultDTO cacheWrite(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("create", "createOrder"),
                normal("write", "setUserCache"),
                normal("notify", "sendNotify")
        ), List.of(edge("create", "write"), edge("write", "notify")), params);
    }

    // ==================== MQ场景链条 (121-135) ====================

    public ChainExecuteResultDTO mqProduce(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("create", "createOrder"),
                normal("send", "sendOrderCreatedMsg"),
                normal("notify", "sendNotify")
        ), List.of(edge("create", "send"), edge("send", "notify")), params);
    }

    public ChainExecuteResultDTO mqConsume(String code, Map<String, Object> params) {
        return execute(code, List.of(
                normal("consume", "consumeOrderCreatedMsg"),
                normal("process", "processPayment"),
                normal("notify", "sendNotify")
        ), List.of(edge("consume", "process"), edge("process", "notify")), params);
    }

    // ==================== 批量注册所有链条 ====================

    public List<String> registerAllChains() {
        List<String> registeredCodes = new ArrayList<>();
        Map<String, Object> defaultParams = Map.of("userId", "U001", "orderId", "ORD-001", "amount", 99.9);

        // 订单场景 (1-10)
        registeredCodes.add("order-simple"); orderCreateSimple("order-simple", defaultParams);
        registeredCodes.add("order-validation"); orderCreateWithValidation("order-validation", defaultParams);
        registeredCodes.add("order-parallel"); orderCreateParallel("order-parallel", defaultParams);
        registeredCodes.add("order-cancel"); orderCancel("order-cancel", defaultParams);
        registeredCodes.add("order-refund"); orderRefund("order-refund", defaultParams);
        registeredCodes.add("order-batch"); orderBatchCreate("order-batch", defaultParams);
        registeredCodes.add("order-subchain"); orderWithSubChain("order-subchain", defaultParams);
        registeredCodes.add("order-script"); orderWithScript("order-script", defaultParams);
        registeredCodes.add("order-retry"); orderWithRetry("order-retry", defaultParams);
        registeredCodes.add("order-cache"); orderWithCache("order-cache", defaultParams);

        // 支付场景 (11-20)
        registeredCodes.add("pay-simple"); paymentSimple("pay-simple", defaultParams);
        registeredCodes.add("pay-risk"); paymentWithRisk("pay-risk", defaultParams);
        registeredCodes.add("pay-split"); paymentSplit("pay-split", defaultParams);
        registeredCodes.add("pay-refund"); paymentRefund("pay-refund", defaultParams);
        registeredCodes.add("pay-wallet"); paymentWallet("pay-wallet", defaultParams);
        registeredCodes.add("pay-withdraw"); paymentWithdraw("pay-withdraw", defaultParams);
        registeredCodes.add("pay-query"); paymentSimple("pay-query", defaultParams);
        registeredCodes.add("pay-callback"); paymentSimple("pay-callback", defaultParams);
        registeredCodes.add("pay-verify"); paymentWithRisk("pay-verify", defaultParams);
        registeredCodes.add("pay-auth"); paymentSimple("pay-auth", defaultParams);

        // 库存场景 (21-30)
        registeredCodes.add("inv-check"); inventoryCheck("inv-check", defaultParams);
        registeredCodes.add("inv-deduct"); inventoryDeduct("inv-deduct", defaultParams);
        registeredCodes.add("inv-transfer"); inventoryTransfer("inv-transfer", defaultParams);
        registeredCodes.add("inv-restore"); inventoryRestore("inv-restore", defaultParams);
        registeredCodes.add("inv-lock"); inventoryCheck("inv-lock", defaultParams);
        registeredCodes.add("inv-unlock"); inventoryRestore("inv-unlock", defaultParams);
        registeredCodes.add("inv-adjust"); inventoryDeduct("inv-adjust", defaultParams);
        registeredCodes.add("inv-sync"); inventoryCheck("inv-sync", defaultParams);
        registeredCodes.add("inv-stockin"); inventoryDeduct("inv-stockin", defaultParams);
        registeredCodes.add("inv-stockout"); inventoryTransfer("inv-stockout", defaultParams);

        // 营销场景 (31-42)
        registeredCodes.add("mkt-coupon"); marketingCoupon("mkt-coupon", defaultParams);
        registeredCodes.add("mkt-discount"); marketingDiscount("mkt-discount", defaultParams);
        registeredCodes.add("mkt-points"); marketingPoints("mkt-points", defaultParams);
        registeredCodes.add("mkt-cashback"); marketingCashback("mkt-cashback", defaultParams);
        registeredCodes.add("mkt-promotion"); marketingDiscount("mkt-promotion", defaultParams);
        registeredCodes.add("mkt-gift"); marketingCoupon("mkt-gift", defaultParams);
        registeredCodes.add("mkt-reward"); marketingPoints("mkt-reward", defaultParams);
        registeredCodes.add("mkt-bundle"); marketingDiscount("mkt-bundle", defaultParams);
        registeredCodes.add("mkt-flash"); marketingCoupon("mkt-flash", defaultParams);
        registeredCodes.add("mkt-group"); marketingDiscount("mkt-group", defaultParams);
        registeredCodes.add("mkt-recommend"); marketingPoints("mkt-recommend", defaultParams);
        registeredCodes.add("mkt-lottery"); marketingCoupon("mkt-lottery", defaultParams);

        // 物流场景 (43-55)
        registeredCodes.add("log-create"); logisticsCreate("log-create", defaultParams);
        registeredCodes.add("log-delivery"); logisticsDelivery("log-delivery", defaultParams);
        registeredCodes.add("log-return"); logisticsReturn("log-return", defaultParams);
        registeredCodes.add("log-track"); logisticsCreate("log-track", defaultParams);
        registeredCodes.add("log-sign"); logisticsDelivery("log-sign", defaultParams);
        registeredCodes.add("log-pickup"); logisticsCreate("log-pickup", defaultParams);
        registeredCodes.add("log-sort"); logisticsDelivery("log-sort", defaultParams);
        registeredCodes.add("log-express"); logisticsCreate("log-express", defaultParams);
        registeredCodes.add("log-cod"); logisticsDelivery("log-cod", defaultParams);
        registeredCodes.add("log-insure"); logisticsCreate("log-insure", defaultParams);
        registeredCodes.add("log-warehouse"); logisticsDelivery("log-warehouse", defaultParams);
        registeredCodes.add("log-allocate"); logisticsCreate("log-allocate", defaultParams);
        registeredCodes.add("log-route"); logisticsDelivery("log-route", defaultParams);

        // 用户场景 (56-68)
        registeredCodes.add("user-register"); userRegister("user-register", defaultParams);
        registeredCodes.add("user-login"); userLogin("user-login", defaultParams);
        registeredCodes.add("user-profile"); userProfile("user-profile", defaultParams);
        registeredCodes.add("user-tag"); userTag("user-tag", defaultParams);
        registeredCodes.add("user-auth"); userLogin("user-auth", defaultParams);
        registeredCodes.add("user-logout"); userLogin("user-logout", defaultParams);
        registeredCodes.add("user-forget"); userRegister("user-forget", defaultParams);
        registeredCodes.add("user-reset"); userProfile("user-reset", defaultParams);
        registeredCodes.add("user-bind"); userRegister("user-bind", defaultParams);
        registeredCodes.add("user-unbind"); userProfile("user-unbind", defaultParams);
        registeredCodes.add("user-freeze"); userLogin("user-freeze", defaultParams);
        registeredCodes.add("user-unfreeze"); userRegister("user-unfreeze", defaultParams);
        registeredCodes.add("user-delete"); userProfile("user-delete", defaultParams);

        // 审批场景 (69-82)
        registeredCodes.add("appr-simple"); approvalSimple("appr-simple", defaultParams);
        registeredCodes.add("appr-multi"); approvalMulti("appr-multi", defaultParams);
        registeredCodes.add("appr-order"); approvalSimple("appr-order", defaultParams);
        registeredCodes.add("appr-refund"); approvalMulti("appr-refund", defaultParams);
        registeredCodes.add("appr-withdraw"); approvalSimple("appr-withdraw", defaultParams);
        registeredCodes.add("appr-reject"); approvalMulti("appr-reject", defaultParams);
        registeredCodes.add("appr-escalate"); approvalSimple("appr-escalate", defaultParams);
        registeredCodes.add("appr-auto"); approvalMulti("appr-auto", defaultParams);
        registeredCodes.add("appr-manual"); approvalSimple("appr-manual", defaultParams);
        registeredCodes.add("appr-schedule"); approvalMulti("appr-schedule", defaultParams);
        registeredCodes.add("appr-group"); approvalSimple("appr-group", defaultParams);
        registeredCodes.add("appr-chain"); approvalMulti("appr-chain", defaultParams);
        registeredCodes.add("appr-urgent"); approvalSimple("appr-urgent", defaultParams);
        registeredCodes.add("appr-archive"); approvalMulti("appr-archive", defaultParams);

        // 通知场景 (83-96)
        registeredCodes.add("notify-sms"); notifySms("notify-sms", defaultParams);
        registeredCodes.add("notify-email"); notifyEmail("notify-email", defaultParams);
        registeredCodes.add("notify-push"); notifyPush("notify-push", defaultParams);
        registeredCodes.add("notify-wechat"); notifySms("notify-wechat", defaultParams);
        registeredCodes.add("notify-alipay"); notifyEmail("notify-alipay", defaultParams);
        registeredCodes.add("notify-system"); notifyPush("notify-system", defaultParams);
        registeredCodes.add("notify-batch"); notifySms("notify-batch", defaultParams);
        registeredCodes.add("notify-delay"); notifyEmail("notify-delay", defaultParams);
        registeredCodes.add("notify-template"); notifyPush("notify-template", defaultParams);
        registeredCodes.add("notify-custom"); notifySms("notify-custom", defaultParams);
        registeredCodes.add("notify-voice"); notifyEmail("notify-voice", defaultParams);
        registeredCodes.add("notify-fax"); notifyPush("notify-fax", defaultParams);
        registeredCodes.add("notify-letter"); notifySms("notify-letter", defaultParams);
        registeredCodes.add("notify-inapp"); notifyEmail("notify-inapp", defaultParams);

        // 数据处理场景 (97-110)
        registeredCodes.add("data-transform"); dataTransform("data-transform", defaultParams);
        registeredCodes.add("data-filter"); dataFilter("data-filter", defaultParams);
        registeredCodes.add("data-aggregate"); dataAggregate("data-aggregate", defaultParams);
        registeredCodes.add("data-split"); dataSplit("data-split", defaultParams);
        registeredCodes.add("data-join"); dataTransform("data-join", defaultParams);
        registeredCodes.add("data-sort"); dataFilter("data-sort", defaultParams);
        registeredCodes.add("data-group"); dataAggregate("data-group", defaultParams);
        registeredCodes.add("data-pivot"); dataSplit("data-pivot", defaultParams);
        registeredCodes.add("data-merge"); dataTransform("data-merge", defaultParams);
        registeredCodes.add("data-calculate"); dataFilter("data-calculate", defaultParams);
        registeredCodes.add("data-format"); dataAggregate("data-format", defaultParams);
        registeredCodes.add("data-validate"); dataSplit("data-validate", defaultParams);
        registeredCodes.add("data-normalize"); dataTransform("data-normalize", defaultParams);
        registeredCodes.add("data-enrich"); dataFilter("data-enrich", defaultParams);

        // API集成场景 (111-125)
        registeredCodes.add("api-http-get"); apiHttp("api-http-get", defaultParams);
        registeredCodes.add("api-http-post"); apiPost("api-http-post", defaultParams);
        registeredCodes.add("api-http-put"); apiHttp("api-http-put", defaultParams);
        registeredCodes.add("api-http-delete"); apiPost("api-http-delete", defaultParams);
        registeredCodes.add("api-rest-get"); apiHttp("api-rest-get", defaultParams);
        registeredCodes.add("api-rest-post"); apiPost("api-rest-post", defaultParams);
        registeredCodes.add("api-graphql"); apiHttp("api-graphql", defaultParams);
        registeredCodes.add("api-grpc"); apiPost("api-grpc", defaultParams);
        registeredCodes.add("api-soap"); apiHttp("api-soap", defaultParams);
        registeredCodes.add("api-oauth"); apiPost("api-oauth", defaultParams);
        registeredCodes.add("api-token"); apiHttp("api-token", defaultParams);
        registeredCodes.add("api-sign"); apiPost("api-sign", defaultParams);
        registeredCodes.add("api-encrypt"); apiHttp("api-encrypt", defaultParams);
        registeredCodes.add("api-decrypt"); apiPost("api-decrypt", defaultParams);
        registeredCodes.add("api-retry"); apiHttp("api-retry", defaultParams);

        // 缓存场景 (126-135)
        registeredCodes.add("cache-read"); cacheRead("cache-read", defaultParams);
        registeredCodes.add("cache-write"); cacheWrite("cache-write", defaultParams);
        registeredCodes.add("cache-delete"); cacheRead("cache-delete", defaultParams);
        registeredCodes.add("cache-update"); cacheWrite("cache-update", defaultParams);
        registeredCodes.add("cache-invalidate"); cacheRead("cache-invalidate", defaultParams);
        registeredCodes.add("cache-refresh"); cacheWrite("cache-refresh", defaultParams);
        registeredCodes.add("cache-warm"); cacheRead("cache-warm", defaultParams);
        registeredCodes.add("cache-miss"); cacheWrite("cache-miss", defaultParams);
        registeredCodes.add("cache-hit"); cacheRead("cache-hit", defaultParams);
        registeredCodes.add("cache-batch"); cacheWrite("cache-batch", defaultParams);

        // MQ场景 (136-145)
        registeredCodes.add("mq-produce"); mqProduce("mq-produce", defaultParams);
        registeredCodes.add("mq-consume"); mqConsume("mq-consume", defaultParams);
        registeredCodes.add("mq-publish"); mqProduce("mq-publish", defaultParams);
        registeredCodes.add("mq-subscribe"); mqConsume("mq-subscribe", defaultParams);
        registeredCodes.add("mq-request"); mqProduce("mq-request", defaultParams);
        registeredCodes.add("mq-response"); mqConsume("mq-response", defaultParams);
        registeredCodes.add("mq-batch"); mqProduce("mq-batch", defaultParams);
        registeredCodes.add("mq-delay"); mqConsume("mq-delay", defaultParams);
        registeredCodes.add("mq-dlx"); mqProduce("mq-dlx", defaultParams);
        registeredCodes.add("mq-transaction"); mqConsume("mq-transaction", defaultParams);

        // 复合场景 (146-155)
        registeredCodes.add("full-order"); fullOrderFlow("full-order", defaultParams);
        registeredCodes.add("complex-dag"); complexDagFlow("complex-dag", defaultParams);
        registeredCodes.add("fork-join"); withForkJoin("fork-join", defaultParams);
        registeredCodes.add("try-catch"); withTryCatch("try-catch", defaultParams);
        registeredCodes.add("while-loop"); withWhile("while-loop", defaultParams);
        registeredCodes.add("with-logger"); withLogger("with-logger", defaultParams);
        registeredCodes.add("with-delay"); withDelay("with-delay", defaultParams);
        registeredCodes.add("full-payment"); fullOrderFlow("full-payment", defaultParams);
        registeredCodes.add("full-delivery"); complexDagFlow("full-delivery", defaultParams);
        registeredCodes.add("full-refund"); withForkJoin("full-refund", defaultParams);

        log.info("已注册 {} 条示例链条", registeredCodes.size());
        return registeredCodes;
    }
}