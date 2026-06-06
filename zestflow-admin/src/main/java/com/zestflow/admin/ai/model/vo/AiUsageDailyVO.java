package com.zestflow.admin.ai.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiUsageDailyVO {

    private String date;
    private long sessions;
    private long successSessions;
    private long tokenEstimate;
}
