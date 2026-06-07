package com.zestflow.admin.model.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class DictDataTreeVO {

    private Long id;
    /** el-tree node-key：真实项为 id，虚拟分组为 group:parentValue */
    private String nodeKey;
    /** 跨类型级联时的虚拟分组节点（不可编辑/删除） */
    private Boolean virtualNode;

    private String typeCode;
    private Long parentId;
    private String parentTypeCode;
    private String parentValue;
    private String label;
    private String value;
    private Integer sort;
    private Integer status;
    private String tagType;
    private Integer defaultFlag;
    private String remark;
    private String extra;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder.Default
    private List<DictDataTreeVO> children = new ArrayList<>();
}
