package com.zestflow.executor.chain;

import com.zestflow.common.constant.ChainDeliveryLifecycle;
import com.zestflow.executor.registry.ExecutorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 运行时链元数据登记：内存 load 后同步 zf_chain 为已发布，供 ChainKeyResolver 放行。
 */
@Slf4j
@RequiredArgsConstructor
public class ChainRuntimeRegistrar {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final ExecutorProperties executorProperties;
    private final long tenantId;

    public void ensurePublished(String chainCode) {
        ensurePublished(chainCode, chainCode);
    }

    public void ensurePublished(String chainCode, String name) {
        if (chainCode == null || chainCode.isBlank()) {
            return;
        }
        String appCode = executorProperties.getAppCode();
        String now = LocalDateTime.now().format(DTF);
        synchronized (lockFor(chainCode)) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM zf_chain WHERE code = ? AND tenant_id = ? AND is_deleted = 0",
                    Integer.class, chainCode, tenantId);
            if (count != null && count > 0) {
                markPublishedRow(chainCode, now);
                return;
            }
            try {
                jdbcTemplate.update(
                        "INSERT INTO zf_chain (code, name, description, status, version, delivery_lifecycle, tenant_id, app_code, "
                                + "created_by, updated_by, created_at, updated_at, is_deleted) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        chainCode,
                        name != null ? name : chainCode,
                        "Runtime loaded chain",
                        ChainLifecycleStatus.PUBLISHED,
                        1,
                        ChainDeliveryLifecycle.PRODUCTION,
                        tenantId,
                        appCode,
                        "runtime",
                        "runtime",
                        now,
                        now,
                        0);
                log.debug("运行时链元数据已登记 code={}", chainCode);
            } catch (DataIntegrityViolationException e) {
                markPublishedRow(chainCode, now);
            }
        }
    }

    private void markPublishedRow(String chainCode, String now) {
        jdbcTemplate.update(
                "UPDATE zf_chain SET status = ?, delivery_lifecycle = ?, updated_at = ? WHERE code = ? AND tenant_id = ?",
                ChainLifecycleStatus.PUBLISHED, ChainDeliveryLifecycle.PRODUCTION, now, chainCode, tenantId);
    }

    private static Object lockFor(String chainCode) {
        return ("chain-registrar:" + chainCode).intern();
    }
}
