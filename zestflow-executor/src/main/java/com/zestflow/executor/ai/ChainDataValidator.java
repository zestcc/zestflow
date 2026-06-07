package com.zestflow.executor.ai;

/**
 * 链定义 JSON 校验（蒸馏晋升前、suggest 后可选调用）。
 */
@FunctionalInterface
public interface ChainDataValidator {

    /**
     * @return true 表示 chainData 可通过运行时 validate-definition
     */
    boolean isValid(String chainCode, String chainDataJson);
}
