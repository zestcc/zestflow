package com.zestflow.admin.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.admin.ai.model.entity.AiCopilotMessagePO;
import com.zestflow.admin.ai.model.entity.AiCopilotSessionPO;
import com.zestflow.admin.ai.model.vo.AiCopilotMessageVO;
import com.zestflow.admin.ai.model.vo.AiValidationVO;
import com.zestflow.admin.ai.repository.AiCopilotMessageMapper;
import com.zestflow.admin.ai.repository.AiCopilotSessionMapper;
import com.zestflow.admin.config.AiPlatformConfig;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.util.SecurityUtils;
import com.zestflow.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Copilot 会话与消息持久化
 */
@Component
@RequiredArgsConstructor
public class AiCopilotSessionSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("\\d{17}[\\dXx]");
    private static final int SUMMARY_MAX_LEN = 2000;

    private final AiPlatformConfig aiPlatformConfig;
    private final AiCopilotSessionMapper sessionMapper;
    private final AiCopilotMessageMapper messageMapper;

    AiCopilotSessionPO findLatestSession(Long tenantId, Long userId,
                                         String appCode, String designId, String chainCode) {
        if (!StringUtils.hasText(designId)) {
            return null;
        }
        LambdaQueryWrapper<AiCopilotSessionPO> q = baseSessionQuery(tenantId, userId, appCode, designId, chainCode)
                .ge(AiCopilotSessionPO::getCreatedAt, LocalDateTime.now().minusDays(7))
                .orderByDesc(AiCopilotSessionPO::getCreatedAt)
                .last("LIMIT 1");
        return sessionMapper.selectOne(q);
    }

    List<AiCopilotSessionPO> listSessions(Long tenantId, Long userId,
                                          String appCode, String designId, String chainCode, int limit) {
        if (!StringUtils.hasText(designId)) {
            return List.of();
        }
        int max = Math.min(Math.max(limit, 1), 50);
        LambdaQueryWrapper<AiCopilotSessionPO> q = baseSessionQuery(tenantId, userId, appCode, designId, chainCode)
                .orderByDesc(AiCopilotSessionPO::getCreatedAt)
                .last("LIMIT " + max);
        return sessionMapper.selectList(q);
    }

    AiCopilotSessionPO requireSession(Long sessionId, Long tenantId, Long userId) {
        AiCopilotSessionPO session = sessionMapper.selectById(sessionId);
        if (session == null || !tenantId.equals(session.getTenantId())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (userId != null && userId > 0 && !userId.equals(session.getUserId())) {
            throw new BizException(ErrorCode.PERMISSION_DENIED);
        }
        if (session.getArchived() != null && session.getArchived() == 1) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return session;
    }

    Long createSession(Long tenantId, Long userId, String appCode, String designId,
                       String chainCode, String mode, String title) {
        AiCopilotSessionPO session = new AiCopilotSessionPO();
        session.setTenantId(tenantId);
        session.setUserId(userId);
        session.setAppCode(appCode);
        session.setDesignId(designId);
        session.setChainCode(chainCode);
        session.setMode(StringUtils.hasText(mode) ? mode : "suggest");
        session.setTitle(StringUtils.hasText(title) ? truncateTitle(title) : null);
        session.setArchived(0);
        sessionMapper.insert(session);
        return session.getId();
    }

    void updateTitle(Long sessionId, String title) {
        if (!StringUtils.hasText(title)) {
            return;
        }
        AiCopilotSessionPO update = new AiCopilotSessionPO();
        update.setId(sessionId);
        update.setTitle(truncateTitle(title));
        sessionMapper.updateById(update);
    }

    void archiveSession(Long sessionId) {
        AiCopilotSessionPO update = new AiCopilotSessionPO();
        update.setId(sessionId);
        update.setArchived(1);
        sessionMapper.updateById(update);
    }

    void maybeSetTitleFromUserMessage(Long sessionId, String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return;
        }
        AiCopilotSessionPO session = sessionMapper.selectById(sessionId);
        if (session == null || StringUtils.hasText(session.getTitle())) {
            return;
        }
        updateTitle(sessionId, userMessage.trim());
    }

    private LambdaQueryWrapper<AiCopilotSessionPO> baseSessionQuery(Long tenantId, Long userId,
                                                                      String appCode, String designId,
                                                                      String chainCode) {
        LambdaQueryWrapper<AiCopilotSessionPO> q = new LambdaQueryWrapper<AiCopilotSessionPO>()
                .eq(AiCopilotSessionPO::getTenantId, tenantId)
                .eq(AiCopilotSessionPO::getUserId, userId)
                .eq(AiCopilotSessionPO::getDesignId, designId.trim())
                .and(w -> w.isNull(AiCopilotSessionPO::getArchived).or().eq(AiCopilotSessionPO::getArchived, 0));
        if (StringUtils.hasText(appCode)) {
            q.eq(AiCopilotSessionPO::getAppCode, appCode.trim());
        }
        if (StringUtils.hasText(chainCode)) {
            q.eq(AiCopilotSessionPO::getChainCode, chainCode.trim());
        }
        return q;
    }

    Long resolveOrCreateSession(Long tenantId, String appCode, String designId,
                                String chainCode, String mode, Long existingSessionId) {
        if (existingSessionId != null) {
            AiCopilotSessionPO existing = sessionMapper.selectById(existingSessionId);
            if (existing != null && tenantId.equals(existing.getTenantId())
                    && matchesSessionContext(existing, appCode, designId, chainCode)) {
                return existingSessionId;
            }
        }
        return recordSession(tenantId, appCode, designId, chainCode, mode);
    }

    void savePendingProposal(Long sessionId, String chainData, String summary, AiValidationVO validation) {
        AiCopilotSessionPO update = new AiCopilotSessionPO();
        update.setId(sessionId);
        update.setPendingChainData(chainData);
        update.setPendingSummary(summary);
        try {
            update.setPendingValidationJson(MAPPER.writeValueAsString(validation));
        } catch (Exception e) {
            update.setPendingValidationJson(null);
        }
        sessionMapper.updateById(update);
    }

    AiValidationVO readPendingValidation(AiCopilotSessionPO session) {
        if (session == null || !StringUtils.hasText(session.getPendingValidationJson())) {
            return null;
        }
        try {
            return MAPPER.readValue(session.getPendingValidationJson(), AiValidationVO.class);
        } catch (Exception e) {
            return null;
        }
    }

    void clearPendingProposal(Long sessionId) {
        AiCopilotSessionPO update = new AiCopilotSessionPO();
        update.setId(sessionId);
        update.setPendingChainData(null);
        update.setPendingSummary(null);
        update.setPendingValidationJson(null);
        sessionMapper.updateById(update);
    }

    void finalizeSession(Long sessionId, long startMs, boolean success, String errorMessage, String model) {
        if (sessionId == null) {
            return;
        }
        AiCopilotSessionPO update = new AiCopilotSessionPO();
        update.setId(sessionId);
        update.setLatencyMs((int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - startMs));
        update.setSuccess(success ? 1 : 0);
        update.setErrorMessage(truncate(errorMessage));
        if (StringUtils.hasText(model)) {
            update.setLastModel(model);
        }
        sessionMapper.updateById(update);
    }

    Long recordSession(Long tenantId, String appCode, String designId, String chainCode, String mode) {
        AiCopilotSessionPO session = new AiCopilotSessionPO();
        session.setTenantId(tenantId);
        session.setUserId(currentUserId());
        session.setAppCode(appCode);
        session.setDesignId(designId);
        session.setChainCode(chainCode);
        session.setMode(mode);
        session.setArchived(0);
        sessionMapper.insert(session);
        return session.getId();
    }

    void recordMessage(Long sessionId, Long tenantId, String role, String summary) {
        AiCopilotMessagePO msg = new AiCopilotMessagePO();
        msg.setSessionId(sessionId);
        msg.setTenantId(tenantId);
        msg.setRole(role);
        msg.setContentSummary(summary);
        msg.setTokenEstimate(estimateTokens(summary));
        messageMapper.insert(msg);
    }

    AiCopilotMessageVO toMessageVo(AiCopilotMessagePO po) {
        AiCopilotService.ParsedAssistantContent parsed = "assistant".equals(po.getRole())
                ? AiCopilotService.parseAssistantRecord(po.getContentSummary())
                : new AiCopilotService.ParsedAssistantContent(null, po.getContentSummary());
        return AiCopilotMessageVO.builder()
                .id(po.getId())
                .role(po.getRole())
                .content(parsed.body())
                .reasoning(parsed.reasoning())
                .createdAt(po.getCreatedAt())
                .build();
    }

    AiCopilotService.ParsedChainProposal parseChainProposal(String llmReply) {
        if (!StringUtils.hasText(llmReply)) {
            throw new BizException(ErrorCode.AI_LLM_EMPTY_RESPONSE);
        }
        String json = AiCopilotService.stripMarkdownJson(llmReply);
        try {
            JsonNode root = MAPPER.readTree(json);
            String chainData;
            String summary;
            String reasoning = null;
            if (root.has("chainData")) {
                JsonNode cd = root.get("chainData");
                chainData = cd.isTextual() ? cd.asText() : cd.toString();
                summary = root.has("summary") ? root.get("summary").asText() : "";
                if (root.has("reasoning")) {
                    reasoning = root.get("reasoning").asText();
                }
            } else {
                chainData = root.toString();
                summary = "";
            }
            return new AiCopilotService.ParsedChainProposal(chainData, summary, reasoning);
        } catch (Exception e) {
            return new AiCopilotService.ParsedChainProposal(json, "", null);
        }
    }

    String maskIfNeeded(String text) {
        if (!aiPlatformConfig.isPiiMask() || !StringUtils.hasText(text)) {
            return text;
        }
        String masked = PHONE_PATTERN.matcher(text).replaceAll("138****0000");
        masked = ID_CARD_PATTERN.matcher(masked).replaceAll("110***********0000");
        return masked;
    }

    String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= SUMMARY_MAX_LEN ? text : text.substring(0, SUMMARY_MAX_LEN);
    }

    private static boolean matchesSessionContext(AiCopilotSessionPO session, String appCode,
                                                 String designId, String chainCode) {
        if (StringUtils.hasText(designId) && !designId.trim().equals(nullToEmpty(session.getDesignId()))) {
            return false;
        }
        if (StringUtils.hasText(appCode) && !appCode.trim().equals(nullToEmpty(session.getAppCode()))) {
            return false;
        }
        if (StringUtils.hasText(chainCode) && !chainCode.trim().equals(nullToEmpty(session.getChainCode()))) {
            return false;
        }
        return true;
    }

    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return 0L;
        }
        try {
            return SecurityUtils.getUserId(auth);
        } catch (Exception e) {
            return 0L;
        }
    }

    private static int estimateTokens(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }

    private static String truncateTitle(String title) {
        String trimmed = title.trim();
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 80) + "…";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
