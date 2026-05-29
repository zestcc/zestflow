package com.zestflow.collector.jdbc.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 链执行事件 PO — 对应 chain_event 表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chain_event")
public class ChainEventPO {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 事件全局唯一 ID */
    private String eventId;

    /** 事件类型 */
    private String eventType;

    /** 链实例 ID */
    private String chainId;

    /** 链名称 */
    private String chainName;

    /** 节点实例 ID */
    private String nodeId;

    /** 节点名称 */
    private String nodeName;

    /** 执行器 ID */
    private String executorId;

    /** 应用名 */
    private String appName;

    /** 执行入参 JSON */
    private String params;

    /** 执行结果 JSON */
    private String result;

    /** 错误消息 */
    private String errorMessage;

    /** 执行耗时（毫秒） */
    private Long costMs;

    /** 节点状态（0=失败, 1=成功） */
    private Integer status;

    /** 事件发生时间戳 */
    private Long timestamp;

    /** 扩展元数据 JSON */
    private String metadata;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
