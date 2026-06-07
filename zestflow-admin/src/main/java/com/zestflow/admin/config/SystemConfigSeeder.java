package com.zestflow.admin.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.ai.AiProperties;
import com.zestflow.admin.alert.AlertProperties;
import com.zestflow.admin.model.entity.SysConfigPO;
import com.zestflow.admin.repository.SysConfigMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 平台 sys_config 种子（租户 1）：仅补齐缺失键，不覆盖已有运维修改。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemConfigSeeder {

    private final SysConfigMapper sysConfigMapper;
    private final AiProperties aiProperties;
    private final AlertProperties alertProperties;
    private final TenantModeConfig tenantModeConfig;
    private final Environment environment;
    private final PlatformConfigReader platformConfigReader;

    @PostConstruct
    void seedIfMissing() {
        int inserted = 0;
        for (Seed seed : buildSeeds()) {
            if (ensureSeed(seed)) {
                inserted++;
            }
        }
        if (inserted > 0) {
            log.info("平台 sys_config 种子补齐 count={}", inserted);
        }
        platformConfigReader.invalidate();
    }

    private List<Seed> buildSeeds() {
        List<Seed> seeds = new ArrayList<>();
        int sort = 1;
        seeds.add(seed(SysConfigKeys.AI_ENABLED, "Copilot 全局开关", bool(aiProperties.isEnabled()), "bool", "ai", sort++, "覆盖 yaml zestflow.ai.enabled"));
        seeds.add(seed(SysConfigKeys.AI_TIMEOUT_MS, "LLM 超时(ms)", str(aiProperties.getTimeoutMs()), "number", "ai", sort++, null));
        seeds.add(seed(SysConfigKeys.AI_MAX_TOKENS, "最大输出 Token", str(aiProperties.getMaxTokens()), "number", "ai", sort++, null));
        seeds.add(seed(SysConfigKeys.AI_TEMPERATURE, "采样温度", str(aiProperties.getTemperature()), "number", "ai", sort++, null));
        seeds.add(seed(SysConfigKeys.AI_PII_MASK, "Prompt PII 脱敏", bool(aiProperties.isPiiMask()), "bool", "ai", sort++, null));
        seeds.add(seed(SysConfigKeys.AI_REPAIR_MAX_ROUNDS, "Validator 修复轮次", str(aiProperties.getRepairMaxRounds()), "number", "ai", sort++, null));
        seeds.add(seed(SysConfigKeys.AI_TENANT_AUTO_INIT, "启动时自动初始化租户 AI", bool(aiProperties.isTenantAutoInit()), "bool", "ai", sort++, null));
        seeds.add(seed(SysConfigKeys.AI_RAG_ENABLED, "RAG 开关", bool(aiProperties.isRagEnabled()), "bool", "ai", sort++, null));
        seeds.add(seed(SysConfigKeys.AI_RAG_MODE, "RAG 模式", aiProperties.getRagMode(), "text", "ai", sort++, "keyword|vector|hybrid"));
        seeds.add(seed(SysConfigKeys.AI_RAG_USE_LLM_EMBEDDING, "RAG LLM Embedding 重排", bool(aiProperties.isRagUseLlmEmbedding()), "bool", "ai", sort++, null));
        seeds.add(seed(SysConfigKeys.AI_RAG_EMBEDDING_MODEL, "Embedding 模型", aiProperties.getRagEmbeddingModel(), "text", "ai", sort++, null));
        seeds.add(seed(SysConfigKeys.AI_RAG_EMBEDDING_CANDIDATE_LIMIT, "Embedding 候选上限", str(aiProperties.getRagEmbeddingCandidateLimit()), "number", "ai", sort++, null));
        seeds.add(seed(SysConfigKeys.AI_RAG_MAX_CHUNKS, "RAG 注入片段数", str(aiProperties.getRagMaxChunks()), "number", "ai", sort++, null));
        seeds.add(seed(SysConfigKeys.AI_RAG_TENANT_DATA_DIR, "租户 RAG 目录", aiProperties.getRagTenantDataDir(), "text", "ai", sort++, null));
        seeds.add(seed(SysConfigKeys.AI_RAG_TENANT_FILESYSTEM_ENABLED, "扫描租户 RAG 目录", bool(aiProperties.isRagTenantFilesystemEnabled()), "bool", "ai", sort++, null));
        seeds.add(seed(SysConfigKeys.AI_RAG_TENANT_MAX_DOCUMENTS, "每租户 RAG 文档上限", str(aiProperties.getRagTenantMaxDocuments()), "number", "ai", sort++, null));
        seeds.add(seed(SysConfigKeys.AI_RAG_TENANT_MAX_CONTENT_BYTES, "单文档最大字节", str(aiProperties.getRagTenantMaxContentBytes()), "number", "ai", sort++, null));
        seeds.add(seed(SysConfigKeys.AI_DEFAULT_MONTHLY_TOKEN_QUOTA, "默认月 Token 配额", str(aiProperties.getDefaultMonthlyTokenQuota()), "number", "ai", sort++, "0=不限"));

        sort = 1;
        seeds.add(seed(SysConfigKeys.ALERT_ENABLED, "SLA 告警总开关", bool(alertProperties.isEnabled()), "bool", "alert", sort++, null));
        seeds.add(seed(SysConfigKeys.ALERT_SCAN_INTERVAL_MS, "扫描间隔(ms)", str(alertProperties.getScanIntervalMs()), "number", "alert", sort++, "修改后需重启生效"));
        seeds.add(seed(SysConfigKeys.ALERT_COOLDOWN_MINUTES, "告警冷却(分钟)", str(alertProperties.getCooldownMinutes()), "number", "alert", sort++, null));
        seeds.add(seed(SysConfigKeys.ALERT_WINDOW_MINUTES, "统计窗口(分钟)", str(alertProperties.getWindowMinutes()), "number", "alert", sort++, null));
        seeds.add(seed(SysConfigKeys.ALERT_MIN_EXECUTIONS, "最少执行次数", str(alertProperties.getMinExecutions()), "number", "alert", sort++, null));
        seeds.add(seed(SysConfigKeys.ALERT_SUCCESS_RATE_THRESHOLD, "成功率阈值", str(alertProperties.getSuccessRateThreshold()), "number", "alert", sort++, null));
        seeds.add(seed(SysConfigKeys.ALERT_FAIL_COUNT_THRESHOLD, "失败次数阈值", str(alertProperties.getFailCountThreshold()), "number", "alert", sort++, null));
        seeds.add(seed(SysConfigKeys.ALERT_P95_COST_MS_THRESHOLD, "P95 耗时阈值(ms)", str(alertProperties.getP95CostMsThreshold()), "number", "alert", sort++, null));
        seeds.add(seed(SysConfigKeys.ALERT_SCHEDULE_FAIL_THRESHOLD, "调度失败阈值", str(alertProperties.getScheduleFailThreshold()), "number", "alert", sort++, null));
        seeds.add(seed(SysConfigKeys.ALERT_NO_ONLINE_EXECUTOR, "无在线执行器告警", bool(alertProperties.isAlertNoOnlineExecutor()), "bool", "alert", sort++, null));
        seeds.add(seed(SysConfigKeys.ALERT_SUBJECT_PREFIX, "邮件主题前缀", alertProperties.getSubjectPrefix(), "text", "alert", sort++, null));

        sort = 1;
        boolean playgroundEnabled = environment.getProperty("zestflow.playground.enabled", Boolean.class, Boolean.TRUE);
        int playgroundTimeout = environment.getProperty("zestflow.playground.execute-timeout-ms", Integer.class, 30_000);
        int playgroundRate = environment.getProperty("zestflow.playground.rate-limit", Integer.class, 30);
        seeds.add(seed(SysConfigKeys.PLAYGROUND_ENABLED, "系统演示开关", bool(playgroundEnabled), "bool", "playground", sort++, "前端菜单与 /system/features"));
        seeds.add(seed(SysConfigKeys.PLAYGROUND_EXECUTE_TIMEOUT_MS, "演示执行超时(ms)", str(playgroundTimeout), "number", "playground", sort++, null));
        seeds.add(seed(SysConfigKeys.PLAYGROUND_RATE_LIMIT, "默认每 IP 限流/分钟", str(playgroundRate), "number", "playground", sort++, null));

        sort = 1;
        seeds.add(seed(SysConfigKeys.TENANT_IP_TIMEOUT_MINUTES, "IP 试玩超时(分钟)", str(tenantModeConfig.getIpTenantTimeoutMinutes()), "number", "tenant", sort++, null));
        seeds.add(seed(SysConfigKeys.TENANT_TRIAL_LIFECYCLE_ENABLED, "试玩租户回收", bool(tenantModeConfig.isTrialLifecycleEnabled()), "bool", "tenant", sort++, null));
        seeds.add(seed(SysConfigKeys.TENANT_PUBLIC_PROVISION_ENABLED, "开放公开开户 API", bool(tenantModeConfig.isPublicProvisionEnabled()), "bool", "tenant", sort++, null));

        return seeds;
    }

    private boolean ensureSeed(Seed seed) {
        Long count = sysConfigMapper.selectCount(
                new LambdaQueryWrapper<SysConfigPO>()
                        .eq(SysConfigPO::getTenantId, PlatformConfigReader.PLATFORM_TENANT_ID)
                        .eq(SysConfigPO::getConfigKey, seed.key()));
        if (count != null && count > 0) {
            return false;
        }
        SysConfigPO po = new SysConfigPO();
        po.setConfigKey(seed.key());
        po.setConfigName(seed.name());
        po.setConfigValue(seed.value());
        po.setValueType(seed.valueType());
        po.setCategory(seed.category());
        po.setStatus(1);
        po.setSort(seed.sort());
        po.setRemark(seed.remark());
        po.setTenantId(PlatformConfigReader.PLATFORM_TENANT_ID);
        sysConfigMapper.insert(po);
        return true;
    }

    private static Seed seed(String key, String name, String value, String valueType, String category, int sort, String remark) {
        return new Seed(key, name, value, valueType, category, sort, remark);
    }

    private static String bool(boolean v) {
        return v ? "true" : "false";
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private record Seed(String key, String name, String value, String valueType, String category, int sort, String remark) {
    }
}
