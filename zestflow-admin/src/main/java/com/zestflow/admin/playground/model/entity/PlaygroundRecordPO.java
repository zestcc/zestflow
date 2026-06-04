package com.zestflow.admin.playground.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 演示执行记录 PO
 */
@Data
@TableName("playground_record")
public class PlaygroundRecordPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联场景ID */
    private Long sceneId;

    /** 场景名称（冗余） */
    private String sceneName;

    /** 场景编码（冗余） */
    private String sceneCode;

    /** 请求方法 */
    private String requestMethod;

    /** 请求路径 */
    private String requestPath;

    /** 请求头 JSON */
    private String requestHeaders;

    /** 请求体类型 */
    private String bodyType;

    /** 调用载荷 ID（request/response 存 app_log） */
    private String invocationId;

    /** HTTP 响应状态码 */
    private Integer responseStatus;

    /** 响应头 JSON */
    private String responseHeaders;

    /** 关联链编码 */
    private String chainCode;

    /** 链执行实例 ID */
    private String instanceId;

    /** 执行状态：0-失败 1-成功 */
    private Integer status;

    /** 耗时（毫秒） */
    private Long costMs;

    /** 错误信息 */
    private String errorMsg;

    /** 请求IP（仅入库，API 不返回） */
    private String requestIp;

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
