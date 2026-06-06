package com.zestflow.admin.ai.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("zf_ai_rag_document")
public class AiRagDocumentPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String title;
    private String appCode;
    private String content;
    private Integer enabled;
    private Integer sortOrder;
    private String sourceType;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer isDeleted;
}
