package com.zestflow.common.protocol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 链级 Spring 事务配置 — 来自 chainData.config.transaction（设计器编排时定义）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChainTransactionConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 节点传播策略：继承链级设置 */
    public static final String PROPAGATION_INHERIT = "INHERIT";

    /** 默认传播策略 */
    public static final String DEFAULT_PROPAGATION = "REQUIRED";

    /** 是否启用链级本地事务 */
    private boolean enabled;

    /** Spring 传播策略名，如 REQUIRED / REQUIRES_NEW */
    @Builder.Default
    private String propagation = DEFAULT_PROPAGATION;

    /** 触发回滚的异常类名（可选，默认 RuntimeException + Error） */
    private List<String> rollbackFor;

    public static ChainTransactionConfig disabled() {
        return ChainTransactionConfig.builder().enabled(false).build();
    }

    @SuppressWarnings("unchecked")
    public static ChainTransactionConfig fromExtraConfig(Map<String, Object> extraConfig) {
        if (extraConfig == null || extraConfig.isEmpty()) {
            return disabled();
        }
        Object tx = extraConfig.get("transaction");
        if (!(tx instanceof Map<?, ?> raw)) {
            return disabled();
        }
        ChainTransactionConfigBuilder builder = ChainTransactionConfig.builder();
        Object enabled = raw.get("enabled");
        if (enabled instanceof Boolean b) {
            builder.enabled(b);
        } else if (enabled != null) {
            builder.enabled(Boolean.parseBoolean(String.valueOf(enabled)));
        }
        Object propagation = raw.get("propagation");
        if (propagation != null && !String.valueOf(propagation).isBlank()) {
            builder.propagation(String.valueOf(propagation).trim().toUpperCase());
        }
        Object rollbackFor = raw.get("rollbackFor");
        if (rollbackFor instanceof List<?> list) {
            builder.rollbackFor(list.stream().map(String::valueOf).toList());
        }
        ChainTransactionConfig cfg = builder.build();
        if (!cfg.isEnabled()) {
            return disabled();
        }
        return cfg;
    }

    /** 解析节点级传播策略；空或 INHERIT 表示继承链级 */
    public static String resolveNodePropagation(String nodePropagation, ChainTransactionConfig chainConfig) {
        if (nodePropagation != null && !nodePropagation.isBlank()
                && !PROPAGATION_INHERIT.equalsIgnoreCase(nodePropagation.trim())) {
            return nodePropagation.trim().toUpperCase();
        }
        if (chainConfig != null && chainConfig.isEnabled()) {
            String chainProp = chainConfig.getPropagation();
            return chainProp != null && !chainProp.isBlank() ? chainProp : DEFAULT_PROPAGATION;
        }
        return null;
    }

    /** 节点是否需要独立 TransactionTemplate 包装（非继承链级、或显式 REQUIRES_NEW 等） */
    public static boolean requiresDedicatedTemplate(String nodePropagation, ChainTransactionConfig chainConfig) {
        if (nodePropagation != null && !nodePropagation.isBlank()
                && !PROPAGATION_INHERIT.equalsIgnoreCase(nodePropagation.trim())) {
            return true;
        }
        return false;
    }
}
