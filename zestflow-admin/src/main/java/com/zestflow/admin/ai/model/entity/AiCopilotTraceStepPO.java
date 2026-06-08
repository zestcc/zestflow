package com.zestflow.admin.ai.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI Copilot Trace 步骤 PO
 */
@Data
@TableName("zf_ai_copilot_trace_step")
public class AiCopilotTraceStepPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long sessionId;

    private Long jobId;

    /** RAG|LLM|QUALITY|VALIDATE|REPAIR|DONE */
    private String stepType;

    private String stepName;

    /** RUNNING|OK|FAIL */
    private String status;

    private Integer latencyMs;

    private Integer tokenEstimate;

    private String detailJson;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
