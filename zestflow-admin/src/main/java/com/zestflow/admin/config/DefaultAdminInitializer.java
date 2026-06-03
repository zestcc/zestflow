package com.zestflow.admin.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.model.entity.UserPO;
import com.zestflow.admin.repository.UserMapper;
import com.zestflow.common.util.ProductionSecretGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultAdminInitializer implements ApplicationRunner {

    private final DefaultAdminProperties defaultAdminProperties;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        String username = defaultAdminProperties.getUsername();
        String password = defaultAdminProperties.getPassword();
        String email = defaultAdminProperties.getEmail();
        boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");

        if (username == null || username.isBlank()) {
            log.warn("默认管理员用户名为空，跳过自动创建");
            return;
        }

        if (prod && ProductionSecretGuard.isWeakAdminPassword(password)) {
            throw new IllegalStateException(
                    "[prod] default-user.password 禁止使用 admin123 或模板占位符，请配置强口令");
        }

        UserPO existing = userMapper.selectOne(
                new LambdaQueryWrapper<UserPO>()
                        .eq(UserPO::getUsername, username)
                        .last("LIMIT 1")
        );
        if (existing != null) {
            log.debug("默认管理员用户已存在 username={}", username);
            return;
        }

        UserPO user = new UserPO();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setStatus(1);
        user.setIsSuperAdmin(1);
        user.setMustChangePassword(prod ? 1 : 0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);

        log.info("默认管理员用户创建成功 username={} id={} mustChangePassword={}",
                username, user.getId(), user.getMustChangePassword());
    }
}
