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

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        TenantLineInnerInterceptor tenantInterceptor = new TenantLineInnerInterceptor();
        tenantInterceptor.setTenantLineHandler(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                Long tenantId = TenantContextHolder.getTenantId();
                return new LongValue(tenantId != null ? tenantId : defaultTenantId);
            }

            @Override
            public boolean ignoreTable(String tableName) {
                return EXCLUDED_TABLES.contains(tableName);
            }
        });
        interceptor.addInnerInterceptor(tenantInterceptor);

        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
