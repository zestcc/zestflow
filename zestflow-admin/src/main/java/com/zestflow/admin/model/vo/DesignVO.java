package com.zestflow.admin.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DesignVO {

    private Long id;
    private String code;
    private String name;
    private Long moduleId;
    private Integer status;
    private String description;
    private String graphData;
    private String designer;
    private String updatedBy;
    private Integer chainCount;
    private String boundChainCodes;
    private List<ChainVO> boundChains;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
