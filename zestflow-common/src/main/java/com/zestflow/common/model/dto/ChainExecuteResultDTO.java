package com.zestflow.common.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Executor 向 Admin 回传的执行结果 DTO
 * <p>
 * 同时支持 Map 形式的结果数据和类型化结果提取。
 * <pre>{@code
 * ChainExecuteResultDTO result = engine.execute("order-flow", orderData);
 * // 通过类型直接获取业务结果
 * OrderInfo output = result.getData(OrderInfo.class);
 * // 或通过 Map 键值访问
 * Object val = result.getResultData().get("orderId");
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChainExecuteResultDTO {

    /** 执行实例 ID */
    private String instanceId;

    /** 链编码 */
    private String chainCode;

    /** 执行状态码（对应 ChainConstants 链状态） */
    private Integer status;

    /** 执行耗时（毫秒） */
    private Long costMs;

    /** 执行结果数据（DataBus 快照） */
    private Map<String, Object> resultData;

    /** 节点执行结果明细 */
    private List<NodeResultDTO> nodeResults;

    /** 错误信息 */
    private String errorMessage;

    /** 类型化结果数据（按 Class 索引） */
    private Map<Class<?>, Object> resultTypedData;

    /**
     * 按类型获取结果数据。
     * 优先从类型化结果中匹配，兜底遍历 DataBus 快照按类型匹配。
     */
    @SuppressWarnings("unchecked")
    public <T> T getData(Class<T> type) {
        // 优先从类型化结果中匹配
        if (resultTypedData != null) {
            Object val = resultTypedData.get(type);
            if (val != null) return (T) val;
            // 超类/接口兜底
            for (Map.Entry<Class<?>, Object> entry : resultTypedData.entrySet()) {
                if (type.isAssignableFrom(entry.getKey())) {
                    return (T) entry.getValue();
                }
            }
        }
        // 兜底：遍历 DataBus 快照
        if (resultData != null) {
            for (Object val : resultData.values()) {
                if (type.isInstance(val)) {
                    return (T) val;
                }
            }
        }
        return null;
    }
}
