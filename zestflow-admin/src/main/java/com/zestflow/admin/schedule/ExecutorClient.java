package com.zestflow.admin.schedule;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.common.model.dto.ChainExecuteRequestDTO;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;

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
     * 向指定执行器发送链执行请求
     *
     * @param host      执行器 host
     * @param port      执行器 port
     * @param chainCode 链编码
     * @param params    执行参数
     * @return 执行结果
     */
    public ChainExecuteResultDTO execute(String host, int port, String chainCode, Map<String, Object> params) {
        String url = protocol + "://" + host + ":" + port + "/execute";

        ChainExecuteRequestDTO request = new ChainExecuteRequestDTO();
        request.setChainCode(chainCode);
        request.setParams(params);

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
                        host, port, chainCode, response.statusCode());
                ChainExecuteResultDTO error = new ChainExecuteResultDTO();
                error.setChainCode(chainCode);
                error.setStatus(2);
                error.setErrorMessage("执行器返回状态码: " + response.statusCode());
                return error;
            }

            return objectMapper.readValue(response.body(), ChainExecuteResultDTO.class);

        } catch (JsonProcessingException e) {
            log.error("序列化执行请求失败 chainCode={}", chainCode, e);
            ChainExecuteResultDTO error = new ChainExecuteResultDTO();
            error.setChainCode(chainCode);
            error.setStatus(2);
            error.setErrorMessage("请求序列化失败: " + e.getMessage());
            return error;
        } catch (Exception e) {
            log.error("调用执行器失败 host={}:{} chainCode={}", host, port, chainCode, e);
            ChainExecuteResultDTO error = new ChainExecuteResultDTO();
            error.setChainCode(chainCode);
            error.setStatus(2);
            error.setErrorMessage("调用执行器失败: " + e.getMessage());
            return error;
        }
    }
}
