package com.zestflow.admin.config;

/**
 * 平台级 sys_config 键（租户 1 维护，全租户运行时读取）。
 */
public final class SysConfigKeys {

    private SysConfigKeys() {
    }

    // AI
    public static final String AI_ENABLED = "ai.enabled";
    public static final String AI_TIMEOUT_MS = "ai.timeout_ms";
    public static final String AI_MAX_TOKENS = "ai.max_tokens";
    public static final String AI_TEMPERATURE = "ai.temperature";
    public static final String AI_PII_MASK = "ai.pii_mask";
    public static final String AI_REPAIR_MAX_ROUNDS = "ai.repair_max_rounds";
    public static final String AI_TENANT_AUTO_INIT = "ai.tenant_auto_init";
    public static final String AI_RAG_ENABLED = "ai.rag_enabled";
    public static final String AI_RAG_MODE = "ai.rag_mode";
    public static final String AI_RAG_USE_LLM_EMBEDDING = "ai.rag_use_llm_embedding";
    public static final String AI_RAG_EMBEDDING_MODEL = "ai.rag_embedding_model";
    public static final String AI_RAG_EMBEDDING_CANDIDATE_LIMIT = "ai.rag_embedding_candidate_limit";
    public static final String AI_RAG_MAX_CHUNKS = "ai.rag_max_chunks";
    public static final String AI_RAG_TENANT_DATA_DIR = "ai.rag_tenant_data_dir";
    public static final String AI_RAG_TENANT_FILESYSTEM_ENABLED = "ai.rag_tenant_filesystem_enabled";
    public static final String AI_RAG_TENANT_MAX_DOCUMENTS = "ai.rag_tenant_max_documents";
    public static final String AI_RAG_TENANT_MAX_CONTENT_BYTES = "ai.rag_tenant_max_content_bytes";
    public static final String AI_DEFAULT_MONTHLY_TOKEN_QUOTA = "ai.default_monthly_token_quota";

    // Alert
    public static final String ALERT_ENABLED = "alert.enabled";
    public static final String ALERT_SCAN_INTERVAL_MS = "alert.scan_interval_ms";
    public static final String ALERT_COOLDOWN_MINUTES = "alert.cooldown_minutes";
    public static final String ALERT_WINDOW_MINUTES = "alert.window_minutes";
    public static final String ALERT_MIN_EXECUTIONS = "alert.min_executions";
    public static final String ALERT_SUCCESS_RATE_THRESHOLD = "alert.success_rate_threshold";
    public static final String ALERT_FAIL_COUNT_THRESHOLD = "alert.fail_count_threshold";
    public static final String ALERT_P95_COST_MS_THRESHOLD = "alert.p95_cost_ms_threshold";
    public static final String ALERT_SCHEDULE_FAIL_THRESHOLD = "alert.schedule_fail_threshold";
    public static final String ALERT_NO_ONLINE_EXECUTOR = "alert.alert_no_online_executor";
    public static final String ALERT_SUBJECT_PREFIX = "alert.subject_prefix";

    // Playground
    public static final String PLAYGROUND_ENABLED = "playground.enabled";
    public static final String PLAYGROUND_EXECUTE_TIMEOUT_MS = "playground.execute_timeout_ms";
    public static final String PLAYGROUND_RATE_LIMIT = "playground.rate_limit";

    // Tenant ops
    public static final String TENANT_IP_TIMEOUT_MINUTES = "tenant.ip_tenant_timeout_minutes";
    public static final String TENANT_TRIAL_LIFECYCLE_ENABLED = "tenant.trial_lifecycle_enabled";
    public static final String TENANT_PUBLIC_PROVISION_ENABLED = "tenant.public_provision_enabled";
}
