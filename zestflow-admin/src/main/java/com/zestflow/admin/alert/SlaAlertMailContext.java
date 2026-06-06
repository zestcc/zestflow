package com.zestflow.admin.alert;

import lombok.Builder;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/** SLA 告警邮件上下文 */
@Data
@Builder
public class SlaAlertMailContext {

    private Long tenantId;
    private String appCode;
    private String ruleLabel;
    private String summary;
    @Builder.Default
    private Map<String, String> metrics = new LinkedHashMap<>();
    private int windowMinutes;
    private String subjectPrefix;
}
