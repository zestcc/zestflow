package com.zestflow.common.protocol;

import com.zestflow.common.model.dto.ChainEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 执行轨迹 — 一次链执行的所有事件 + 摘要信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionTrace implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 执行追踪 ID */
    private String executionId;

    /** 链编码 */
    private String chainName;

    /** 执行器 ID */
    private String executorId;

    /** 应用编码 */
    private String appCode;

    /** 应用名 */
    private String appName;

    /** 开始时间（毫秒） */
    private long startTime;

    /** 结束时间（毫秒） */
    private Long endTime;

    /** 耗时（毫秒） */
    private Long costMs;

    /** 最终状态（1=成功, 0=失败） */
    private Integer status;

    /** 事件总数 */
    private int eventCount;

    /** 节点数 */
    private int nodeCount;

    /** 成功节点数 */
    private int successCount;

    /** 失败节点数 */
    private int failedCount;

    /** 错误消息 */
    private String errorMessage;

    /** 所有事件（按时间升序） */
    private List<ChainEvent> events;
}
