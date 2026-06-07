package com.zestflow.admin.config.flyway;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 迁移脚本门禁：命名规范 + 版本号唯一（不连库，CI 可跑）。
 */
class FlywayMigrationScriptsTest {

    private static final Pattern VERSIONED = Pattern.compile("^V\\d+__.*\\.sql$");

    @Test
    void migrationFiles_followNamingAndUniqueVersions() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:db/migration/V*.sql");
        assertTrue(resources.length >= 1, "至少应有一个 Flyway 脚本");

        Set<String> versions = new HashSet<>();
        for (Resource resource : resources) {
            String name = resource.getFilename();
            assertTrue(name != null && VERSIONED.matcher(name).matches(),
                    "非法迁移文件名: " + name + "，应为 V{n}__desc.sql");

            String version = name.substring(1, name.indexOf("__"));
            assertFalse(versions.contains(version), "重复 Flyway 版本: " + version);
            versions.add(version);
        }
    }
}
