package com.zestflow.executor.engine;

import com.zestflow.common.model.dto.ChainExecuteResultDTO;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 链执行引擎接口
 * <p>
 * 负责接收执行请求，按 DAG 拓扑结构编排节点运行，返回执行结果。
 */
public interface ChainExecutionEngine {

    /**
     * 同步执行链
     *
     * @param chainCode 链编码
     * @param params    执行参数
     * @return 执行结果
     */
    ChainExecuteResultDTO execute(String chainCode, Map<String, Object> params);

    /**
     * 异步执行链（用于长耗时链）
     */
    CompletableFuture<ChainExecuteResultDTO> executeAsync(String chainCode, Map<String, Object> params);

    /**
     * 终止某个运行中的实例
     *
     * @param instanceId 实例 ID
     * @return 是否成功终止
     */
    boolean stop(String instanceId);

    /**
     * 终止某个链的所有运行实例
     *
     * @param chainCode 链编码
     * @return 终止的实例数
     */
    int stopByChain(String chainCode);

    /**
     * 查询运行中的实例
     */
    List<ChainInstance> listRunning(String chainCode);
}
