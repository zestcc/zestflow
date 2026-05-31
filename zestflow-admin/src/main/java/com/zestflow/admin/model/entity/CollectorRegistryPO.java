package com.zestflow.admin.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 采集器注册表 PO
 */
@Data
@TableName("collector_registry")
public class CollectorRegistryPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 采集器唯一标识 */
    private String collectorId;

    /** 应用编码（分组标识） */
    @TableField("app_code")
    private String appCode;

    /** 应用名 */
    private String appName;

    /** 采集器 Host */
    private String collectorHost;

    /** 采集器 Port */
    private Integer collectorPort;

    /** 状态：1-在线 0-离线 2-异常离线 */
    private Integer status;

    /** 最后心跳时间 */
    private LocalDateTime lastHeartbeat;

    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
