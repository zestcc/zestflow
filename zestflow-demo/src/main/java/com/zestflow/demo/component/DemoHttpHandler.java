package com.zestflow.demo.component;

import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestErrorHandler;
import com.zestflow.executor.annotation.ZestFailure;
import com.zestflow.executor.annotation.ZestParser;
import com.zestflow.executor.annotation.ZestResult;
import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP 三 Mode 演示元件 — PARSER 终态响应 + ERROR_HANDLER 失败兜底。
 */
@Slf4j
@ZestComponent("demoHttp")
public class DemoHttpHandler {

    @ZestParser(value = "parseOrderCreateResponse", name = "HTTP订单创建响应")
    public Map<String, Object> parseOrderCreateResponse(@ZestResult Object upstream, ChainContext ctx) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("mode", "PARSER");
        body.put("orderId", ctx.get("orderId"));
        Object parsed = ctx.get("parsedOrderId");
        if (parsed != null) {
            body.put("parsedOrderId", parsed);
        }
        if (upstream != null) {
            body.put("upstreamType", upstream.getClass().getSimpleName());
        }
        return body;
    }

    @ZestParser(value = "parseAfterSaleResponse", name = "HTTP售后响应")
    public Map<String, Object> parseAfterSaleResponse(@ZestResult Object upstream, ChainContext ctx) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("mode", "PARSER");
        body.put("applyId", ctx.get("applyId"));
        if (upstream != null) {
            body.put("result", upstream.toString());
        }
        return body;
    }

    @ZestErrorHandler(value = "demoErrorHandler", name = "演示错误处理")
    public Map<String, Object> demoErrorHandler(@ZestFailure ChainExecuteResultDTO failure, ChainContext ctx) {
        log.warn("demoErrorHandler 处理链失败 chainCode={} error={}",
                failure != null ? failure.getChainCode() : null,
                failure != null ? failure.getErrorMessage() : null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("handledBy", "demoErrorHandler");
        if (failure != null) {
            body.put("chainCode", failure.getChainCode());
            body.put("errorMessage", failure.getErrorMessage());
            body.put("failedNodeId", failure.getFailedNodeId());
        }
        return body;
    }
}
