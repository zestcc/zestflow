package com.zestflow.admin.ai.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI Copilot 会话审计 PO
 */
@Data
@TableName("zf_ai_copilot_session")
public class AiCopilotSessionPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long userId;

    private String appCode;

    private String designId;

    private String chainCode;

    /** explain|suggest|fix-errors|expression|diagnose|scaffold */
    private String mode;

    /** 1采纳 0拒绝 */
    private Integer adopted;

    /** LLM 调用耗时 ms */
    private Integer latencyMs;

    /** 1成功 0失败 */
    private Integer success;

    /** 失败摘要 */
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
