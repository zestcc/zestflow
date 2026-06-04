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

    /** 执行追踪 ID（同一次链执行的所有事件共享） */
    private String executionId;

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

    /** 应用编码 */
    private String appCode;

    /** 应用名 */
    private String appName;

    /** 租户 ID */
    private Long tenantId;

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

    /** 事件数（GROUP BY 查询用，非 DB 字段） */
    @TableField(exist = false)
    private Integer eventCount;

    /** 节点数（GROUP BY 查询用，非 DB 字段） */
    @TableField(exist = false)
    private Integer nodeCount;

    /** 成功节点数（GROUP BY 查询用，非 DB 字段） */
    @TableField(exist = false)
    private Integer successCount;

    /** 失败节点数（GROUP BY 查询用，非 DB 字段） */
    @TableField(exist = false)
    private Integer failedCount;
}
