package com.zestflow.admin.config.flyway;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

/**
 * Beta rebaseline（2026-06-08）：旧版 V2 beta / V4 / V5 / V6 history 与新版 V1→V2→V3 不兼容。
 * 非 prod 启动时自动清理 legacy history 后全量 migrate（脚本均幂等）。
 */
@Slf4j
final class FlywayLegacyHistoryCleaner {

    private static final Set<String> LEGACY_VERSIONS = Set.of("4", "5", "6");

    private FlywayLegacyHistoryCleaner() {
    }

    static void resetLegacyHistoryIfNeeded(Flyway flyway, String tag) {
        if (!containsLegacyHistory(flyway)) {
            return;
        }
        log.warn("[{}] 检测到 Beta 旧 Flyway history（V4/V5/V6 或旧 V2 align）；"
                + "非 prod 自动清空 flyway_schema_history 并按 V1→V2→V3 重放（DDL/DML 幂等）", tag);
        clearSchemaHistory(flyway, tag);
    }

    private static boolean containsLegacyHistory(Flyway flyway) {
        for (MigrationInfo info : flyway.info().applied()) {
            if (info.getVersion() == null) {
                continue;
            }
            String version = info.getVersion().getVersion();
            if (LEGACY_VERSIONS.contains(version)) {
                return true;
            }
            if ("2".equals(version)) {
                String script = info.getScript();
                String desc = info.getDescription();
                if ((script != null && script.contains("beta_schema_align"))
                        || (desc != null && desc.toLowerCase().contains("beta"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void clearSchemaHistory(Flyway flyway, String tag) {
        DataSource dataSource = flyway.getConfiguration().getDataSource();
        if (dataSource == null) {
            log.warn("[{}] 无法清空 Flyway history：DataSource 为空", tag);
            return;
        }
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.execute("DELETE FROM flyway_schema_history");
            log.info("[{}] 已清空 flyway_schema_history", tag);
        } catch (SQLException e) {
            throw new IllegalStateException("["
                    + tag
                    + "] 清空 flyway_schema_history 失败；可手工执行 scripts/deploy/rebaseline-admin-dev.sql", e);
        }
    }
}
