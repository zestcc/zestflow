package com.zestflow.admin.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AlertScanResultVO {

    private boolean success;
    private String summary;
    private String errorMessage;
    private Long costMs;
}
