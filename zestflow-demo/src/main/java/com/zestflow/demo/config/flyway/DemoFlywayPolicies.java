package com.zestflow.demo.config.flyway;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;

/** 与 admin {@code ZestFlowFlywayPolicies} 保持同一非 prod 策略。 */
@Slf4j
public final class DemoFlywayPolicies {

    private DemoFlywayPolicies() {
    }

    public static void applyNonProductionPolicy(FluentConfiguration configuration) {
        configuration.outOfOrder(true).validateOnMigrate(false);
    }

    public static MigrateResult migrateNonProduction(Flyway flyway, String tag) {
        log.info("[{}] Flyway non-prod: repair → migrate", tag);
        flyway.repair();
        MigrateResult result = flyway.migrate();
        var current = flyway.info().current();
        log.info("[{}] Flyway done: applied={}, current={}", tag, result.migrationsExecuted,
                current != null ? current.getVersion() : "none");
        if (!result.success) {
            throw new IllegalStateException("[" + tag + "] Flyway migrate failed");
        }
        return result;
    }
}
