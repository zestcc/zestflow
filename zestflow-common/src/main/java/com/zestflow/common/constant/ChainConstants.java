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
    /** 选择器节点（多条件分支） */
    public static final String NODE_TYPE_SELECTOR = "SELECTOR";
    /** 脚本节点 */
    public static final String NODE_TYPE_SCRIPT = "SCRIPT";
    /** 子链节点 */
    public static final String NODE_TYPE_SUB_CHAIN = "SUB_CHAIN";
    /** 迭代节点 */
    public static final String NODE_TYPE_ITERATOR = "ITERATOR";
    /** 并行分叉节点 */
    public static final String NODE_TYPE_FORK = "FORK";
    /** 并行汇聚节点 */
    public static final String NODE_TYPE_JOIN = "JOIN";
    /** 异常捕获节点 */
    public static final String NODE_TYPE_TRY_CATCH = "TRY_CATCH";
    /** 条件循环节点 */
    public static final String NODE_TYPE_WHILE = "WHILE";
    /** 审批节点 */
    public static final String NODE_TYPE_APPROVAL = "APPROVAL";
    /** 通知节点 */
    public static final String NODE_TYPE_NOTIFICATION = "NOTIFICATION";
    /** 数据转换节点 */
    public static final String NODE_TYPE_TRANSFORMER = "TRANSFORMER";
    /** 数据过滤节点 */
    public static final String NODE_TYPE_FILTER = "FILTER";
    /** 数据聚合节点 */
    public static final String NODE_TYPE_AGGREGATOR = "AGGREGATOR";
    /** 数据拆分节点 */
    public static final String NODE_TYPE_SPLITTER = "SPLITTER";
    /** HTTP 调用节点 */
    public static final String NODE_TYPE_HTTP_CLIENT = "HTTP_CLIENT";
    /** 消息生产节点 */
    public static final String NODE_TYPE_MQ_PRODUCER = "MQ_PRODUCER";
    /** 消息消费节点 */
    public static final String NODE_TYPE_MQ_CONSUMER = "MQ_CONSUMER";
    /** 缓存读取节点 */
    public static final String NODE_TYPE_CACHE_READER = "CACHE_READER";
    /** 缓存写入节点 */
    public static final String NODE_TYPE_CACHE_WRITER = "CACHE_WRITER";
    /** 日志记录节点 */
    public static final String NODE_TYPE_LOGGER = "LOGGER";
    /** 延迟等待节点 */
    public static final String NODE_TYPE_DELAY = "DELAY";

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
    /** 子链最大递归深度 */
    public static final int MAX_SUB_CHAIN_DEPTH = 10;
    /** 迭代器最大迭代次数 */
    public static final int MAX_ITERATOR_COUNT = 10_000;
    /** WHILE 循环默认上限 */
    public static final int MAX_WHILE_ITERATIONS = 10_000;
    /** DELAY 节点最大延迟（24h，对标 BPMN Timer） */
    public static final long MAX_DELAY_MS = 86_400_000L;
    /** 链最大节点数 */
    public static final int MAX_CHAIN_NODE_COUNT = 100;
    /** 脚本执行超时（毫秒） */
    public static final long SCRIPT_EXECUTION_TIMEOUT_MS = 5_000L;

    /* ========== 错误策略 ========== */
    /** 失败即终止 */
    public static final String ERROR_STRATEGY_STOP = "STOP";
    /** 忽略失败继续执行 */
    public static final String ERROR_STRATEGY_CONTINUE = "CONTINUE";
    /** 触发 Saga 补偿 */
    public static final String ERROR_STRATEGY_COMPENSATE = "COMPENSATE";

    /** 节点超时：无限制（与 ChainValidator 约定一致） */
    public static final long NODE_TIMEOUT_UNLIMITED = -1L;

    /** ChainContext 元数据：绝对 deadline 时间戳（毫秒），供子链接管 */
    public static final String META_DEADLINE_MS = "deadlineMs";

    /** ChainContext 元数据：停止检查（BooleanSupplier），供 NodeRunner/RetryExecutor 感知 stop() */
    public static final String META_STOP_CHECK = "stopCheck";

    /** 链展示名称（写入 ChainContext metadata，供事件发布） */
    public static final String META_CHAIN_NAME = "chainName";

    /** ChainContext 元数据：前驱节点 returnValue，供 @ZestResult 注入 */
    public static final String META_PREDECESSOR_RESULT = "predecessorResult";

    /** ChainContext 元数据：链失败结果 DTO，供 @ZestFailure / errorHandler 注入 */
    public static final String META_CHAIN_FAILURE_RESULT = "chainFailureResult";

    /** CONTINUE 策略：上下文标记部分成功 */
    public static final String CTX_PARTIAL_FAILURE = "_partialFailure";

    /** CONTINUE 策略：失败节点 ID 列表 */
    public static final String CTX_FAILED_NODE_IDS = "_failedNodeIds";
}
