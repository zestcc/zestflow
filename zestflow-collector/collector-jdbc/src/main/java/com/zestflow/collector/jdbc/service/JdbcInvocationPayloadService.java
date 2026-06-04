package com.zestflow.collector.jdbc.service;

import com.zestflow.collector.jdbc.entity.InvocationPayloadPO;
import com.zestflow.collector.jdbc.mapper.InvocationPayloadMapper;
import com.zestflow.collector.spi.InvocationPayloadService;
import com.zestflow.common.protocol.InvocationPayloadDTO;
import lombok.RequiredArgsConstructor;

/**
 * JDBC 外部调用载荷服务
 */
@RequiredArgsConstructor
public class JdbcInvocationPayloadService implements InvocationPayloadService {

    private final InvocationPayloadMapper invocationPayloadMapper;

    @Override
    public void save(InvocationPayloadDTO dto) {
        if (dto == null || dto.getInvocationId() == null || dto.getInvocationId().isBlank()) {
            return;
        }
        invocationPayloadMapper.upsert(toPO(dto));
    }

    @Override
    public InvocationPayloadDTO getByInvocationId(String invocationId) {
        if (invocationId == null || invocationId.isBlank()) {
            return null;
        }
        InvocationPayloadPO po = invocationPayloadMapper.selectByInvocationId(invocationId);
        return po != null ? toDTO(po) : null;
    }

    private static InvocationPayloadPO toPO(InvocationPayloadDTO dto) {
        return InvocationPayloadPO.builder()
                .invocationId(dto.getInvocationId())
                .sourceType(dto.getSourceType())
                .executionId(dto.getExecutionId())
                .sceneCode(dto.getSceneCode())
                .requestBody(dto.getRequestBody())
                .responseBody(dto.getResponseBody())
                .requestHeaders(dto.getRequestHeaders())
                .tenantId(dto.getTenantId())
                .appCode(dto.getAppCode())
                .build();
    }

    private static InvocationPayloadDTO toDTO(InvocationPayloadPO po) {
        return InvocationPayloadDTO.builder()
                .invocationId(po.getInvocationId())
                .sourceType(po.getSourceType())
                .executionId(po.getExecutionId())
                .sceneCode(po.getSceneCode())
                .requestBody(po.getRequestBody())
                .responseBody(po.getResponseBody())
                .requestHeaders(po.getRequestHeaders())
                .tenantId(po.getTenantId())
                .appCode(po.getAppCode())
                .build();
    }
}
