package com.zestflow.admin.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.zestflow.admin.util.SecurityUtils;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Value("${zestflow.admin.system-user:sys}")
    private String systemUser;

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
            } else {
                this.setFieldValByName("updatedBy", systemUser, metaObject);
            }
        } catch (Exception ignored) {
            this.setFieldValByName("updatedBy", systemUser, metaObject);
        }
    }
}
