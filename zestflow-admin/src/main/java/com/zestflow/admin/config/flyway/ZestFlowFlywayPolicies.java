package com.zestflow.admin.config.flyway;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;

/**
 * Admin Flyway 分层策略 — 非 prod 自愈升级，prod 严格校验（见 docs/FLYWAY_POLICY.md）。
 */
@Slf4j
public final class ZestFlowFlywayPolicies {

    private ZestFlowFlywayPolicies() {
    }

    /** 开发 / demo / 试玩：允许漏跑中间版本、checksum 漂移后 repair。 */
    public static void applyNonProductionPolicy(FluentConfiguration configuration) {
        configuration
                .outOfOrder(true)
                .validateOnMigrate(false);
    }

    /** 生产：顺序迁移 + migrate 前校验，禁止 out-of-order。 */
    public static void applyProductionPolicy(FluentConfiguration configuration) {
        configuration
                .outOfOrder(false)
                .validateOnMigrate(true);
    }

    /**
     * 非 prod 标准启动流程：repair → migrate，并打印 pending / 结果。
     */
    public static MigrateResult migrateNonProduction(Flyway flyway, String tag) {
        log.info("[{}] Flyway non-prod: repair → migrate (outOfOrder=true, validateOnMigrate=false)", tag);
        logPendingMigrations(flyway, tag);
        flyway.repair();
        MigrateResult result = flyway.migrate();
        var current = flyway.info().current();
        log.info("[{}] Flyway done: applied={}, current={}, success={}",
                tag,
                result.migrationsExecuted,
                current != null ? current.getVersion() : "none",
                result.success);
        if (!result.success) {
            throw new IllegalStateException("["
                    + tag
                    + "] Flyway migrate failed after repair; see docs/FLYWAY_POLICY.md §4");
        }
        return result;
    }

    private static void logPendingMigrations(Flyway flyway, String tag) {
        var pending = flyway.info().pending();
        if (pending.length == 0) {
            log.debug("[{}] Flyway: no pending migrations", tag);
            return;
        }
        for (var migration : pending) {
            log.info("[{}] Flyway pending: v{} {}", tag, migration.getVersion(), migration.getDescription());
        }
    }
}
