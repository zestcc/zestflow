package com.zestflow.collector.spi;

import com.zestflow.common.protocol.InvocationPayloadDTO;

/**
 * 外部调用载荷 SPI — 试验场/API 触发 request/response 存 app_log
 */
public interface InvocationPayloadService {

    void save(InvocationPayloadDTO dto);

    InvocationPayloadDTO getByInvocationId(String invocationId);
}
