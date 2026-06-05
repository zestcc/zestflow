package com.zestflow.executor.http;

import com.zestflow.common.exception.ChainExecutionException;
import com.zestflow.common.model.dto.ChainExecuteRequestDTO;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * Mode 3 编程式网关 — 业务 Controller 一行调用，失败抛 {@link ChainExecutionException} 保证事务回滚。
 */
@RequiredArgsConstructor
public class ChainGateway {

    private final ChainExecuteFacade facade;

    public ChainExecuteResultDTO execute(String chainCode, Map<String, Object> params) {
        ChainExecuteRequestDTO request = ChainExecuteRequestDTO.builder()
                .chainCode(chainCode)
                .params(params)
                .build();
        return executeOrThrow(request);
    }

    public ChainExecuteResultDTO executeByKey(String chainKey, Map<String, Object> params) {
        ChainExecuteRequestDTO request = ChainExecuteRequestDTO.builder()
                .chainKey(chainKey)
                .params(params)
                .build();
        return executeOrThrow(request);
    }

    public ChainExecuteResultDTO executeOrThrow(ChainExecuteRequestDTO request) {
        return executeOrThrow(request, new Object[0]);
    }

    public ChainExecuteResultDTO executeOrThrow(ChainExecuteRequestDTO request, Object... typedArgs) {
        ChainExecuteResultDTO result = facade.executeCore(request, typedArgs);
        if (!result.isSuccess()) {
            throw new ChainExecutionException(result);
        }
        return result;
    }

    public ChainExecuteResultDTO executeOrThrow(String chainCode, Object... typedArgs) {
        return executeOrThrow(ChainExecuteRequestDTO.builder().chainCode(chainCode).build(), typedArgs);
    }

    public ChainExecuteResultDTO executeOrThrow(String chainCode, Map<String, Object> params, Object... typedArgs) {
        return executeOrThrow(ChainExecuteRequestDTO.builder()
                .chainCode(chainCode)
                .params(params)
                .build(), typedArgs);
    }

    public ChainExecuteResultDTO executeByKeyOrThrow(String chainKey, Map<String, Object> params) {
        return executeOrThrow(ChainExecuteRequestDTO.builder()
                .chainKey(chainKey)
                .params(params)
                .build());
    }

    public Object getReturnValue(String chainCode, Map<String, Object> params) {
        return executeOrThrow(ChainExecuteRequestDTO.builder()
                .chainCode(chainCode)
                .params(params)
                .build()).getReturnValue();
    }

    @SuppressWarnings("unchecked")
    public <T> T getReturnValue(String chainCode, Map<String, Object> params, Class<T> type) {
        ChainExecuteResultDTO result = executeOrThrow(ChainExecuteRequestDTO.builder()
                .chainCode(chainCode)
                .params(params)
                .build());
        T val = result.getReturnValue(type);
        if (val != null) {
            return val;
        }
        return result.getData(type);
    }
}
