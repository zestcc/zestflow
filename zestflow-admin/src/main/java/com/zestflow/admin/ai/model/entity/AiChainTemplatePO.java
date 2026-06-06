package com.zestflow.admin.ai.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("zf_ai_chain_template")
public class AiChainTemplatePO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String name;
    private String description;
    private String appCode;
    private String promptSummary;
    private String chainData;
    private String tags;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer isDeleted;
}
