package com.zestflow.collector.jdbc.service;

import com.zestflow.collector.jdbc.entity.ExecutionPayloadPO;
import com.zestflow.collector.jdbc.mapper.ExecutionPayloadMapper;
import com.zestflow.collector.spi.InvocationPayloadService;
import com.zestflow.common.protocol.InvocationPayloadDTO;
import lombok.RequiredArgsConstructor;

/**
 * JDBC 外部调用载荷服务 — 写入 execution_payload（ref_type=INVOCATION）
 */
@RequiredArgsConstructor
public class JdbcInvocationPayloadService implements InvocationPayloadService {

    private final ExecutionPayloadMapper executionPayloadMapper;

    @Override
    public void save(InvocationPayloadDTO dto) {
        if (dto == null || dto.getInvocationId() == null || dto.getInvocationId().isBlank()) {
            return;
        }
        executionPayloadMapper.upsert(toPO(dto));
    }

    @Override
    public InvocationPayloadDTO getByInvocationId(String invocationId) {
        if (invocationId == null || invocationId.isBlank()) {
            return null;
        }
        ExecutionPayloadPO po = executionPayloadMapper.selectByRefId(invocationId);
        return po != null ? toDTO(po) : null;
    }

    private static ExecutionPayloadPO toPO(InvocationPayloadDTO dto) {
        return ExecutionPayloadPO.builder()
                .refId(dto.getInvocationId())
                .refType(ExecutionPayloadPO.REF_INVOCATION)
                .sourceType(dto.getSourceType())
                .executionId(dto.getExecutionId())
                .sceneCode(dto.getSceneCode())
                .params(dto.getRequestBody())
                .result(dto.getResponseBody())
                .extra(dto.getRequestHeaders())
                .tenantId(dto.getTenantId())
                .appCode(dto.getAppCode())
                .build();
    }

    private static InvocationPayloadDTO toDTO(ExecutionPayloadPO po) {
        return InvocationPayloadDTO.builder()
                .invocationId(po.getRefId())
                .sourceType(po.getSourceType())
                .executionId(po.getExecutionId())
                .sceneCode(po.getSceneCode())
                .requestBody(po.getParams())
                .responseBody(po.getResult())
                .requestHeaders(po.getExtra())
                .tenantId(po.getTenantId())
                .appCode(po.getAppCode())
                .build();
    }
}
