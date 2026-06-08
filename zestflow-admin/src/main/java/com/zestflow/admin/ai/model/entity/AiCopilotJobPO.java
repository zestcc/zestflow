package com.zestflow.admin.ai.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI Copilot 异步任务 PO
 */
@Data
@TableName("zf_ai_copilot_job")
public class AiCopilotJobPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long userId;

    private Long sessionId;

    /** suggest|explain */
    private String jobType;

    /** PENDING|RUNNING|DONE|FAILED|CANCELLED */
    private String status;

    private String requestJson;

    private String resultJson;

    private String progressStep;

    private String reasoningBuffer;

    private String errorMessage;

    private Integer latencyMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private LocalDateTime finishedAt;
}
