package com.zestflow.admin.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.zestflow.admin.service.TenantAppContext;
import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
@RequiredArgsConstructor
public class MybatisPlusConfig {

    /** 租户过滤排除表 — 这些表不自动注入 tenant_id 条件 */
    private static final Set<String> EXCLUDED_TABLES = Set.of(
            "tenant", "user_tenant", "role", "tenant_ip_mapping"
    );

    private final TenantAppContext tenantAppContext;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        TenantLineInnerInterceptor tenantInterceptor = new TenantLineInnerInterceptor();
        tenantInterceptor.setTenantLineHandler(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                Long tenantId = tenantAppContext.getCurrentTenantId();
                return new LongValue(tenantId != null ? tenantId : 1L);
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
