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

import java.util.List;

/**
 * OpenAI 兼容 HTTP 客户端 — POST {baseUrl}/v1/chat/completions
 */
@Slf4j
@Component
public class OpenAiCompatibleClient implements AiChatClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String chat(List<ChatMessage> messages, AiChatOptions options) {
        if (options == null || !StringUtils.hasText(options.baseUrl())) {
            throw new BizException(ErrorCode.AI_CONFIG_INVALID);
        }
        String url = normalizeUrl(options.baseUrl()) + "/chat/completions";
        RestTemplate restTemplate = createRestTemplate(options.timeoutMs());

        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", options.model());
        body.put("temperature", options.temperature());
        body.put("max_tokens", options.maxTokens());
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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(options.apiKey())) {
            headers.setBearerAuth(options.apiKey());
        }

        try {
            String response = restTemplate.postForObject(url, new HttpEntity<>(body.toString(), headers), String.class);
            return extractContent(response);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("LLM 请求失败 url={} model={}", url, options.model(), e);
            throw new BizException(ErrorCode.AI_LLM_REQUEST_FAILED);
        }
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
            if (message == null || !message.has("content")) {
                throw new BizException(ErrorCode.AI_LLM_EMPTY_RESPONSE);
            }
            return message.get("content").asText();
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
