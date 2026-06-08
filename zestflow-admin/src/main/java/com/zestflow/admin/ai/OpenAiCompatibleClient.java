package com.zestflow.admin.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * OpenAI 兼容 HTTP 客户端 — POST {baseUrl}/v1/chat/completions（支持 stream）
 */
@Slf4j
@Component
public class OpenAiCompatibleClient implements AiChatClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String chat(List<ChatMessage> messages, AiChatOptions options) {
        return chatStream(messages, options, null);
    }

    @Override
    public String chatStream(List<ChatMessage> messages, AiChatOptions options, StreamHandlers handlers) {
        if (options == null || !StringUtils.hasText(options.baseUrl())) {
            throw new BizException(ErrorCode.AI_CONFIG_INVALID);
        }
        boolean stream = handlers != null || options.stream();
        if (stream) {
            return postStream(messages, options, handlers);
        }
        return postBlocking(messages, options);
    }

    private String postBlocking(List<ChatMessage> messages, AiChatOptions options) {
        String url = normalizeUrl(options.baseUrl()) + "/chat/completions";
        RestTemplate restTemplate = createRestTemplate(options.timeoutMs());
        try {
            String response = restTemplate.postForObject(
                    url, new HttpEntity<>(buildBody(messages, options, false).toString(), buildHeaders(options)),
                    String.class);
            return extractContent(response);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("LLM 请求失败 url={} model={}", url, options.model(), e);
            throw new BizException(ErrorCode.AI_LLM_REQUEST_FAILED);
        }
    }

    private String postStream(List<ChatMessage> messages, AiChatOptions options, StreamHandlers handlers) {
        String urlStr = normalizeUrl(options.baseUrl()) + "/chat/completions";
        int timeout = Math.max(options.timeoutMs(), 5_000);
        StringBuilder reasoning = new StringBuilder();
        StringBuilder content = new StringBuilder();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "text/event-stream");
            if (StringUtils.hasText(options.apiKey())) {
                conn.setRequestProperty("Authorization", "Bearer " + options.apiKey());
            }
            byte[] bodyBytes = buildBody(messages, options, true).toString().getBytes(StandardCharsets.UTF_8);
            conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
            }
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new BizException(ErrorCode.AI_LLM_REQUEST_FAILED, "HTTP " + code);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    parseStreamChunk(data, reasoning, content, handlers);
                }
            }
            if (content.isEmpty() && reasoning.isEmpty()) {
                throw new BizException(ErrorCode.AI_LLM_EMPTY_RESPONSE);
            }
            return content.toString();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("LLM 流式请求失败 url={} model={}", urlStr, options.model(), e);
            throw new BizException(ErrorCode.AI_LLM_REQUEST_FAILED);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private void parseStreamChunk(String data, StringBuilder reasoning, StringBuilder content,
                                  StreamHandlers handlers) throws Exception {
        JsonNode root = MAPPER.readTree(data);
        JsonNode error = root.get("error");
        if (error != null && !error.isNull()) {
            String msg = error.has("message") ? error.get("message").asText() : error.toString();
            throw new BizException(ErrorCode.AI_LLM_REQUEST_FAILED, msg);
        }
        JsonNode delta = root.path("choices").path(0).path("delta");
        if (delta.has("reasoning_content") && !delta.get("reasoning_content").isNull()) {
            String piece = delta.get("reasoning_content").asText();
            reasoning.append(piece);
            if (handlers != null) {
                handlers.onReasoningDelta(piece);
            }
        }
        if (delta.has("content") && !delta.get("content").isNull()) {
            String piece = delta.get("content").asText();
            content.append(piece);
            if (handlers != null) {
                handlers.onContentDelta(piece);
            }
        }
    }

    private ObjectNode buildBody(List<ChatMessage> messages, AiChatOptions options, boolean stream) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", options.model());
        body.put("temperature", options.temperature());
        body.put("max_tokens", options.maxTokens());
        body.put("stream", stream);
        ArrayNode msgArray = body.putArray("messages");
        for (ChatMessage msg : messages) {
            ObjectNode m = msgArray.addObject();
            m.put("role", msg.role());
            m.put("content", msg.content());
        }
        if (options.jsonMode()) {
            ObjectNode responseFormat = body.putObject("response_format");
            responseFormat.put("type", "json_object");
        }
        return body;
    }

    private static HttpHeaders buildHeaders(AiChatOptions options) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(options.apiKey())) {
            headers.setBearerAuth(options.apiKey());
        }
        return headers;
    }

    static String normalizeUrl(String baseUrl) {
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.endsWith("/v1")) {
            return trimmed;
        }
        return trimmed + "/v1";
    }

    static String extractContent(String response) {
        if (!StringUtils.hasText(response)) {
            throw new BizException(ErrorCode.AI_LLM_EMPTY_RESPONSE);
        }
        try {
            JsonNode root = MAPPER.readTree(response);
            JsonNode error = root.get("error");
            if (error != null && !error.isNull()) {
                String msg = error.has("message") ? error.get("message").asText() : error.toString();
                throw new BizException(ErrorCode.AI_LLM_REQUEST_FAILED, msg);
            }
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                throw new BizException(ErrorCode.AI_LLM_EMPTY_RESPONSE);
            }
            JsonNode message = choices.get(0).get("message");
            if (message == null) {
                throw new BizException(ErrorCode.AI_LLM_EMPTY_RESPONSE);
            }
            String reasoning = message.has("reasoning_content") && !message.get("reasoning_content").isNull()
                    ? message.get("reasoning_content").asText() : null;
            String content = message.has("content") && !message.get("content").isNull()
                    ? message.get("content").asText() : "";
            if (!StringUtils.hasText(content) && !StringUtils.hasText(reasoning)) {
                throw new BizException(ErrorCode.AI_LLM_EMPTY_RESPONSE);
            }
            return StringUtils.hasText(content) ? content : reasoning;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.AI_LLM_EMPTY_RESPONSE);
        }
    }

    private static RestTemplate createRestTemplate(int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(timeoutMs, 5_000));
        factory.setReadTimeout(Math.max(timeoutMs, 5_000));
        return new RestTemplate(factory);
    }
}
