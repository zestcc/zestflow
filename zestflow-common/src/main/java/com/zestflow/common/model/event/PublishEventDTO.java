package com.zestflow.common.model.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 发布事件 DTO — 链部署/热切换的事件载体
 * <p>
 * Admin 发起发布时填充此事件，发送给所有在线 Executor；
 * Executor 处理后返回，Admin 聚合结果。
 * 事件数据通过 ChainEventDTO 的 data map 透传落地到 Collector。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishEventDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 发布会话 ID（UUID，同一批发布共用） */
    private String publishId;

    /** 发布事件类型 */
    private ChainEventType eventType;

    /** 链编码 */
    private String chainCode;

    /** 模块 ID */
    private Long moduleId;

    /** 执行器地址（Executor 端填充） */
    private String executorUrl;

    /** 设计图谱 JSON（Admin 推送时携带） */
    private String graphData;

    /** 总执行器数（Admin 填充） */
    private Integer totalExecutors;

    /** 已完成执行器数（Admin 聚合） */
    private Integer completedExecutors;

    /** 执行结果（成功/失败） */
    private Boolean success;

    /** 错误消息 */
    private String errorMessage;

    /** 节点数（加载成功后填充） */
    private Integer nodeCount;

    /** 事件时间戳 */
    private Long timestamp;
}
