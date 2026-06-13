package com.zestflow.admin.service.log;

/**
 * 执行轨迹流式推送回调 — SSE / WebSocket 共用。
 */
public interface ExecutionTraceStreamCallback {

    void send(String eventName, Object payload) throws Exception;

    void complete();

    void completeWithError(Exception error);
}
