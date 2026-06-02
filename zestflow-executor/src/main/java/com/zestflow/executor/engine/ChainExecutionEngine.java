package com.zestflow.executor.engine;

import com.zestflow.common.model.dto.ChainExecuteResultDTO;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 链执行引擎接口
 * <p>
 * 负责接收执行请求，按 DAG 拓扑结构编排节点运行，返回执行结果。
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 简单模式：直接传入业务对象，无需了解 DataBus
 * ChainExecuteResultDTO result = engine.execute("order-flow", orderData, userInfo);
 * OrderInfo output = result.getData(OrderInfo.class);
 *
 * // 高级模式：通过 Map 显式传递 DataBus 参数
 * Map<String, Object> params = new HashMap<>();
 * params.put("source", "API");
 * ChainExecuteResultDTO result = engine.execute("order-flow", params, orderData);
 * }</pre>
 */
public interface ChainExecutionEngine {

    /**
     * 执行链（简单模式）
     * <p>
     * 直接传入业务对象，引擎自动按类型注册到上下文。
     * 元件方法声明对应类型参数即可自动注入。
     *
     * @param chainCode 链编码
     * @param args      业务对象（引擎按类型注册到上下文）
     * @return 执行结果
     */
    ChainExecuteResultDTO execute(String chainCode, Object... args);

    /**
     * 执行链（高级模式）
     * <p>
     * 通过 Map 显式传递 DataBus 参数，同时支持类型化业务对象注入。
     * 适合需要精细控制 DataBus 键值对的场景。
     *
     * @param chainCode 链编码
     * @param params    DataBus 参数（key-value 模式，可为 null）
     * @param args      业务对象（引擎按类型注册到上下文）
     * @return 执行结果
     */
    ChainExecuteResultDTO execute(String chainCode, Map<String, Object> params, Object... args);

    /**
     * 执行链（继承父链绝对 deadline，用于子链节点）
     *
     * @param parentDeadlineMs 父链绝对 deadline 时间戳（毫秒）；{@link Long#MAX_VALUE} 表示无父约束
     */
    ChainExecuteResultDTO executeWithDeadline(String chainCode, Map<String, Object> params, long parentDeadlineMs);

    /**
     * 异步执行链（用于长耗时链）
     */
    CompletableFuture<ChainExecuteResultDTO> executeAsync(String chainCode, Object... args);

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
