package com.zestflow.executor.controller;

import com.zestflow.common.model.dto.ChainExecuteRequestDTO;
import com.zestflow.executor.http.ChainExecuteFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mode 1 统一执行端点 — {@code POST /execute} / {@code POST /api/execute}
 * <p>
 * 成功时响应体 = 链终态 PARSER 返回值；失败按 {@code execute-failure-policy} 处理，默认 PROPAGATE 抛异常。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ExecutionController {

    private final ChainExecuteFacade chainExecuteFacade;

    @PostMapping({"/execute", "/api/execute"})
    public ResponseEntity<?> execute(@RequestBody(required = false) ChainExecuteRequestDTO request,
                                     @RequestParam(value = "chainCode", required = false) String chainCodeParam) {
        ChainExecuteRequestDTO effective = request != null ? request : ChainExecuteRequestDTO.builder().build();
        if ((effective.getChainCode() == null || effective.getChainCode().isBlank())
                && chainCodeParam != null && !chainCodeParam.isBlank()) {
            effective.setChainCode(chainCodeParam);
        }
        log.info("统一执行端点请求 chainCode={}", effective.getChainCode());
        return chainExecuteFacade.executeHttp(effective);
    }
}
