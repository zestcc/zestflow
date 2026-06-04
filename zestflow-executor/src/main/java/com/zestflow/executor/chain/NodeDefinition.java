package com.zestflow.executor.chain;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.model.dto.ComponentRef;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 运行时节点定义（由 graph_data JSON 解析而来）
 */
@Data
@Builder
@AllArgsConstructor
public class NodeDefinition {

    /** 节点唯一标识（对应 graph_data.nodes[].id） */
    private String id;

    /** 节点显示名称 */
    private String label;

    /** 节点类型：NORMAL / CONDITION / SCRIPT / SUB_CHAIN / ITERATOR */
    private String type;

    /** 映射的 @ZestComponent ID */
    private String component;

    /** 绑定元件名称 */
    private String componentName;

    /** 元件分组 */
    private String groupName;

    /** 描述 */
    private String description;

    /** 参数解析器链（按顺序匹配，第一个 supports 的生效） */
    private List<ComponentRef> paramResolvers;

    /** 参数校验器 */
    private ComponentRef paramValidator;

    /** 前置处理器列表 */
    private List<ComponentRef> preComponents;

    /** 后置处理器列表 */
    private List<ComponentRef> postComponents;

    /** 脚本内容（SCRIPT 类型） */
    private String script;

    /** 子链编码（SUB_CHAIN 类型） */
    private String subChainCode;

    /** 超时时间（毫秒） */
    @Builder.Default
    private long timeout = ChainConstants.DEFAULT_NODE_TIMEOUT_MS;

    /** 重试次数 */
    @Builder.Default
    private int retryCount = ChainConstants.DEFAULT_RETRY_COUNT;

    /** 重试间隔（毫秒） */
    @Builder.Default
    private long retryInterval = ChainConstants.DEFAULT_RETRY_INTERVAL_MS;

    /** 重试退避因子 */
    @Builder.Default
    private double retryBackoff = 1.0;

    /** 触发重试的错误码列表 */
    @Builder.Default
    private Set<String> retryFor = new HashSet<>();

    /** 补偿元件 ID（COMPENSATE 策略失败时逆序调用；空则尝试 {component}Compensate） */
    private String compensateComponent;

    /** 降级组件 ID */
    private String fallbackComponent;

    /** 降级触发条件（TIMEOUT / ALL_RETRY_FAILED 等） */
    @Builder.Default
    private Set<String> fallbackOn = new HashSet<>();

    /** 是否启用熔断器 */
    @Builder.Default
    private boolean circuitBreakerEnabled = false;

    /** 熔断失败阈值 */
    @Builder.Default
    private int circuitBreakerThreshold = ChainConstants.DEFAULT_CIRCUIT_BREAKER_THRESHOLD;

    /** 熔断恢复时间（毫秒） */
    @Builder.Default
    private long circuitBreakerRecoveryMs = ChainConstants.DEFAULT_CIRCUIT_BREAKER_RECOVERY_MS;

    /** 是否异步执行 */
    @Builder.Default
    private boolean async = false;

    /** 额外配置 */
    @Builder.Default
    private Map<String, Object> config = new HashMap<>();

    /** 迭代数据源表达式（ITERATOR 类型） */
    private String iteratorDataSource;

    /** 迭代项名称（ITERATOR 类型） */
    private String iteratorItemName;

    /** 迭代子节点（ITERATOR 类型） */
    private java.util.List<NodeDefinition> iteratorSubNodes;

    /** 条件表达式（CONDITION 类型），空则始终执行 */
    private String condition;

    /** 判断模式：bind=绑定元件 / script=内联脚本（随设计持久化） */
    @Builder.Default
    private String predicateMode = "bind";

    /** 内联脚本表达式（script 模式），Aviator 语法 */
    private String predicateScript;

    /** script 模式 True 分支标签（匹配出线 label） */
    @Builder.Default
    private String trueLabel = "True";

    /** script 模式 False 分支标签（匹配出线 label） */
    @Builder.Default
    private String falseLabel = "False";

    public boolean isNormal() {
        return ChainConstants.NODE_TYPE_NORMAL.equals(type);
    }

    public boolean isCondition() {
        return ChainConstants.NODE_TYPE_CONDITION.equals(type);
    }

    public boolean isScript() {
        return ChainConstants.NODE_TYPE_SCRIPT.equals(type);
    }

    public boolean isSubChain() {
        return ChainConstants.NODE_TYPE_SUB_CHAIN.equals(type);
    }

    public boolean isIterator() {
        return ChainConstants.NODE_TYPE_ITERATOR.equals(type);
    }

    /** 是否内联脚本判断（不依赖 Executor 元件注册表） */
    public boolean isInlineScriptPredicate() {
        return "script".equalsIgnoreCase(predicateMode);
    }
}
