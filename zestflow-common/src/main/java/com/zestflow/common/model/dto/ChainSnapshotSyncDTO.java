package com.zestflow.common.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin → Collector 同步链图数据快照 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChainSnapshotSyncDTO {

    /** 链编码 */
    private String chainCode;

    /** 图数据 JSON */
    private String graphData;

    /** 应用编码 */
    private String appCode;

    /** 操作人 */
    private String createdBy;
}
