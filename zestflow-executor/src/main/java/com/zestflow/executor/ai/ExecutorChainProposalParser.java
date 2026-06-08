package com.zestflow.executor.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

/**
 * LLM 回复 → chainData 解析。
 */
public final class ExecutorChainProposalParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ExecutorChainProposalParser() {
    }

    public static ParsedProposal parse(String llmReply) {
        if (!StringUtils.hasText(llmReply)) {
            throw new ExecutorAiException("LLM 空响应");
        }
        String json = stripMarkdownJson(llmReply);
        try {
            JsonNode root = MAPPER.readTree(json);
            String chainData;
            String summary;
            if (root.has("chainData")) {
                JsonNode cd = root.get("chainData");
                chainData = cd.isTextual() ? cd.asText() : cd.toString();
                summary = root.has("summary") ? root.get("summary").asText() : "";
            } else {
                chainData = root.toString();
                summary = "";
            }
            return new ParsedProposal(chainData, summary);
        } catch (Exception e) {
            return new ParsedProposal(json, "");
        }
    }

    static String stripMarkdownJson(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) {
                return trimmed.substring(start + 1, end).trim();
            }
        }
        return trimmed;
    }

    public record ParsedProposal(String chainData, String summary) {
    }
}
