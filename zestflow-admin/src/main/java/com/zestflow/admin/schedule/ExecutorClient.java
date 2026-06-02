package com.zestflow.admin.schedule;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.common.model.dto.ChainExecuteRequestDTO;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Admin → Executor HTTP 客户端
 * <p>
 * 向执行器的 Netty HTTP 服务发送链执行请求。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutorClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** 服务间通信协议（http/https） */
    @Value("${zestflow.admin.protocol:http}")
    private String protocol;

    /** Admin → Executor Netty 鉴权（与 zestflow.executor.access-token 一致） */
    @Value("${zestflow.admin.executor-access-token:}")
    private String executorAccessToken;

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /**
     * 向指定执行器发送链执行请求（无幂等键）。
     */
    public ChainExecuteResultDTO execute(String host, int port, String chainCode, Map<String, Object> params) {
        ChainExecuteRequestDTO request = ChainExecuteRequestDTO.builder()
                .chainCode(chainCode)
                .params(params)
                .source("admin-schedule")
                .build();
        return execute(host, port, request);
    }

    /**
     * 向指定执行器发送链执行请求（含幂等键 / traceId）。
     */
    public ChainExecuteResultDTO execute(String host, int port, ChainExecuteRequestDTO request) {
        String url = protocol + "://" + host + ":" + port + "/execute";

        if (request.getSource() == null || request.getSource().isBlank()) {
            request.setSource("admin");
        }

        try {
            String json = objectMapper.writeValueAsString(request);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json");
            if (executorAccessToken != null && !executorAccessToken.isEmpty()) {
                builder.header("X-Access-Token", executorAccessToken);
            }
            HttpRequest httpRequest = builder
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("执行器返回非200状态码 host={}:{} chainCode={} status={}",
                        host, port, request.getChainCode(), response.statusCode());
                ChainExecuteResultDTO error = new ChainExecuteResultDTO();
                error.setChainCode(request.getChainCode());
                error.setStatus(2);
                error.setErrorMessage("执行器返回状态码: " + response.statusCode());
                return error;
            }

            return objectMapper.readValue(response.body(), ChainExecuteResultDTO.class);

        } catch (JsonProcessingException e) {
            log.error("序列化执行请求失败 chainCode={}", request.getChainCode(), e);
            ChainExecuteResultDTO error = new ChainExecuteResultDTO();
            error.setChainCode(request.getChainCode());
            error.setStatus(2);
            error.setErrorMessage("请求序列化失败: " + e.getMessage());
            return error;
        } catch (Exception e) {
            log.error("调用执行器失败 host={}:{} chainCode={}", host, port, request.getChainCode(), e);
            ChainExecuteResultDTO error = new ChainExecuteResultDTO();
            error.setChainCode(request.getChainCode());
            error.setStatus(2);
            error.setErrorMessage("调用执行器失败: " + e.getMessage());
            return error;
        }
    }
}
