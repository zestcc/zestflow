package com.zestflow.collector.jdbc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 链事件载荷 PO — 对应 chain_event_payload 表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chain_event_payload")
public class ChainEventPayloadPO {

    private String eventId;
    private String params;
    private String result;
    private String errorMessage;
}
