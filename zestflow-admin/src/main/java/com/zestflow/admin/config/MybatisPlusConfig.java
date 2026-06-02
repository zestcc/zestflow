package com.zestflow.admin.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
public class MybatisPlusConfig {

    /** 租户过滤排除表 — 这些表不自动注入 tenant_id 条件 */
    private static final Set<String> EXCLUDED_TABLES = Set.of(
            "tenant", "user_tenant", "role", "tenant_ip_mapping"
    );

    @Value("${zestflow.admin.tenant-id:1}")
    private Long defaultTenantId;

    @Value("${zestflow.tenant.mode:single}")
    private String tenantMode;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 多租户模式启用行级隔离；单租户模式跳过（Service 层已确保 tenant_id 填充，且 JSqlParser 4.9
        // parseStatements() 不支持 UPDATE 语句的解析，会造成运行时异常）
        if (!"single".equals(tenantMode)) {
            TenantLineInnerInterceptor tenantInterceptor = new TenantLineInnerInterceptor();
            tenantInterceptor.setTenantLineHandler(new TenantLineHandler() {
                @Override
                public Expression getTenantId() {
                    Long tenantId = TenantContextHolder.getTenantId();
                    return new LongValue(tenantId != null ? tenantId : defaultTenantId);
                }

                @Override
                public boolean ignoreTable(String tableName) {
                    return isExcludedFromTenantLine(tableName);
                }
            });
            interceptor.addInnerInterceptor(tenantInterceptor);
        }

        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /** JSQLParser 表名可能带反引号或库前缀，需归一化后再匹配排除列表 */
    static boolean isExcludedFromTenantLine(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return false;
        }
        String bare = tableName.replace("`", "").trim();
        int dot = bare.lastIndexOf('.');
        if (dot >= 0) {
            bare = bare.substring(dot + 1);
        }
        return EXCLUDED_TABLES.contains(bare.toLowerCase());
    }
}
