package com.zestflow.admin.ai;

import com.zestflow.admin.ai.model.dto.*;
import com.zestflow.admin.ai.model.vo.*;
import com.zestflow.admin.client.ExecutorProxyService;
import com.zestflow.admin.config.AiPlatformConfig;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.service.PermissionService;
import com.zestflow.admin.service.TenantAppContext;
import com.zestflow.admin.util.SecurityUtils;
import com.zestflow.common.exception.BizException;
import com.zestflow.common.model.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI Copilot REST API
 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiCopilotController {

    private final AiCopilotService aiCopilotService;
    private final TenantAiConfigService tenantAiConfigService;
    private final AiChainKeyHintService chainKeyHintService;
    private final AiChainTemplateService chainTemplateService;
    private final AiRagService aiRagService;
    private final AiRagDocumentService ragDocumentService;
    private final AiUsageStatsService usageStatsService;
    private final AiLearningEventService aiLearningEventService;
    private final AiPlatformConfig aiPlatformConfig;
    private final TenantAppContext tenantAppContext;
    private final PermissionService permissionService;
    private final ExecutorProxyService executorProxyService;
    private final ExecutorChainAiClient executorChainAiClient;

    @GetMapping("/config")
    public Result<AiConfigStatusVO> getConfig() {
        Long tenantId = tenantAppContext.getCurrentTenantId();
        return Result.success(tenantAiConfigService.getConfigStatus(tenantId));
    }

    @GetMapping("/providers")
    public Result<List<AiProviderVO>> listProviders() {
        Long tenantId = tenantAppContext.getCurrentTenantId();
        return Result.success(tenantAiConfigService.listProvidersForTenant(tenantId));
    }

    @PostMapping("/test")
    public Result<AiTestConnectionResponse> testConnection(@RequestBody AiTestConnectionRequest request) {
        requireSuperAdminOrTenantAdmin();
        return Result.success(aiCopilotService.testConnection(request));
    }

    @GetMapping("/tenant-config")
    public Result<AiTenantConfigVO> getTenantConfig() {
        requireSuperAdminOrTenantAdmin();
        Long tenantId = tenantAppContext.getCurrentTenantId();
        return Result.success(tenantAiConfigService.getTenantConfig(tenantId));
    }

    @PutMapping("/tenant-config")
    public Result<AiTenantConfigVO> saveTenantConfig(@RequestBody AiTenantConfigSaveDTO dto) {
        requireSuperAdminOrTenantAdmin();
        Long tenantId = tenantAppContext.getCurrentTenantId();
        return Result.success(tenantAiConfigService.saveTenantConfig(tenantId, dto));
    }

    @GetMapping("/context/components")
    public Result<String> listComponents(@RequestParam String appCode) {
        requireAppEditor(appCode);
        String json = executorProxyService.getFromExecutor(appCode, "/api/components", "?page=1&size=9999");
        return Result.success(json);
    }

    @GetMapping("/context/chain-keys")
    public Result<AiChainKeyHintsVO> chainKeys(@RequestParam String appCode) {
        requireAppEditor(appCode);
        return Result.success(chainKeyHintService.getHints(appCode));
    }

    @GetMapping("/templates")
    public Result<List<AiChainTemplateVO>> listTemplates(@RequestParam(required = false) String appCode) {
        if (StringUtils.hasText(appCode)) {
            requireAppEditor(appCode);
        }
        return Result.success(chainTemplateService.list(appCode));
    }

    @GetMapping("/templates/{id}")
    public Result<AiChainTemplateVO> getTemplate(@PathVariable Long id) {
        return Result.success(chainTemplateService.get(id));
    }

    @PostMapping("/templates")
    public Result<AiChainTemplateVO> saveTemplate(@RequestBody AiChainTemplateSaveDTO dto) {
        if (StringUtils.hasText(dto.getAppCode())) {
            requireAppEditor(dto.getAppCode());
        }
        return Result.success(chainTemplateService.save(dto));
    }

    @DeleteMapping("/templates/{id}")
    public Result<Void> deleteTemplate(@PathVariable Long id) {
        chainTemplateService.delete(id);
        return Result.success();
    }

    @GetMapping("/rag/search")
    public Result<List<String>> ragSearch(@RequestParam String q,
                                         @RequestParam(required = false) String appCode,
                                         @RequestParam(required = false, defaultValue = "3") int limit) {
        Long tenantId = tenantAppContext.getCurrentTenantId();
        java.util.ArrayList<String> merged = new java.util.ArrayList<>();
        if (StringUtils.hasText(appCode)) {
            merged.addAll(executorChainAiClient.searchRag(appCode, q, limit));
        }
        if (aiPlatformConfig.isRagEnabled()) {
            merged.addAll(aiRagService.retrieve(tenantId, appCode, q, Math.max(1, limit / 2)));
        }
        return Result.success(merged.stream().limit(limit).toList());
    }

    @GetMapping("/rag/status")
    public Result<java.util.Map<String, Object>> ragStatus() {
        java.util.Map<String, Object> status = new java.util.LinkedHashMap<>();
        Long tenantId = tenantAppContext.getCurrentTenantId();
        status.put("enabled", aiPlatformConfig.isRagEnabled());
        status.put("mode", aiRagService.retrievalMode());
        status.put("platformChunks", aiRagService.globalChunkCount());
        status.put("tenantChunks", aiRagService.tenantChunkCount(tenantId));
        status.put("tenantDocuments", ragDocumentService.countTenantDocuments(tenantId));
        if (aiPlatformConfig.isRagTenantFilesystemEnabled()) {
            String dir = aiPlatformConfig.getRagTenantDataDir();
            status.put("filesystemPath", dir + "/{tenantId}/*.md");
        }
        return Result.success(status);
    }

    @GetMapping("/rag/documents")
    public Result<List<AiRagDocumentVO>> listRagDocuments(@RequestParam(required = false) String appCode) {
        return Result.success(ragDocumentService.list(appCode));
    }

    @GetMapping("/rag/documents/{id}")
    public Result<AiRagDocumentVO> getRagDocument(@PathVariable Long id) {
        return Result.success(ragDocumentService.get(id));
    }

    @PostMapping("/rag/documents")
    public Result<AiRagDocumentVO> saveRagDocument(@RequestBody AiRagDocumentSaveDTO dto) {
        requireSuperAdminOrTenantAdmin();
        return Result.success(ragDocumentService.save(dto));
    }

    @PutMapping("/rag/documents/{id}")
    public Result<AiRagDocumentVO> updateRagDocument(@PathVariable Long id,
                                                     @RequestBody AiRagDocumentSaveDTO dto) {
        requireSuperAdminOrTenantAdmin();
        return Result.success(ragDocumentService.update(id, dto));
    }

    @DeleteMapping("/rag/documents/{id}")
    public Result<Void> deleteRagDocument(@PathVariable Long id) {
        requireSuperAdminOrTenantAdmin();
        ragDocumentService.delete(id);
        return Result.success();
    }

    @PostMapping("/rag/documents/rebuild-index")
    public Result<Void> rebuildRagIndex() {
        requireSuperAdminOrTenantAdmin();
        ragDocumentService.rebuildIndex();
        return Result.success();
    }

    @GetMapping("/rag/documents/export")
    public Result<AiRagDocumentExportVO> exportRagDocuments(@RequestParam(required = false) String appCode) {
        requireSuperAdminOrTenantAdmin();
        return Result.success(ragDocumentService.exportDocuments(appCode));
    }

    @PostMapping("/rag/documents/import")
    public Result<java.util.Map<String, Object>> importRagDocuments(@RequestBody AiRagDocumentImportDTO dto) {
        requireSuperAdminOrTenantAdmin();
        int count = ragDocumentService.importDocuments(dto);
        return Result.success(java.util.Map.of("imported", count));
    }

    @GetMapping("/usage/overview")
    public Result<AiUsageOverviewVO> usageOverview(@RequestParam(defaultValue = "30") int days) {
        requireSuperAdminOrTenantAdmin();
        return Result.success(usageStatsService.overview(days));
    }

    @PostMapping("/design/explain")
    public Result<AiExplainResponse> explain(@RequestBody AiExplainRequest request) {
        requireAppEditor(request.getAppCode());
        return Result.success(aiCopilotService.explain(request));
    }

    @PostMapping("/design/suggest")
    public Result<AiSuggestResponse> suggest(@RequestBody AiSuggestRequest request) {
        requireAppEditor(request.getAppCode());
        return Result.success(aiCopilotService.suggest(request));
    }

    @PostMapping("/design/validate")
    public Result<AiValidationVO> validate(@RequestBody AiValidateRequest request) {
        if (StringUtils.hasText(request.getAppCode())) {
            requireAppEditor(request.getAppCode());
        }
        return Result.success(aiCopilotService.validate(request));
    }

    @PostMapping("/expression/suggest")
    public Result<AiExpressionSuggestResponse> expressionSuggest(@RequestBody AiExpressionSuggestRequest request) {
        requireAppEditor(request.getAppCode());
        return Result.success(aiCopilotService.expressionSuggest(request));
    }

    @PostMapping("/logs/diagnose")
    public Result<AiDiagnoseResponse> diagnose(@RequestBody AiDiagnoseRequest request) {
        requireAppEditor(request.getAppCode());
        return Result.success(aiCopilotService.diagnose(request));
    }

    @PostMapping("/sessions/{id}/feedback")
    public Result<Void> sessionFeedback(@PathVariable Long id, @RequestBody AiSessionFeedbackDTO dto) {
        aiCopilotService.recordFeedback(id, dto);
        return Result.success();
    }

    @PostMapping("/learning/events")
    public Result<java.util.Map<String, Object>> recordLearningEvent(@RequestBody AiLearningEventSaveDTO dto) {
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("intent", dto.getIntent());
        body.put("feature", dto.getFeature());
        body.put("appCode", dto.getAppCode());
        body.put("chainCode", dto.getChainCode());
        body.put("httpMode", dto.getHttpMode());
        body.put("validatePassed", dto.getValidatePassed());
        body.put("validateRounds", dto.getValidateRounds());
        body.put("adopted", dto.getAdopted());
        body.put("playgroundSuccess", dto.getPlaygroundSuccess());
        body.put("userCorrection", dto.getUserCorrection());
        body.put("reusedComponents", dto.getReusedComponents());
        body.put("createdComponents", dto.getCreatedComponents());
        return Result.success(executorChainAiClient.recordLearningEvent(dto.getAppCode(), body));
    }

    @GetMapping("/learning/events")
    public Result<List<AiLearningEventVO>> listLearningEvents(
            @RequestParam(required = false) String appCode,
            @RequestParam(defaultValue = "30") int limit) {
        return Result.success(aiLearningEventService.listRecent(appCode, limit));
    }

    @PostMapping("/learning/events/{id}/promote-rag")
    public Result<AiRagDocumentVO> promoteLearningEventToRag(@PathVariable Long id) {
        requireSuperAdminOrTenantAdmin();
        return Result.success(aiLearningEventService.promoteToRag(id));
    }

    private void requireAppEditor(String appCode) {
        if (!StringUtils.hasText(appCode)) {
            return;
        }
        requireAppPermission(appCode, "APP_EDITOR");
    }

    private void requireAppPermission(String appCode, String permission) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (SecurityUtils.isSuperAdmin(auth)) {
            return;
        }
        Long userId = SecurityUtils.getUserId(auth);
        if (userId == null || !permissionService.hasAppPermission(userId, appCode, permission)) {
            throw new BizException(ErrorCode.PERMISSION_DENIED);
        }
    }

    /** 超管或当前租户已登录用户（配置按 tenant_id 隔离） */
    private void requireSuperAdminOrTenantAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }
}
