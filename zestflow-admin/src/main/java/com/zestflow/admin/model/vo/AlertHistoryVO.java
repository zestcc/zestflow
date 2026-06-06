package com.zestflow.admin.model.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class AlertHistoryVO {

    private Long id;
    private String appCode;
    private String ruleCode;
    private String ruleLabel;
    private String summary;
    private Map<String, String> metrics;
    private Integer recipientCount;
    private String recipients;
    private Boolean mailSent;
    private LocalDateTime sentAt;
}
