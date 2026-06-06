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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
public class OpenAiCompatibleEmbeddingClient implements AiEmbeddingClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public float[] embed(String text, AiChatClient.AiChatOptions options) {
        List<float[]> batch = embedBatch(List.of(text), options);
        return batch.isEmpty() ? new float[0] : batch.get(0);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts, AiChatClient.AiChatOptions options) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        if (options == null || !StringUtils.hasText(options.baseUrl())) {
            throw new BizException(ErrorCode.AI_CONFIG_INVALID);
        }
        String url = OpenAiCompatibleClient.normalizeUrl(options.baseUrl()) + "/embeddings";
        RestTemplate restTemplate = createRestTemplate(options.timeoutMs());

        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", options.model());
        ArrayNode input = body.putArray("input");
        texts.forEach(input::add);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(options.apiKey())) {
            headers.setBearerAuth(options.apiKey());
        }

        try {
            String response = restTemplate.postForObject(url, new HttpEntity<>(body.toString(), headers), String.class);
            return parseEmbeddings(response, texts.size());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Embedding 请求失败 url={} model={}", url, options.model(), e);
            throw new BizException(ErrorCode.AI_LLM_REQUEST_FAILED);
        }
    }

    static List<float[]> parseEmbeddings(String response, int expectedSize) {
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
            JsonNode data = root.get("data");
            if (data == null || !data.isArray() || data.isEmpty()) {
                throw new BizException(ErrorCode.AI_LLM_EMPTY_RESPONSE);
            }
            List<JsonNode> sorted = new ArrayList<>();
            data.forEach(sorted::add);
            sorted.sort(Comparator.comparingInt(n -> n.path("index").asInt(0)));

            List<float[]> out = new ArrayList<>(sorted.size());
            for (JsonNode item : sorted) {
                JsonNode embedding = item.get("embedding");
                if (embedding == null || !embedding.isArray()) {
                    throw new BizException(ErrorCode.AI_LLM_EMPTY_RESPONSE);
                }
                float[] vector = new float[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) {
                    vector[i] = (float) embedding.get(i).asDouble();
                }
                out.add(vector);
            }
            if (expectedSize > 0 && out.size() != expectedSize) {
                throw new BizException(ErrorCode.AI_LLM_EMPTY_RESPONSE);
            }
            return out;
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
