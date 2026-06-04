package com.zestflow.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private String paymentId;
    private String status;
    private int amount;
    private String method;
    private String approvalResult;
    private int retryCount;
    private boolean fallbackUsed;
    private boolean circuitBreakerOpen;
    private String errorMessage;
    private long costMs;
    private List<String> nodeSequence;
}
