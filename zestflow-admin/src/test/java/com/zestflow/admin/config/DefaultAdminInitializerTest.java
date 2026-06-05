package com.zestflow.admin.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.model.entity.UserPO;
import com.zestflow.admin.repository.UserMapper;
import com.zestflow.admin.repository.UserTenantMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class DefaultAdminInitializerTest {

    @Mock private DefaultAdminProperties defaultAdminProperties;
    @Mock private UserMapper userMapper;
    @Mock private UserTenantMapper userTenantMapper;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private Environment environment;

    private DefaultAdminInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new DefaultAdminInitializer(
                defaultAdminProperties, userMapper, userTenantMapper, jdbcTemplate, passwordEncoder, environment);
        when(defaultAdminProperties.getUsername()).thenReturn("admin");
        when(defaultAdminProperties.getPassword()).thenReturn("admin123");
        when(defaultAdminProperties.getEmail()).thenReturn("admin@example.com");
        when(environment.getActiveProfiles()).thenReturn(new String[]{"local"});
    }

    @Test
    void whenAdminExists_shouldBindDefaultTenants() {
        UserPO existing = new UserPO();
        existing.setId(1L);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(userTenantMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(jdbcTemplate.update(anyString(), anyLong(), anyLong())).thenReturn(1);

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(jdbcTemplate, times(2)).update(anyString(), anyLong(), anyLong());
    }

    @Test
    void whenAdminCreated_shouldBindDefaultTenants() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(passwordEncoder.encode("admin123")).thenReturn("encoded");
        when(userTenantMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(jdbcTemplate.update(anyString(), anyLong(), anyLong())).thenReturn(1);
        doAnswer(invocation -> {
            UserPO user = invocation.getArgument(0);
            user.setId(99L);
            return 1;
        }).when(userMapper).insert(any(UserPO.class));

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(jdbcTemplate, times(2)).update(anyString(), anyLong(), anyLong());
    }
}
