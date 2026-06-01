package com.zestflow.admin.playground.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 演示场景定义 PO
 */
@Data
@TableName("playground_scene")
public class PlaygroundScenePO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 场景编码 */
    private String sceneCode;

    /** 场景名称 */
    private String name;

    /** 场景描述 */
    private String description;

    /** 请求路径 */
    private String requestPath;

    /** 请求方法 */
    private String requestMethod;

    /** 默认请求头 JSON */
    private String requestHeaders;

    /** 请求体类型 */
    private String bodyType;

    /** 请求体模板 */
    private String requestBody;

    /** 响应示例 JSON */
    private String responseExample;

    /** 关联链编码 */
    private String chainCode;

    /** 每 IP 每分钟限流数 */
    private Integer rateLimit;

    /** 租户ID */
    private Long tenantId;

    /** 应用编码 */
    private String appCode;

    @TableField(fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
