package com.zestflow.executor.chain;

import lombok.Builder;
import lombok.Value;

/**
 * 应用声明的链契约元数据（来自 {@code @ZestChain} 扫描）。
 */
@Value
@Builder
public class ChainDeclarationMeta {

    String chainKey;
    String name;
    String description;
    String declaringClass;
    String declaringMethod;
}
