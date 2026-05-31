package com.zestflow.executor.chain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 链版本快照 PO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChainVersionPO {
    private Long id;
    private String chainCode;
    private Integer version;
    private String designCode;
    private String graphData;
    private String chainData;
    private Long tenantId;
    private String appCode;
    private String createdBy;
    private String createdAt;
}
