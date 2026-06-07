package com.zestflow.common.alert;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/** 单条待发送 SLA 告警候选 */
@Data
@Builder
public class SlaAlertCandidate {

    private AlertRule rule;
    private String summary;
    private Map<String, String> metrics;
}
