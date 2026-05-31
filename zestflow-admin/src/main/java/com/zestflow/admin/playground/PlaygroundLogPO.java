package com.zestflow.admin.playground;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 试验场执行记录
 */
@Data
@TableName("playground_log")
public class PlaygroundLogPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 场景标识 */
    private String sceneId;

    /** 场景名称 */
    private String sceneName;

    /** 链编码 */
    private String chainCode;

    /** 请求IP（仅入库，不展示） */
    private String requestIp;

    /** 自定义请求头 JSON */
    private String requestHeaders;

    /** 请求参数 JSON */
    private String params;

    /** 执行结果 JSON */
    private String result;

    /** 链执行实例 ID */
    private String instanceId;

    /** 状态：0-失败 1-成功 */
    private Integer status;

    /** 耗时（毫秒） */
    private Long costMs;

    /** 错误信息 */
    private String errorMsg;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
