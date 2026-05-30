package com.zestflow.common.model;

/**
 * 执行元件类型
 */
public enum ComponentType {

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
    /** 前置处理器 — 在节点主逻辑执行前运行，用于参数校验、数据准备等 */
    PRE_PROCESSOR,
    /** 后置处理器 — 在节点主逻辑执行后运行，用于结果增强、日志记录等 */
    POST_PROCESSOR,
    /** 参数绑定器 — 在节点执行前运行，负责入参格式转换、数据组装 */
    PARAM_BINDER,
    /** 参数校验器 — 在参数绑定后运行，负责必填检查、业务规则校验 */
    PARAM_VALIDATOR

}
