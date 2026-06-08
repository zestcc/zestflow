package com.zestflow.executor.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * OpenAI 兼容 HTTP 客户端（Chat + Embedding），供 Executor 独立 suggest/RAG，不依赖 Admin。
 */
@Slf4j
public class ExecutorOpenAiClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String chat(List<ChatMessage> messages, ExecutorAiProperties props) {
        if (!props.llmReady()) {
            throw new ExecutorAiException("LLM 未配置：请设置 zestflow.executor.ai.llm-enabled=true 与 base-url/model");
        }
        String url = normalizeUrl(props.getBaseUrl()) + "/chat/completions";
        RestTemplate restTemplate = createRestTemplate(props.getTimeoutMs());

        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", props.getModel());
        body.put("temperature", props.getTemperature());
        body.put("max_tokens", props.getMaxTokens());
        ArrayNode msgArray = body.putArray("messages");
        for (ChatMessage msg : messages) {
            ObjectNode m = msgArray.addObject();
            m.put("role", msg.role());
            m.put("content", msg.content());
        }
        ObjectNode responseFormat = body.putObject("response_format");
        responseFormat.put("type", "json_object");

        HttpHeaders headers = buildHeaders(props);
        try {
            String response = restTemplate.postForObject(url, new HttpEntity<>(body.toString(), headers), String.class);
            return extractChatContent(response);
        } catch (ExecutorAiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Executor LLM 请求失败 url={} model={}", url, props.getModel(), e);
            throw new ExecutorAiException("LLM 请求失败: " + e.getMessage(), e);
        }
    }

    public float[] embed(String text, String model, ExecutorAiProperties props) {
        List<float[]> batch = embedBatch(List.of(text), model, props);
        return batch.isEmpty() ? new float[0] : batch.get(0);
    }

    public List<float[]> embedBatch(List<String> texts, String model, ExecutorAiProperties props) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        if (!props.llmReady()) {
            throw new ExecutorAiException("Embedding 需要 LLM base-url 配置");
        }
        String url = normalizeUrl(props.getBaseUrl()) + "/embeddings";
        RestTemplate restTemplate = createRestTemplate(props.getTimeoutMs());

        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        ArrayNode input = body.putArray("input");
        texts.forEach(input::add);

        HttpHeaders headers = buildHeaders(props);
        try {
            String response = restTemplate.postForObject(url, new HttpEntity<>(body.toString(), headers), String.class);
            return parseEmbeddings(response, texts.size());
        } catch (ExecutorAiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Executor Embedding 请求失败 url={} model={}", url, model, e);
            throw new ExecutorAiException("Embedding 请求失败: " + e.getMessage(), e);
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

    private static HttpHeaders buildHeaders(ExecutorAiProperties props) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(props.getApiKey())) {
            headers.setBearerAuth(props.getApiKey());
        }
        return headers;
    }

    private static String extractChatContent(String response) {
        if (!StringUtils.hasText(response)) {
            throw new ExecutorAiException("LLM 空响应");
        }
        try {
            JsonNode root = MAPPER.readTree(response);
            JsonNode error = root.get("error");
            if (error != null && !error.isNull()) {
                throw new ExecutorAiException("LLM 错误: " + error.path("message").asText(error.toString()));
            }
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                throw new ExecutorAiException("LLM 空 choices");
            }
            return choices.get(0).path("message").path("content").asText("");
        } catch (ExecutorAiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExecutorAiException("LLM 响应解析失败", e);
        }
    }

    static List<float[]> parseEmbeddings(String response, int expectedSize) {
        if (!StringUtils.hasText(response)) {
            throw new ExecutorAiException("Embedding 空响应");
        }
        try {
            JsonNode root = MAPPER.readTree(response);
            JsonNode data = root.get("data");
            if (data == null || !data.isArray() || data.isEmpty()) {
                throw new ExecutorAiException("Embedding 空 data");
            }
            List<JsonNode> sorted = new ArrayList<>();
            data.forEach(sorted::add);
            sorted.sort(Comparator.comparingInt(n -> n.path("index").asInt(0)));

            List<float[]> out = new ArrayList<>(sorted.size());
            for (JsonNode item : sorted) {
                JsonNode embedding = item.get("embedding");
                if (embedding == null || !embedding.isArray()) {
                    throw new ExecutorAiException("Embedding 格式错误");
                }
                float[] vector = new float[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) {
                    vector[i] = (float) embedding.get(i).asDouble();
                }
                out.add(vector);
            }
            if (expectedSize > 0 && out.size() != expectedSize) {
                throw new ExecutorAiException("Embedding 数量不匹配");
            }
            return out;
        } catch (ExecutorAiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExecutorAiException("Embedding 解析失败", e);
        }
    }

    private static RestTemplate createRestTemplate(int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int t = Math.max(timeoutMs, 5_000);
        factory.setConnectTimeout(t);
        factory.setReadTimeout(t);
        return new RestTemplate(factory);
    }

    public record ChatMessage(String role, String content) {
    }
}
