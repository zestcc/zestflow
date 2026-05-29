package com.zestflow.common.constant;

/**
 * 链路相关常量
 */
public final class ChainConstants {

    private ChainConstants() {}

    /* ========== 链状态 ========== */
    /** 启用 */
    public static final int CHAIN_ENABLED = 1;
    /** 停用 */
    public static final int CHAIN_DISABLED = 0;

    /* ========== 链运行状态 ========== */
    public static final int CHAIN_INIT = 0;
    public static final int CHAIN_LOADING = 1;
    public static final int CHAIN_READY = 2;
    public static final int CHAIN_RUNNING = 3;
    public static final int CHAIN_SUCCESS = 4;
    public static final int CHAIN_FAILED = 5;
    public static final int CHAIN_TIMEOUT = 6;
    public static final int CHAIN_COMPENSATED = 7;
    public static final int CHAIN_STOPPED = 8;

    /* ========== 节点运行状态 ========== */
    public static final int NODE_CREATED = 0;
    public static final int NODE_READY = 1;
    public static final int NODE_RUNNING = 2;
    public static final int NODE_SUCCESS = 3;
    public static final int NODE_FAILED = 4;
    public static final int NODE_TIMEOUT = 5;
    public static final int NODE_RETRYING = 6;
    public static final int NODE_FALLBACKING = 7;
    public static final int NODE_SKIPPED = 8;
    public static final int NODE_COMPENSATED = 9;

    /* ========== 节点类型 ========== */
    /** 标准节点 */
    public static final String NODE_TYPE_NORMAL = "NORMAL";
    /** 条件节点 */
    public static final String NODE_TYPE_CONDITION = "CONDITION";
    /** 脚本节点 */
    public static final String NODE_TYPE_SCRIPT = "SCRIPT";
    /** 子链节点 */
    public static final String NODE_TYPE_SUB_CHAIN = "SUB_CHAIN";
    /** 迭代节点 */
    public static final String NODE_TYPE_ITERATOR = "ITERATOR";

    /* ========== 默认值 ========== */
    /** 链默认超时 60s */
    public static final long DEFAULT_CHAIN_TIMEOUT_MS = 60_000L;
    /** 节点默认超时 30s */
    public static final long DEFAULT_NODE_TIMEOUT_MS = 30_000L;
    /** 节点默认重试次数 */
    public static final int DEFAULT_RETRY_COUNT = 0;
    /** 节点默认重试间隔 1s */
    public static final long DEFAULT_RETRY_INTERVAL_MS = 1_000L;
    /** 同层默认并行阈值 */
    public static final int DEFAULT_PARALLEL_THRESHOLD = 3;
    /** 熔断器默认阈值 */
    public static final int DEFAULT_CIRCUIT_BREAKER_THRESHOLD = 5;
    /** 熔断器默认恢复时间 30s */
    public static final long DEFAULT_CIRCUIT_BREAKER_RECOVERY_MS = 30_000L;

    /* ========== 错误策略 ========== */
    /** 失败即终止 */
    public static final String ERROR_STRATEGY_STOP = "STOP";
    /** 忽略失败继续执行 */
    public static final String ERROR_STRATEGY_CONTINUE = "CONTINUE";
    /** 触发 Saga 补偿 */
    public static final String ERROR_STRATEGY_COMPENSATE = "COMPENSATE";
}
