package com.zestflow.admin.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chain_snapshot")
public class ChainSnapshotPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("chain_id")
    private Long chainId;

    private Integer version;

    @TableField("graph_data")
    private String graphData;

    @TableField("change_log")
    private String changeLog;

    @TableField("published_by")
    private String publishedBy;

    private Long tenantId;

    @TableField("app_code")
    private String appCode;

    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
