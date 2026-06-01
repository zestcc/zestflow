package com.zestflow.admin.config;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.service.TenantAppContext;
import com.zestflow.admin.util.SecurityUtils;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyMetaObjectHandlerTest {

    @Mock private Authentication authentication;
    @Mock private TenantAppContext tenantAppContext;

    private MyMetaObjectHandler handler;

    @BeforeEach
    void setUp() {
        lenient().when(tenantAppContext.getCurrentTenantId()).thenReturn(1L);
        handler = new MyMetaObjectHandler(tenantAppContext);
        // 用反射设置 systemUser 字段，避免 @Value 依赖
        try {
            java.lang.reflect.Field field = MyMetaObjectHandler.class.getDeclaredField("systemUser");
            field.setAccessible(true);
            field.set(handler, "sys");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // 注册 ExecutorRegistryPO 的 TableInfo，供 strictInsertFill/strictUpdateFill 使用
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test-resource");
        assistant.setCurrentNamespace(ExecutorRegistryPO.class.getName());
        TableInfoHelper.initTableInfo(assistant, ExecutorRegistryPO.class);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void insertFill_withAuth_setsUsername() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.getPrincipal()).thenReturn("admin");
        when(authentication.isAuthenticated()).thenReturn(true);

        ExecutorRegistryPO po = new ExecutorRegistryPO();
        MetaObject metaObject = SystemMetaObject.forObject(po);
        handler.insertFill(metaObject);

        assertThat(po.getCreatedBy()).isEqualTo("admin");
        assertThat(po.getUpdatedBy()).isEqualTo("admin");
        assertThat(po.getCreatedAt()).isNotNull();
        assertThat(po.getUpdatedAt()).isNotNull();
    }

    @Test
    void insertFill_withoutAuth_fallsBackToSystemUser() {
        SecurityContextHolder.getContext().setAuthentication(null);

        ExecutorRegistryPO po = new ExecutorRegistryPO();
        MetaObject metaObject = SystemMetaObject.forObject(po);
        handler.insertFill(metaObject);

        assertThat(po.getCreatedBy()).isEqualTo("sys");
        assertThat(po.getUpdatedBy()).isEqualTo("sys");
    }

    @Test
    void updateFill_withAuth_setsUpdatedBy() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.getPrincipal()).thenReturn("admin");
        when(authentication.isAuthenticated()).thenReturn(true);

        ExecutorRegistryPO po = new ExecutorRegistryPO();
        MetaObject metaObject = SystemMetaObject.forObject(po);
        handler.updateFill(metaObject);

        assertThat(po.getUpdatedBy()).isEqualTo("admin");
        assertThat(po.getUpdatedAt()).isNotNull();
    }

    @Test
    void updateFill_withoutAuth_fallsBackToSystemUser() {
        SecurityContextHolder.getContext().setAuthentication(null);

        ExecutorRegistryPO po = new ExecutorRegistryPO();
        MetaObject metaObject = SystemMetaObject.forObject(po);
        handler.updateFill(metaObject);

        assertThat(po.getUpdatedBy()).isEqualTo("sys");
        assertThat(po.getUpdatedAt()).isNotNull();
    }

    @Test
    void insertFill_setsCreatedAtAndUpdatedAt() {
        ExecutorRegistryPO po = new ExecutorRegistryPO();
        MetaObject metaObject = SystemMetaObject.forObject(po);
        handler.insertFill(metaObject);

        assertThat(po.getCreatedAt()).isNotNull();
        assertThat(po.getUpdatedAt()).isNotNull();
    }

    @Test
    void updateFill_setsUpdatedAt() {
        ExecutorRegistryPO po = new ExecutorRegistryPO();
        MetaObject metaObject = SystemMetaObject.forObject(po);
        handler.updateFill(metaObject);

        assertThat(po.getUpdatedAt()).isNotNull();
    }
}
