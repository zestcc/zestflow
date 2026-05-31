package com.zestflow.admin.model.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class DictTypeVO {

    private Long id;
    private String code;
    private String name;
    private String description;
    private Integer status;
    private Integer sort;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 该类型下的字典数据列表（仅 getByCode 返回时填充） */
    private List<DictDataVO> dataList;
}
