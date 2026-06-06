package com.zestflow.admin.ai.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI Copilot 消息审计 PO
 */
@Data
@TableName("zf_ai_copilot_message")
public class AiCopilotMessagePO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long tenantId;

    /** user|assistant|system */
    private String role;

    /** 内容摘要 */
    private String contentSummary;

    private Integer tokenEstimate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
