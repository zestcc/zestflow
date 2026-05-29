package com.zestflow.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "zestflow.admin.default-user")
public class DefaultAdminProperties {

    private String username = "admin";

    private String password = "admin123";

    private String email;
}
