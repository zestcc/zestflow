package com.zestflow.common.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 链定义中的连线（边）DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChainEdgeDTO {

    /** 源节点 ID */
    private String source;

    /** 目标节点 ID */
    private String target;

    /** 连线名称 */
    private String label;

    /** 条件表达式（为空则始终执行），如 "${result.status} == 'PASS'" */
    private String condition;
}
