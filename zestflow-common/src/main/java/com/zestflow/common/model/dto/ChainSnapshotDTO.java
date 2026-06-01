package com.zestflow.common.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 链图数据快照查询结果 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChainSnapshotDTO {

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

    /** 创建时间 */
    private String createdAt;
}
