package com.zestflow.admin.playground;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 试验场执行记录 VO（返回前端，不含 IP）
 */
@Data
public class PlaygroundLogVO {
    private Long id;
    private String sceneId;
    private String sceneName;
    private String chainCode;
    private Map<String, String> requestHeaders;
    private Map<String, Object> params;
    private Map<String, Object> result;
    private String instanceId;
    private Integer status;
    private Long costMs;
    private String errorMsg;
    private LocalDateTime createdAt;
}
