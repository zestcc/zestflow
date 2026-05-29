package com.zestflow.common.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 完整链定义 DTO（对应 graph_data JSON 结构）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChainDefinitionDTO {

    /** 链编码 */
    private String code;

    /** 版本号 */
    private Integer version;

    /** 节点列表 */
    private List<ChainNodeDTO> nodes;

    /** 连线列表 */
    private List<ChainEdgeDTO> edges;

    /** 链级别配置 */
    private Map<String, Object> config;
}
