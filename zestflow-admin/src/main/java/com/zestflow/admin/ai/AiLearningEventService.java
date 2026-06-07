package com.zestflow.admin.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.admin.ai.model.dto.AiLearningEventSaveDTO;
import com.zestflow.admin.ai.model.dto.AiRagDocumentSaveDTO;
import com.zestflow.admin.ai.model.entity.AiLearningEventPO;
import com.zestflow.admin.ai.model.vo.AiLearningEventVO;
import com.zestflow.admin.ai.model.vo.AiRagDocumentVO;
import com.zestflow.admin.ai.repository.AiLearningEventMapper;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.util.SecurityUtils;
import com.zestflow.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P1～P3 租户级学习事件 — Orchestration Copilot 与 Dev 摘要统一沉淀。
 */
@Service
@RequiredArgsConstructor
public class AiLearningEventService {

    private static final double PROMOTION_THRESHOLD = 0.97;

    private final AiLearningEventMapper learningEventMapper;
    private final TenantAiConfigService tenantAiConfigService;
    private final AiRagDocumentService ragDocumentService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiLearningEventVO record(AiLearningEventSaveDTO dto) {
        Long tenantId = tenantAiConfigService.getCurrentTenantId();
        PromotionEval eval = evaluate(dto);

        AiLearningEventPO po = new AiLearningEventPO();
        po.setTenantId(tenantId);
        po.setUserId(currentUserId());
        po.setAppCode(trim(dto.getAppCode()));
        po.setSessionId(dto.getSessionId());
        po.setIntent(requireIntent(dto.getIntent()));
        po.setFeature(trim(dto.getFeature()));
        po.setChainCode(trim(dto.getChainCode()));
        po.setHttpMode(dto.getHttpMode());
        po.setPayloadJson(buildPayloadJson(dto));
        po.setValidatePassed(boolInt(dto.getValidatePassed()));
        po.setValidateRounds(dto.getValidateRounds());
        po.setAdopted(boolInt(dto.getAdopted()));
        po.setPlaygroundSuccess(boolInt(dto.getPlaygroundSuccess()));
        po.setPromotionScore(BigDecimal.valueOf(eval.score()).setScale(4, RoundingMode.HALF_UP));
        po.setPromotionEligible(eval.eligible() ? 1 : 0);
        po.setUserCorrection(truncate(dto.getUserCorrection(), 1000));
        po.setPromotedToRag(0);
        learningEventMapper.insert(po);
        return toVo(po);
    }

