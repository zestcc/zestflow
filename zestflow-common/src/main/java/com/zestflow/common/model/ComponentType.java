package com.zestflow.common.model;

/**
 * 执行元件类型（28 种，覆盖 99% 业务场景）
 * <p>
 * 分类体系：
 * <ul>
 *   <li>基础执行类：EXECUTOR / PREDICATE / SELECTOR / LOADER / PARSER</li>
 *   <li>数据处理类：TRANSFORMER / FILTER / AGGREGATOR / SPLITTER</li>
 *   <li>集成连接类：HTTP_CLIENT / MQ_PRODUCER / MQ_CONSUMER / CACHE_READER / CACHE_WRITER</li>
 *   <li>流程控制类：FORK / JOIN / TRY_CATCH / WHILE</li>
 *   <li>人工交互类：APPROVAL / NOTIFICATION</li>
 *   <li>辅助增强类：PRE_PROCESSOR / POST_PROCESSOR / PARAM_BINDER / PARAM_VALIDATOR / ERROR_HANDLER / LOGGER / DELAY</li>
 * </ul>
 */
public enum ComponentType {

    /* ========== 基础执行类 ========== */
    /** 执行器 — 执行业务逻辑（默认） */
    EXECUTOR,
    /** 判断器 — 返回 true/false，用于条件节点路由 */
    PREDICATE,
    /** 选择器 — 返回路由标识，用于多条件节点分支选择 */
    SELECTOR,
    /** 数据加载器 — 从外部获取数据写入上下文 */
    LOADER,
    /** 结果解析器 — 解析执行结果到上下文 */
    PARSER,

    /* ========== 数据处理类 ========== */
    /** 转换器 — 数据格式/协议转换（JSON→XML、DTO→VO 等） */
    TRANSFORMER,
    /** 过滤器 — 数据筛选、去重、条件过滤 */
    FILTER,
    /** 聚合器 — 多分支结果汇聚合并 */
    AGGREGATOR,
    /** 拆分器 — 将数据拆分为多个子集 */
    SPLITTER,

    /* ========== 集成连接类 ========== */
    /** HTTP 调用器 — 内置 HTTP 远程调用 */
    HTTP_CLIENT,
    /** 消息生产者 — 发送消息到队列 */
    MQ_PRODUCER,
    /** 消息消费者 — 从队列消费消息 */
    MQ_CONSUMER,
    /** 缓存读取器 — 从缓存读取数据 */
    CACHE_READER,
    /** 缓存写入器 — 向缓存写入数据 */
    CACHE_WRITER,

    /* ========== 流程控制类 ========== */
    /** 并行分叉 — 显式声明并行分支起点 */
    FORK,
    /** 并行汇聚 — 等待所有并行分支完成 */
    JOIN,
    /** 异常捕获 — 局部 try-catch 块 */
    TRY_CATCH,
    /** 条件循环 — while 循环控制 */
    WHILE,

    /* ========== 人工交互类 ========== */
    /** 审批器 — 人工审批节点（挂起/恢复） */
    APPROVAL,
    /** 通知器 — 邮件/短信/IM 通知发送 */
    NOTIFICATION,

    /* ========== 辅助增强类 ========== */
    /** 前置处理器 — 在节点主逻辑执行前运行 */
    PRE_PROCESSOR,
    /** 后置处理器 — 在节点主逻辑执行后运行 */
    POST_PROCESSOR,
    /** 参数绑定器 — 入参格式转换、数据组装 */
    PARAM_BINDER,
    /** 参数校验器 — 必填检查、业务规则校验 */
    PARAM_VALIDATOR,
    /** HTTP 错误处理器 — 链失败时生成对外响应体 */
    ERROR_HANDLER,
    /** 日志记录器 — 独立日志埋点 */
    LOGGER,
    /** 延迟器 — 定时等待/轮询间隔控制 */
    DELAY

}
