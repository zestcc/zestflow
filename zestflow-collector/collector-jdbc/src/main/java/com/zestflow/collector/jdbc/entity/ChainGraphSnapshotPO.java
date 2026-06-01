package com.zestflow.collector.jdbc.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 链图数据快照 PO — 对应 chain_graph_snapshot 表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chain_graph_snapshot")
public class ChainGraphSnapshotPO {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 链编码 */
    private String chainCode;

    /** 版本号 */
    private Integer version;

    /** 图数据 JSON */
    private String graphData;

    /** 状态：1-生效 0-已废弃 */
    private Integer status;

    /** 租户ID */
    private Long tenantId;

    /** 应用编码 */
    private String appCode;

    /** 创建人 */
    private String createdBy;

    /** 最后更新人 */
    private String updatedBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 删除标记 */
    @TableLogic
    private Integer isDeleted;
}
