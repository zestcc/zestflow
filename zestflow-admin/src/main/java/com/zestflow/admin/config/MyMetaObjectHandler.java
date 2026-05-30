package com.zestflow.admin.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.zestflow.admin.util.SecurityUtils;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime::now, LocalDateTime.class);
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime::now, LocalDateTime.class);
        fillUpdatedBy(metaObject);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime::now, LocalDateTime.class);
        fillUpdatedBy(metaObject);
    }

    private void fillUpdatedBy(MetaObject metaObject) {
        try {
            String username = SecurityUtils.getCurrentUsername();
            if (username != null) {
                this.setFieldValByName("updatedBy", username, metaObject);
            }
        } catch (Exception ignored) {
            // 无登录上下文（如 Executor 注册心跳），不设置更新人
        }
    }
}
