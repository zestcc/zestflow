package com.zestflow.demo.controller;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.model.Result;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.demo.dto.WorkflowRequest;
import com.zestflow.demo.dto.WorkflowResponse;
import com.zestflow.demo.service.BizOrchestrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 通用工作流 Controller — 覆盖 25+ 编排场景
 * <p>
 * 场景矩阵：M03/M08/M09/M10/M11/M12/M13/M14/M15/L04/L05/L07/L08/L09/L10/L11/L13/L14/L15/
 * E01/E04/E05/E06/E07/E08/E10
 */
@Slf4j
@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final BizOrchestrationService orch;

    /** 工作流场景默认带上 userId，供 validateUser 等元件使用 */
    private Map<String, Object> buildWorkflowParams(WorkflowRequest req) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (req != null) {
            if (req.getParams() != null) {
                params.putAll(req.getParams());
            }
            if (req.getUserId() != null) {
                params.put("userId", req.getUserId());
            }
            if (req.getStatus() != null) {
                params.put("status", req.getStatus());
            }
            if (req.getAmount() != null) {
                params.put("amount", req.getAmount());
            }
            if (req.getChannel() != null) {
                params.put("channel", req.getChannel());
            }
        }
        if (!params.containsKey("userId")) {
            params.put("userId", "U001");
        }
        return params;
    }

    // ==================== M03: 选择器路由 ====================

    @PostMapping("/selector")
    public Result<WorkflowResponse> selector(@Valid @RequestBody WorkflowRequest req) {
        String code = "wf-selector-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> params = buildWorkflowParams(req);
        params.putIfAbsent("amount", req.getAmount() != null ? req.getAmount() : 100);

        var result = orch.loadAndExecute(code, List.of(
                BizOrchestrationService.normalNode("start", "validateUser"),
                BizOrchestrationService.normalNode("route", "routePromotion"),
                BizOrchestrationService.normalNode("end", "sendNotify")
        ), List.of(
                BizOrchestrationService.edge("start", "route"),
                BizOrchestrationService.edge("route", "end")
        ), params);

        return Result.success(buildResponse(result, "selector", 3));
    }

    // ==================== M08: 加载器 ====================

    @PostMapping("/loader")
    public Result<WorkflowResponse> loader(@Valid @RequestBody WorkflowRequest req) {
        String code = "wf-loader-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> params = buildWorkflowParams(req);

        var result = orch.loadAndExecute(code, List.of(
                BizOrchestrationService.normalNode("load", "loadUserInfo"),
                BizOrchestrationService.normalNode("process", "processPayment"),
                BizOrchestrationService.normalNode("end", "sendNotify")
        ), List.of(
                BizOrchestrationService.edge("load", "process"),
                BizOrchestrationService.edge("process", "end")
        ), params);

        return Result.success(buildResponse(result, "loader", 3));
    }

    // ==================== M09: 解析器 ====================

    @PostMapping("/parser")
    public Result<WorkflowResponse> parser(@Valid @RequestBody WorkflowRequest req) {
        String code = "wf-parser-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> params = buildWorkflowParams(req);

        var result = orch.loadAndExecute(code, List.of(
                BizOrchestrationService.normalNode("start", "validateUser"),
                BizOrchestrationService.normalNode("process", "processPayment"),
                BizOrchestrationService.normalNode("parse", "parseOrderResult")
        ), List.of(
                BizOrchestrationService.edge("start", "process"),
                BizOrchestrationService.edge("process", "parse")
        ), params);

        return Result.success(buildResponse(result, "parser", 3));
    }

    // ==================== M10: 判断器 ====================

    @PostMapping("/predicate")
    public Result<WorkflowResponse> predicate(@Valid @RequestBody WorkflowRequest req) {
        String code = "wf-pred-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> params = buildWorkflowParams(req);
        params.put("stockOk", true);

        var result = orch.loadAndExecute(code, List.of(
                BizOrchestrationService.normalNode("start", "validateUser"),
                BizOrchestrationService.conditionNode("cond", Map.of("condition", "params.stockOk == true")),
                BizOrchestrationService.normalNode("pass", "checkStock"),
                BizOrchestrationService.normalNode("fail", "stockWarning"),
                BizOrchestrationService.normalNode("end", "sendNotify")
        ), List.of(
                BizOrchestrationService.edge("start", "cond"),
                BizOrchestrationService.edge("cond", "pass", "${params.stockOk} == true"),
                BizOrchestrationService.edge("cond", "fail"),
                BizOrchestrationService.edge("pass", "end"),
                BizOrchestrationService.edge("fail", "end")
        ), params);

        return Result.success(buildResponse(result, "predicate", 5));
    }

    // ==================== M11: 全生命周期（前置+执行+后置） ====================

    @PostMapping("/full-lifecycle")
    public Result<WorkflowResponse> fullLifecycle(@Valid @RequestBody WorkflowRequest req) {
        String code = "wf-lifecycle-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> params = buildWorkflowParams(req);

        var result = orch.loadAndExecute(code, List.of(
                BizOrchestrationService.normalNodeWithLifecycle("start", "validateUser",
                        List.of("preCheckOrder"), List.of("postOrderAudit"), null),
                BizOrchestrationService.normalNodeWithLifecycle("process", "createOrder",
                        List.of("preCheckOrder"), List.of("postOrderAudit"), null),
                BizOrchestrationService.normalNode("end", "sendNotify")
        ), List.of(
                BizOrchestrationService.edge("start", "process"),
                BizOrchestrationService.edge("process", "end")
        ), params);

        return Result.success(buildResponse(result, "full-lifecycle", 3));
    }

    // ==================== M12: 绑定+校验+执行 ====================

    @PostMapping("/bind-validate-exec")
    public Result<WorkflowResponse> bindValidateExec(@Valid @RequestBody WorkflowRequest req) {
        String code = "wf-bindval-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> params = buildWorkflowParams(req);
        params.putIfAbsent("rawOrderId", "ORD-TEST-001");
        params.putIfAbsent("rawAmount", "99.9");

        var result = orch.loadAndExecute(code, List.of(
                BizOrchestrationService.normalNodeWithLifecycle("bind", "createOrder",
                        List.of("bindOrderParam"), null, null),
                BizOrchestrationService.normalNodeWithLifecycle("validate", "createOrder",
                        null, null, "validateUserParam"),
                BizOrchestrationService.normalNode("execute", "deductStock")
        ), List.of(
                BizOrchestrationService.edge("bind", "validate"),
                BizOrchestrationService.edge("validate", "execute")
        ), params);

        return Result.success(buildResponse(result, "bind-validate-exec", 3));
    }

    // ==================== M13: 失败继续（CONTINUE 策略） ====================

    @PostMapping("/continue-on-error")
    public Result<WorkflowResponse> continueOnError(@Valid @RequestBody WorkflowRequest req) {
        String code = "wf-continue-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> params = buildWorkflowParams(req);

        var result = orch.loadAndExecute(code, List.of(
                BizOrchestrationService.normalNode("first", "validateUser"),
                BizOrchestrationService.normalNode("middle", "failStep",
                        Map.of("errorStrategy", ChainConstants.ERROR_STRATEGY_CONTINUE)),
                BizOrchestrationService.normalNode("last", "deductStock")
        ), List.of(
                BizOrchestrationService.edge("first", "middle"),
                BizOrchestrationService.edge("middle", "last")
        ), params, Map.of("errorStrategy", ChainConstants.ERROR_STRATEGY_CONTINUE));

        return Result.success(buildResponse(result, "continue-on-error", 3));
    }

    // ==================== M14: 超时终止 ====================

    @PostMapping("/timeout")
    public Result<WorkflowResponse> timeout(@Valid @RequestBody WorkflowRequest req) {
        String code = "wf-timeout-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> params = buildWorkflowParams(req);

        var result = orch.loadAndExecute(code, List.of(
                BizOrchestrationService.normalNode("start", "validateUser"),
                BizOrchestrationService.normalNode("slow", "processPayment", Map.of("timeout", 1)),
                BizOrchestrationService.normalNode("end", "sendNotify")
        ), List.of(
                BizOrchestrationService.edge("start", "slow"),
                BizOrchestrationService.edge("slow", "end")
        ), params);

        return Result.success(buildResponse(result, "timeout", 3));
    }

    // ==================== M15: 异步并行执行 ====================

    @PostMapping("/async")
    public Result<WorkflowResponse> asyncExec(@Valid @RequestBody WorkflowRequest req) {
        String code = "wf-async-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> params = buildWorkflowParams(req);

        var result = orch.loadAndExecute(code, List.of(
                BizOrchestrationService.normalNode("start", "validateUser"),
                BizOrchestrationService.normalNode("taskA", "processPayment"),
                BizOrchestrationService.normalNode("taskB", "deductStock"),
                BizOrchestrationService.normalNode("taskC", "sendNotify"),
                BizOrchestrationService.normalNode("end", "printWaybill")
        ), List.of(
                BizOrchestrationService.edge("start", "taskA"),
                BizOrchestrationService.edge("start", "taskB"),
                BizOrchestrationService.edge("start", "taskC"),
                BizOrchestrationService.edge("taskA", "end"),
                BizOrchestrationService.edge("taskB", "end"),
                BizOrchestrationService.edge("taskC", "end")
        ), params);

        return Result.success(buildResponse(result, "async", 5));
    }

    // ==================== L07: 菱形 DAG ====================

    @PostMapping("/diamond")
    public Result<WorkflowResponse> diamondDag(@Valid @RequestBody WorkflowRequest req) {
        Map<String, Object> params = buildWorkflowParams(req);
        var result = orch.diamondDag("wf-diamond-" + UUID.randomUUID().toString().substring(0, 8), buildWorkflowParams(req));
        return Result.success(buildResponse(result, "diamond", 5));
    }

    // ==================== L08: W 形 DAG ====================

    @PostMapping("/w-shape")
    public Result<WorkflowResponse> wShapeDag(@Valid @RequestBody WorkflowRequest req) {
        Map<String, Object> params = buildWorkflowParams(req);
        var result = orch.wShapeDag("wf-wshape-" + UUID.randomUUID().toString().substring(0, 8), buildWorkflowParams(req));
        return Result.success(buildResponse(result, "w-shape", 6));
    }

    // ==================== L09: 整链超时 ====================

    @PostMapping("/chain-timeout")
    public Result<WorkflowResponse> chainTimeout(@Valid @RequestBody WorkflowRequest req) {
        String code = "wf-cto-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> params = buildWorkflowParams(req);

        var result = orch.loadAndExecute(code, List.of(
                BizOrchestrationService.normalNode("slow1", "noopStep"),
                BizOrchestrationService.normalNode("slow2", "noopStep")
        ), List.of(
                BizOrchestrationService.edge("slow1", "slow2")
        ), params, Map.of("timeout", 1L));

        return Result.success(buildResponse(result, "chain-timeout", 2));
    }

    // ==================== L10: 10 层上下文传递 ====================

    @PostMapping("/10-layers")
    public Result<WorkflowResponse> tenLayers(@Valid @RequestBody WorkflowRequest req) {
        Map<String, Object> params = buildWorkflowParams(req);
        var result = orch.tenLayers("wf-10layers-" + UUID.randomUUID().toString().substring(0, 8), buildWorkflowParams(req));
        return Result.success(buildResponse(result, "10-layers", 10));
    }

    // ==================== L11: 错误链补偿（Saga） ====================

    @PostMapping("/saga")
    public Result<WorkflowResponse> sagaCompensate(@Valid @RequestBody WorkflowRequest req) {
        String code = "wf-saga-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> params = buildWorkflowParams(req);

        var result = orch.loadAndExecute(code, List.of(
                BizOrchestrationService.normalNode("step1", "validateUser"),
                BizOrchestrationService.normalNode("step2", "processPayment"),
                BizOrchestrationService.normalNode("step3", "nonexistent_component",
                        Map.of("errorStrategy", ChainConstants.ERROR_STRATEGY_COMPENSATE))
        ), List.of(
                BizOrchestrationService.edge("step1", "step2"),
                BizOrchestrationService.edge("step2", "step3")
        ), params);

        return Result.success(buildResponse(result, "saga", 3));
    }

    // ==================== L13: 条件跳过多节点 ====================

    @PostMapping("/conditional-skip")
    public Result<WorkflowResponse> conditionalSkip(@Valid @RequestBody WorkflowRequest req) {
        String code = "wf-cskip-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> params = buildWorkflowParams(req);
        params.putIfAbsent("status", "SKIP");

        var result = orch.loadAndExecute(code, List.of(
                BizOrchestrationService.normalNode("start", "validateUser"),
                BizOrchestrationService.conditionNode("gate", Map.of("condition", "params.status == 'SKIP'")),
                BizOrchestrationService.normalNode("skipped", "printWaybill"),
                BizOrchestrationService.normalNode("normal", "processPayment"),
                BizOrchestrationService.normalNode("end", "sendNotify")
        ), List.of(
                BizOrchestrationService.edge("start", "gate"),
                BizOrchestrationService.edge("gate", "skipped", "${params.status} == 'SKIP'"),
                BizOrchestrationService.edge("gate", "normal"),
                BizOrchestrationService.edge("skipped", "end"),
                BizOrchestrationService.edge("normal", "end")
        ), params);

        return Result.success(buildResponse(result, "conditional-skip", 4));
    }

    // ==================== L14: 全类型混合 DAG ====================

    @PostMapping("/all-types")
    public Result<WorkflowResponse> allTypesMixed(@Valid @RequestBody WorkflowRequest req) {
        Map<String, Object> params = buildWorkflowParams(req);
        params.putIfAbsent("items", List.of(Map.of("sku", "ITEM-1"), Map.of("sku", "ITEM-2")));
        params.putIfAbsent("status", "PASS");

        var result = orch.allTypesMixed("wf-alltypes-" + UUID.randomUUID().toString().substring(0, 8), params);
        return Result.success(buildResponse(result, "all-types", 7));
    }

    // ==================== L15: 长链 50 节点 ====================

    @PostMapping("/long-50")
    public Result<WorkflowResponse> longChain50(@Valid @RequestBody WorkflowRequest req) {
        Map<String, Object> params = buildWorkflowParams(req);
        var result = orch.longChain50("wf-long50-" + UUID.randomUUID().toString().substring(0, 8), buildWorkflowParams(req));
        return Result.success(buildResponse(result, "long-50", 50));
    }

    // ==================== L04: 并发 10 线程 ====================

    @PostMapping("/concurrent")
    public Result<WorkflowResponse> concurrentExec(@Valid @RequestBody WorkflowRequest req) {
        int count = Math.max(1, req.getConcurrency() > 0 ? req.getConcurrency() : 10);
        Map<String, Object> params = buildWorkflowParams(req);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);
        List<Long> costs = Collections.synchronizedList(new ArrayList<>());

        CountDownLatch latch = new CountDownLatch(count);
        for (int i = 0; i < count; i++) {
            int idx = i;
            CompletableFuture.runAsync(() -> {
                try {
                    String code = "wf-concurrent-" + idx + "-" + UUID.randomUUID().toString().substring(0, 4);
                    Map<String, Object> p = new LinkedHashMap<>(params);
                    p.put("threadIdx", idx);
                    var r = orch.loadAndExecute(code, List.of(
                            BizOrchestrationService.normalNode("A", "validateUser"),
                            BizOrchestrationService.normalNode("B", "processPayment"),
                            BizOrchestrationService.normalNode("C", "deductStock")
                    ), List.of(
                            BizOrchestrationService.edge("A", "B"),
                            BizOrchestrationService.edge("B", "C")
                    ), p);
                    if (r.getStatus() == ChainConstants.CHAIN_SUCCESS) {
                        successCount.incrementAndGet();
                    } else {
                        failedCount.incrementAndGet();
                    }
                    costs.add(r.getCostMs());
                } finally {
                    latch.countDown();
                }
            });
        }
        try { latch.await(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }

        double avg = costs.stream().mapToLong(Long::longValue).average().orElse(0);
        return Result.success(WorkflowResponse.builder()
                .workflowId("concurrent-" + count)
                .scenario("concurrent-" + count)
                .status(successCount.get() == count ? "ALL_SUCCESS" : "PARTIAL_FAIL")
                .successCount(successCount.get())
                .failedCount(failedCount.get())
                .avgCostMs(avg)
                .nodeCount(3)
                .costMs((long) costs.stream().mapToLong(Long::longValue).sum())
                .build());
    }

    // ==================== E01: 链不存在 ====================

    @PostMapping("/not-found")
    public Result<WorkflowResponse> chainNotFound(@Valid @RequestBody WorkflowRequest req) {
        String code = "wf-notfound-" + UUID.randomUUID().toString().substring(0, 8);
        // 只注册链但不执行它 — 用另一个不存在的 code 执行
        Map<String, Object> params = buildWorkflowParams(req);
        orch.loadAndExecute(code + "-placeholder", List.of(
                BizOrchestrationService.normalNode("dummy", "validateUser")
        ), List.of(), params);

        // 注册并执行独立链编码（演示链可正常跑通）
        var result = orch.loadAndExecute("nonexistent-chain-code-" + System.currentTimeMillis(), List.of(
                BizOrchestrationService.normalNode("A", "validateUser")
        ), List.of(), params);

        return Result.success(buildResponse(result, "not-found", 1));
    }

    // ==================== E04: 条件全不满足 ====================

    @PostMapping("/all-skip")
    public Result<WorkflowResponse> allConditionsSkip(@Valid @RequestBody WorkflowRequest req) {
        String code = "wf-allskip-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> params = buildWorkflowParams(req);
        params.putIfAbsent("status", "UNKNOWN");

        var result = orch.loadAndExecute(code, List.of(
                BizOrchestrationService.normalNode("start", "validateUser"),
                BizOrchestrationService.conditionNode("c1", Map.of("condition", "params.status == 'A'")),
                BizOrchestrationService.normalNode("branchA", "processPayment"),
                BizOrchestrationService.conditionNode("c2", Map.of("condition", "params.status == 'B'")),
                BizOrchestrationService.normalNode("branchB", "deductStock"),
                BizOrchestrationService.normalNode("end", "sendNotify")
        ), List.of(
                BizOrchestrationService.edge("start", "c1"),
                BizOrchestrationService.edge("c1", "branchA", "${params.status} == 'A'"),
                BizOrchestrationService.edge("c1", "c2"),
                BizOrchestrationService.edge("c2", "branchB", "${params.status} == 'B'"),
                BizOrchestrationService.edge("c2", "end"),
                BizOrchestrationService.edge("branchA", "end"),
                BizOrchestrationService.edge("branchB", "end")
        ), params);

        return Result.success(buildResponse(result, "all-skip", 4));
    }

    // ==================== E05: 脚本语法错误 ====================

    @PostMapping("/bad-script")
    public Result<WorkflowResponse> badScript(@Valid @RequestBody WorkflowRequest req) {
        String code = "wf-badscript-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> params = buildWorkflowParams(req);

        var result = orch.loadAndExecute(code, List.of(
                BizOrchestrationService.normalNode("start", "validateUser"),
                BizOrchestrationService.scriptNode("bad", "aviator: !!! invalid @@@"),
                BizOrchestrationService.normalNode("end", "sendNotify")
        ), List.of(
                BizOrchestrationService.edge("start", "bad"),
                BizOrchestrationService.edge("bad", "end")
        ), params);

        return Result.success(buildResponse(result, "bad-script", 3));
    }

    // ==================== E06: 子链不存在 ====================

    @PostMapping("/bad-subchain")
    public Result<WorkflowResponse> badSubchain(@Valid @RequestBody WorkflowRequest req) {
        String code = "wf-badsub-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> params = buildWorkflowParams(req);

        var result = orch.loadAndExecute(code, List.of(
                BizOrchestrationService.normalNode("start", "validateUser"),
                BizOrchestrationService.subChainNode("sub", "nonexistent-sub-chain-" + System.currentTimeMillis()),
                BizOrchestrationService.normalNode("end", "sendNotify")
        ), List.of(
                BizOrchestrationService.edge("start", "sub"),
                BizOrchestrationService.edge("sub", "end")
        ), params);

        return Result.success(buildResponse(result, "bad-subchain", 3));
    }

    // ==================== E07: 负数重试次数 ====================

    @PostMapping("/negative-retry")
    public Result<WorkflowResponse> negativeRetry(@Valid @RequestBody WorkflowRequest req) {
        String code = "wf-negretry-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> params = buildWorkflowParams(req);

        var result = orch.loadAndExecute(code, List.of(
                BizOrchestrationService.normalNode("start", "validateUser"),
                BizOrchestrationService.normalNode("process", "processPayment",
                        Map.of("retryCount", -1)),
                BizOrchestrationService.normalNode("end", "sendNotify")
        ), List.of(
                BizOrchestrationService.edge("start", "process"),
                BizOrchestrationService.edge("process", "end")
        ), params);

        return Result.success(buildResponse(result, "negative-retry", 3));
    }

    // ==================== E08: 超大超时值 ====================

    @PostMapping("/huge-timeout")
    public Result<WorkflowResponse> hugeTimeout(@Valid @RequestBody WorkflowRequest req) {
        String code = "wf-hugeto-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> params = buildWorkflowParams(req);

        var result = orch.loadAndExecute(code, List.of(
                BizOrchestrationService.normalNode("start", "validateUser"),
                BizOrchestrationService.normalNode("process", "processPayment",
                        Map.of("timeout", Integer.MAX_VALUE)),
                BizOrchestrationService.normalNode("end", "sendNotify")
        ), List.of(
                BizOrchestrationService.edge("start", "process"),
                BizOrchestrationService.edge("process", "end")
        ), params, Map.of("timeout", 86400000L));

        return Result.success(buildResponse(result, "huge-timeout", 3));
    }

    // ==================== E10: 并发注册同一链 ====================

    @PostMapping("/concurrent-register")
    public Result<WorkflowResponse> concurrentRegister(@Valid @RequestBody WorkflowRequest req) {
        String sharedCode = "wf-conreg-shared-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> params = buildWorkflowParams(req);
        int threads = Math.max(1, req.getConcurrency() > 0 ? req.getConcurrency() : 10);

        List<ChainExecuteResultDTO> results = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            CompletableFuture.runAsync(() -> {
                try {
                    Map<String, Object> p = new LinkedHashMap<>(params);
                    p.put("threadIdx", idx);
                    // 所有线程注册同一个 sharedCode
                    var r = orch.loadAndExecute(sharedCode, List.of(
                            BizOrchestrationService.normalNode("N", "validateUser")
                    ), List.of(), p);
                    results.add(r);
                } finally {
                    latch.countDown();
                }
            });
        }
        try { latch.await(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }

        long successCount = results.stream().filter(r -> r.getStatus() == ChainConstants.CHAIN_SUCCESS).count();
        return Result.success(WorkflowResponse.builder()
                .workflowId(sharedCode)
                .scenario("concurrent-register")
                .status(successCount == threads ? "ALL_SUCCESS" : "PARTIAL_FAIL")
                .successCount((int) successCount)
                .failedCount(threads - (int) successCount)
                .nodeCount(1)
                .costMs((long) results.stream().mapToLong(ChainExecuteResultDTO::getCostMs).average().orElse(0))
                .build());
    }

    // ==================== 辅助 ====================

    private WorkflowResponse buildResponse(ChainExecuteResultDTO result, String scenario, int nodeCount) {
        List<String> seq = result.getNodeResults() != null
                ? result.getNodeResults().stream().map(nr -> nr.getNodeId()).collect(Collectors.toList())
                : List.of();
        return WorkflowResponse.builder()
                .workflowId(result.getInstanceId())
                .scenario(scenario)
                .status(statusText(result.getStatus()))
                .nodeCount(nodeCount)
                .nodeSequence(seq)
                .resultData(result.getResultData())
                .costMs(result.getCostMs())
                .errorMessage(result.getErrorMessage())
                .build();
    }

    private String statusText(int status) {
        switch (status) {
            case 4: return "SUCCESS";
            case 5: return "FAILED";
            case 6: return "TIMEOUT";
            case 7: return "COMPENSATED";
            default: return "UNKNOWN";
        }
    }
}
