package com.zestflow.admin.ai.model.dto;

import lombok.Data;

@Data
public class AiDeliveryValidateRequest {
    private String appCode;
    private String chainCode;
    private String chainData;
    private String graphData;
    /** 可选：本地业务工程根目录，提供时执行完整 validate_delivery 扫描 */
    private String projectRoot;
    private Boolean strictMode;
}
