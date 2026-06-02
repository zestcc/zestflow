package com.zestflow.executor.controller;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.model.dto.ChainExecuteRequestDTO;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.executor.chain.ChainDefinition;
import com.zestflow.executor.chain.ChainManager;
import com.zestflow.executor.engine.ChainExecutionEngine;
import com.zestflow.executor.engine.ExecutionIdempotencyGuard;
import com.zestflow.executor.registry.ExecutorProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 统一执行端点 — Spring MVC 版 {@code POST /execute}
 * <p>
 * 作为业务应用的统一链执行入口，接收 chainCode + params，
 * 执行 DAG 后返回终端节点（出边为空）的输出数据。
 */
@Slf4j
@RestController
public class ExecutionController {

    private final ChainExecutionEngine executionEngine;
    private final ChainManager chainManager;
    private final ExecutionIdempotencyGuard idempotencyGuard;
    private final ExecutorProperties executorProperties;

    public ExecutionController(ChainExecutionEngine executionEngine,
                               ChainManager chainManager,
                               ExecutionIdempotencyGuard idempotencyGuard,
                               ExecutorProperties executorProperties) {
        this.executionEngine = executionEngine;
        this.chainManager = chainManager;
        this.idempotencyGuard = idempotencyGuard;
        this.executorProperties = executorProperties;
    }

    @PostMapping("/execute")
    public ResponseEntity<Map<String, Object>> execute(@RequestBody ChainExecuteRequestDTO request) {
        String chainCode = request.getChainCode();
        log.info("统一执行端点请求 chainCode={}", chainCode);

        long startTime = System.currentTimeMillis();
        ChainExecuteResultDTO result = executeWithIdempotency(request);

        if (result.getStatus() == ChainConstants.CHAIN_FAILED) {
            log.warn("链执行失败 chainCode={} error={}", chainCode, result.getErrorMessage());
            return ResponseEntity.ok(buildResponse(false, chainCode, result.getInstanceId(),
                    result.getStatus(), System.currentTimeMillis() - startTime,
                    null, result.getErrorMessage()));
        }

        Map<String, Object> terminalOutputs = extractTerminalOutputs(chainCode, result);

        return ResponseEntity.ok(buildResponse(true, chainCode, result.getInstanceId(),
                result.getStatus(), result.getCostMs() != null ? result.getCostMs() : System.currentTimeMillis() - startTime,
                terminalOutputs, null));
    }

    private ChainExecuteResultDTO executeWithIdempotency(ChainExecuteRequestDTO request) {
        if (!executorProperties.isIdempotencyEnabled()) {
            return executionEngine.execute(request.getChainCode(), request.getParams());
        }
        return idempotencyGuard.execute(
                request.resolveIdempotencyKey(),
                executorProperties.getIdempotencyTtlMs(),
                executorProperties.getIdempotencyWaitMs(),
                () -> executionEngine.execute(request.getChainCode(), request.getParams()));
    }

    private Map<String, Object> buildResponse(boolean success, String chainCode,
                                              String instanceId, int status, long costMs,
                                              Map<String, Object> outputs,
                                              String errorMessage) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", success);
        resp.put("instanceId", instanceId);
        resp.put("chainCode", chainCode);
        resp.put("status", status);
        resp.put("costMs", costMs);
        resp.put("outputs", outputs != null ? outputs : Map.of());
        resp.put("errorMessage", errorMessage);
        return resp;
    }

    /**
     * 提取链 DAG 中终端节点（出边为空）的输出数据
     */
    private Map<String, Object> extractTerminalOutputs(String chainCode,
                                                        ChainExecuteResultDTO engineResult) {
        ChainDefinition definition = chainManager.get(chainCode);
        if (definition == null || definition.getNodes() == null) {
            return Map.of();
        }

        // 找出所有不是任何边 source 的节点 = 终端节点
        Set<String> terminalIds = new HashSet<>(definition.getNodes().keySet());
        if (definition.getEdges() != null) {
            for (ChainDefinition.ChainEdge edge : definition.getEdges()) {
                terminalIds.remove(edge.getSource());
            }
        }

        // 从节点结果中提取终端节点的 outputData
        Map<String, Object> outputs = new LinkedHashMap<>();
        if (engineResult.getNodeResults() != null) {
            engineResult.getNodeResults().stream()
                    .filter(nr -> terminalIds.contains(nr.getNodeId()))
                    .forEach(nr -> outputs.put(nr.getNodeId(),
                            nr.getOutputData() != null ? nr.getOutputData() : Map.of()));
        }
        return outputs;
    }
}
