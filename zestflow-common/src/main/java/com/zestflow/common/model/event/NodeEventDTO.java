package com.zestflow.common.model.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 节点事件 DTO（供 Collector 消费/落库）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeEventDTO {

    /** 事件类型 */
    private ChainEventType eventType;

    /** 执行实例 ID */
    private String instanceId;

    /** 链编码 */
    private String chainCode;

    /** 节点 ID */
    private String nodeId;

    /** 节点名称 */
    private String nodeLabel;

    /** 执行器标识 */
    private String executorId;

    /** 事件时间戳 */
    private Long timestamp;

    /** 事件附带数据 */
    private Map<String, Object> data;
}
