package com.zestflow.test.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowResponse {

    private String workflowId;
    private String scenario;
    private String status;
    private int nodeCount;
    private List<String> nodeSequence;
    private Map<String, Object> resultData;
    private double avgCostMs;
    private String errorMessage;
    private long costMs;
    private int successCount;
    private int failedCount;
}
