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
public class OrderResponse {

    private String orderId;
    private String status;
    private int totalAmount;
    private String payStatus;
    private String stockStatus;
    private List<String> nodeResults;
    private String errorMessage;
    private long costMs;
    private String channel;
    private Map<String, Object> resultData;

    /** 全链路结果明细 */
    private FullLifecycleDetail fullDetail;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FullLifecycleDetail {
        private String orderId;
        private String payResult;
        private String verifyResult;
        private String stockResult;
        private String deductResult;
        private String notifyResult;
        private String splitResult;
    }
}