    public List<AiLearningEventVO> listRecent(String appCode, int limit) {
        Long tenantId = tenantAiConfigService.getCurrentTenantId();
        LambdaQueryWrapper<AiLearningEventPO> q = new LambdaQueryWrapper<AiLearningEventPO>()
                .eq(AiLearningEventPO::getTenantId, tenantId)
                .orderByDesc(AiLearningEventPO::getCreatedAt)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 100));
        if (StringUtils.hasText(appCode)) {
            q.eq(AiLearningEventPO::getAppCode, appCode.trim());
        }
        return learningEventMapper.selectList(q).stream().map(this::toVo).toList();
    }

    public AiRagDocumentVO promoteToRag(Long eventId) {
        AiLearningEventPO po = requireOwned(eventId);
        if (po.getPromotionEligible() == null || po.getPromotionEligible() != 1) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "事件未达晋升门槛（≥97%）");
        }
        if (po.getPromotedToRag() != null && po.getPromotedToRag() == 1) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "已晋升到 RAG");
        }
        String markdown = buildRagMarkdown(po);
        AiRagDocumentSaveDTO save = new AiRagDocumentSaveDTO();
        save.setTitle("[Pattern] " + po.getFeature() + " / " + po.getIntent());
        save.setAppCode(po.getAppCode());
        save.setContent(markdown);
        save.setEnabled(true);
        AiRagDocumentVO doc = ragDocumentService.save(save);
        po.setPromotedToRag(1);
        learningEventMapper.updateById(po);
        return doc;
    }

    private AiLearningEventPO requireOwned(Long id) {
        AiLearningEventPO po = learningEventMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        Long tenantId = tenantAiConfigService.getCurrentTenantId();
        if (!tenantId.equals(po.getTenantId())) {
            throw new BizException(ErrorCode.PERMISSION_DENIED);
        }
        return po;
    }

    private String buildRagMarkdown(AiLearningEventPO po) {
        return """
                # Team Pattern (from learning event #%d)

                - intent: `%s`
                - feature: `%s`
                - chainCode: `%s`
                - httpMode: %s
                - promotionScore: %s

                ## 用户修正
                %s

                ## 说明
                由 Admin 学习事件晋升（P3）；Dev 侧可用 share_pattern + RAG import 同步。
                """.formatted(
                po.getId(),
                po.getIntent(),
                po.getFeature(),
                po.getChainCode() != null ? po.getChainCode() : "-",
                po.getHttpMode() != null ? po.getHttpMode() : "-",
                po.getPromotionScore(),
                po.getUserCorrection() != null ? po.getUserCorrection() : "无");
    }

    private PromotionEval evaluate(AiLearningEventSaveDTO dto) {
        double score = 0.70;
        if (Boolean.TRUE.equals(dto.getValidatePassed())) {
            score += 0.12;
        }
        if (dto.getValidateRounds() != null && dto.getValidateRounds() <= 1) {
            score += 0.05;
        } else if (dto.getValidateRounds() != null && dto.getValidateRounds() == 2) {
            score += 0.02;
        }
        if (Boolean.TRUE.equals(dto.getAdopted())) {
            score += 0.08;
        }
        if (Boolean.TRUE.equals(dto.getPlaygroundSuccess())) {
            score += 0.05;
        }
        if (!StringUtils.hasText(dto.getUserCorrection())) {
            score += 0.03;
        }
        if (dto.getHttpMode() != null && dto.getHttpMode() >= 1 && dto.getHttpMode() <= 3) {
            score += 0.02;
        }
        score = Math.min(1.0, score);
        boolean eligible = score >= PROMOTION_THRESHOLD
                && Boolean.TRUE.equals(dto.getValidatePassed())
                && (Boolean.TRUE.equals(dto.getAdopted()) || Boolean.TRUE.equals(dto.getPlaygroundSuccess()))
                && (dto.getValidateRounds() == null || dto.getValidateRounds() <= 2);
        return new PromotionEval(score, eligible);
    }

    private String buildPayloadJson(AiLearningEventSaveDTO dto) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reusedComponents", dto.getReusedComponents());
        payload.put("createdComponents", dto.getCreatedComponents());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private AiLearningEventVO toVo(AiLearningEventPO po) {
        return AiLearningEventVO.builder()
                .id(po.getId())
                .appCode(po.getAppCode())
                .intent(po.getIntent())
                .feature(po.getFeature())
                .chainCode(po.getChainCode())
                .httpMode(po.getHttpMode())
                .validatePassed(intBool(po.getValidatePassed()))
                .validateRounds(po.getValidateRounds())
                .adopted(intBool(po.getAdopted()))
                .playgroundSuccess(intBool(po.getPlaygroundSuccess()))
                .promotionScore(po.getPromotionScore())
                .promotionEligible(intBool(po.getPromotionEligible()))
                .userCorrection(po.getUserCorrection())
                .promotedToRag(intBool(po.getPromotedToRag()))
                .createdAt(po.getCreatedAt())
                .build();
    }

    private static String requireIntent(String intent) {
        if (!StringUtils.hasText(intent)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "intent 不能为空");
        }
        return intent.trim();
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private static Integer boolInt(Boolean b) {
        if (b == null) {
            return null;
        }
        return b ? 1 : 0;
    }

    private static Boolean intBool(Integer i) {
        if (i == null) {
            return null;
        }
        return i == 1;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private record PromotionEval(double score, boolean eligible) {
    }

    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? SecurityUtils.getUserId(auth) : null;
    }
}
