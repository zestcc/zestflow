package com.zestflow.executor.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executor 侧 LLM chains/suggest：RAG 注入 → LLM 生成 → 质量门禁 → validate 修复 → pattern 回落。
 */
@Slf4j
public class ExecutorChainSuggester {

    private static final Pattern CHAIN_JSON_BLOCK =
            Pattern.compile("```json\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private final ExecutorAiProperties aiProps;
    private final ExecutorOpenAiClient openAiClient;

    public ExecutorChainSuggester(ExecutorAiProperties aiProps, ExecutorOpenAiClient openAiClient) {
        this.aiProps = aiProps;
        this.openAiClient = openAiClient;
    }

    public Map<String, Object> suggest(String userMessage,
                                       String chainCode,
                                       List<String> allowedComponents,
                                       List<ExecutorRagChunk> ragChunks,
                                       ChainDataValidator validator) {
        String q = userMessage != null ? userMessage : "";
        List<String> ragTexts = ragChunks.stream().map(ExecutorRagChunk::text).toList();

        if (aiProps.llmReady() && openAiClient != null) {
            try {
                return suggestWithLlm(q, chainCode, allowedComponents, ragTexts, validator, ragChunks);
            } catch (ExecutorAiException e) {
                log.warn("Executor LLM suggest 失败，尝试 pattern 回落: {}", e.getMessage());
                if (!aiProps.isPatternFallbackEnabled()) {
                    throw e;
                }
            }
        }

        return suggestFromPattern(q, chainCode, allowedComponents, ragChunks, validator);
    }

    private Map<String, Object> suggestWithLlm(String userMessage,
                                               String chainCode,
                                               List<String> allowedComponents,
                                               List<String> ragTexts,
                                               ChainDataValidator validator,
                                               List<ExecutorRagChunk> ragChunks) {
        String system = ExecutorSuggestPromptBuilder.buildSystemPrompt(allowedComponents);
        String user = ExecutorSuggestPromptBuilder.buildUserPrompt(userMessage, ragTexts);

        String llmReply = openAiClient.chat(List.of(
                new ExecutorOpenAiClient.ChatMessage("system", system),
                new ExecutorOpenAiClient.ChatMessage("user", user)), aiProps);

        ExecutorChainProposalParser.ParsedProposal proposal = ExecutorChainProposalParser.parse(llmReply);
        int qualityRetries = 0;
        while (qualityRetries < aiProps.getRepairMaxRounds()) {
            ExecutorChainQualityGate.QualityResult quality =
                    ExecutorChainQualityGate.assess(userMessage, proposal.chainData());
            if (quality.accepted()) {
                break;
            }
            qualityRetries++;
            String retryUser = ExecutorSuggestPromptBuilder.buildQualityRetryPrompt(
                    userMessage, proposal.chainData(), quality.critique());
            llmReply = openAiClient.chat(List.of(
                    new ExecutorOpenAiClient.ChatMessage("system", system),
                    new ExecutorOpenAiClient.ChatMessage("user", retryUser)), aiProps);
            proposal = ExecutorChainProposalParser.parse(llmReply);
        }

        Map<String, Object> validation = validateProposal(chainCode, proposal.chainData(), validator);
        int repairRounds = 0;
        while (!Boolean.TRUE.equals(validation.get("valid")) && repairRounds < aiProps.getRepairMaxRounds()) {
            repairRounds++;
            @SuppressWarnings("unchecked")
            List<String> errors = (List<String>) validation.getOrDefault("errors", List.of());
            String fixUser = ExecutorSuggestPromptBuilder.buildFixErrorsPrompt(
                    userMessage, proposal.chainData(), errors);
            llmReply = openAiClient.chat(List.of(
                    new ExecutorOpenAiClient.ChatMessage("system", system),
                    new ExecutorOpenAiClient.ChatMessage("user", fixUser)), aiProps);
            proposal = ExecutorChainProposalParser.parse(llmReply);
            validation = validateProposal(chainCode, proposal.chainData(), validator);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", StringUtils.hasText(proposal.summary())
                ? proposal.summary() : "Executor LLM 生成链草稿，请校验后采纳。");
        out.put("source", "executor-llm");
        out.put("proposedChainData", proposal.chainData());
        out.put("validation", validation);
        out.put("ragSnippetCount", ragChunks.size());
        out.put("qualityRetries", qualityRetries);
        out.put("repairRounds", repairRounds);
        if (allowedComponents != null && !allowedComponents.isEmpty()) {
            out.put("allowedComponents", allowedComponents);
        }
        return out;
    }

    private Map<String, Object> suggestFromPattern(String userMessage,
                                                   String chainCode,
                                                   List<String> allowedComponents,
                                                   List<ExecutorRagChunk> chunks,
                                                   ChainDataValidator validator) {
        String proposed = null;
        String source = "executor-rag-empty";
        String summary = "未找到可复用的应用端 pattern，请补充学习事件或启用 LLM suggest。";

        for (ExecutorRagChunk chunk : chunks) {
            Optional<String> json = extractChainJson(chunk.text());
            if (json.isPresent()) {
                proposed = json.get();
                source = "executor-pattern:" + chunk.id();
                summary = "基于应用端蒸馏 pattern（" + chunk.id() + "）生成链草稿，请校验后采纳。";
                break;
            }
        }

        if (proposed == null && !chunks.isEmpty()) {
            summary = "检索到 " + chunks.size() + " 条 RAG 片段但无链 JSON；摘要："
                    + truncate(chunks.get(0).text().replace('\n', ' '), 200);
            source = "executor-rag-hints";
        }

        Map<String, Object> validation = validateProposal(chainCode, proposed, validator);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", summary);
        out.put("source", source);
        out.put("proposedChainData", proposed);
        out.put("validation", validation);
        out.put("ragSnippetCount", chunks.size());
        if (allowedComponents != null && !allowedComponents.isEmpty()) {
            out.put("allowedComponents", allowedComponents);
        }
        return out;
    }

    private Map<String, Object> validateProposal(String chainCode, String proposed, ChainDataValidator validator) {
        Map<String, Object> validation = new LinkedHashMap<>();
        validation.put("valid", false);
        validation.put("errors", List.of());
        if (proposed != null && validator != null) {
            String code = chainCode != null && !chainCode.isBlank() ? chainCode : "draft-suggest";
            boolean ok = validator.isValid(code, proposed);
            validation.put("valid", ok);
            if (!ok) {
                validation.put("errors", List.of("应用端 validate-definition 未通过"));
            }
        }
        return validation;
    }

    private Optional<String> extractChainJson(String markdown) {
        if (markdown == null) {
            return Optional.empty();
        }
        Matcher m = CHAIN_JSON_BLOCK.matcher(markdown);
        while (m.find()) {
            String block = m.group(1).trim();
            if (block.contains("\"nodes\"") || block.contains("nodes")) {
                try {
                    var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(block);
                    if (node.has("nodes") || node.has("chainData")) {
                        if (node.has("chainData")) {
                            return Optional.of(node.get("chainData").toString());
                        }
                        return Optional.of(node.toString());
                    }
                } catch (Exception ignored) {
                    // try next
                }
            }
        }
        return Optional.empty();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
