package com.zestflow.admin.ai.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户 AI 配置 PO
 */
@Data
@TableName("zf_ai_tenant_config")
public class AiTenantConfigPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 是否启用 Copilot */
    private Boolean enabled;

    /** 提供商预设 ID */
    private String preset;

    /** 覆盖 baseUrl */
    private String baseUrl;

    /** 加密 API Key */
    private String apiKeyEnc;

    /** 覆盖模型名 */
    private String model;

    /** 允许的预设 JSON 数组 */
    private String allowedPresets;

    /** 月 Token 估算上限，null=不限 */
    private Integer monthlyTokenQuota;

    @TableField(fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic(value = "0", delval = "1")
    @TableField("is_deleted")
    private Integer isDeleted;
}
