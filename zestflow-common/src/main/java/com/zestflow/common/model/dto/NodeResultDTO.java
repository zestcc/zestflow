package com.zestflow.common.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 单节点执行结果 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeResultDTO {

    /** 节点 ID */
    private String nodeId;

    /** 节点状态码 */
    private Integer status;

    /** 执行耗时（毫秒） */
    private Long costMs;

    /** 输出数据（写入 DataBus 给下游节点） */
    private Map<String, Object> outputData;

    /** 元件原始返回值（用于链终态结果展示） */
    private Object returnValue;

    /** 错误信息 */
    private String errorMessage;

    /** 业务错误码（BizException 等） */
    private String errorCode;

    /** 重试次数 */
    private Integer retryCount;
}
