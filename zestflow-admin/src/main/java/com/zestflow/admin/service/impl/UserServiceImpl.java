package com.zestflow.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.config.TenantContextHolder;
import com.zestflow.admin.model.dto.*;
import com.zestflow.admin.model.entity.UserTenantPO;
import com.zestflow.admin.model.entity.UserPO;
import com.zestflow.admin.model.vo.LoginVO;
import com.zestflow.admin.model.vo.TenantSimpleVO;
import com.zestflow.admin.model.vo.UserVO;
import com.zestflow.admin.repository.UserMapper;
import com.zestflow.admin.repository.UserTenantMapper;
import com.zestflow.admin.service.TenantService;
import com.zestflow.admin.service.UserService;
import com.zestflow.admin.util.JwtUtils;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final TenantService tenantService;
    private final UserTenantMapper userTenantMapper;

    @Value("${zestflow.upload.avatar-dir:uploads/avatars}")
    private String avatarDir;

    @Override
    public LoginVO login(LoginDTO dto) {
        // 登录需忽略租户过滤 — 先找到用户再确定其租户
        UserPO user = userMapper.findByUsername(dto.getUsername());

        if (user == null) {
            throw new BizException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }

        // 获取用户租户列表和默认租户
        List<TenantSimpleVO> tenants = tenantService.listUserTenants(user.getId());
        TenantSimpleVO defaultTenant = tenantService.getDefaultTenant(user.getId());
        Long currentTenantId = defaultTenant != null ? defaultTenant.getId() : 1L;

        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getIsSuperAdmin() == 1, currentTenantId);
        UserVO userVO = toUserVO(user);
        userVO.setTenants(tenants);
        return LoginVO.builder()
                .token(token)
                .user(userVO)
                .tenants(tenants)
                .currentTenant(defaultTenant)
                .build();
    }

    @Override
    public LoginVO register(RegisterDTO dto) {
        Long countByUsername = userMapper.selectCount(
                new LambdaQueryWrapper<UserPO>().eq(UserPO::getUsername, dto.getUsername())
        );
        if (countByUsername > 0) {
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }

        Long countByEmail = userMapper.selectCount(
                new LambdaQueryWrapper<UserPO>().eq(UserPO::getEmail, dto.getEmail())
        );
        if (countByEmail > 0) {
            throw new BizException(ErrorCode.EMAIL_EXISTS);
        }

        UserPO user = new UserPO();
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);

        // 新注册用户绑定到租户1
        UserTenantPO ut = new UserTenantPO();
        ut.setUserId(user.getId());
        ut.setTenantId(1L);
        ut.setIsTenantAdmin(0);
        ut.setCreatedAt(LocalDateTime.now());
        userTenantMapper.insert(ut);

        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), false, 1L);
        UserVO userVO = toUserVO(user);
        return LoginVO.builder().token(token).user(userVO).build();
    }

    @Override
    public void forgot(ForgotDTO dto) {
        UserPO user = userMapper.selectOne(
                new LambdaQueryWrapper<UserPO>()
                        .eq(UserPO::getEmail, dto.getEmail())
                        .last("LIMIT 1")
        );

        if (user == null) {
            log.info("找回密码：邮箱 {} 未注册", dto.getEmail());
            return;
        }

        String resetToken = UUID.randomUUID().toString().replace("-", "");
        user.setResetToken(resetToken);
        user.setResetTokenExpiry(LocalDateTime.now().plusHours(1));
        userMapper.updateById(user);

        log.info("找回密码: userId={} email={} resetToken={}", user.getId(), dto.getEmail(), resetToken);
    }

    @Override
    public UserVO updateProfile(Long userId, UpdateProfileDTO dto) {
        UserPO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        Long countByUsername = userMapper.selectCount(
                new LambdaQueryWrapper<UserPO>()
                        .eq(UserPO::getUsername, dto.getUsername())
                        .ne(UserPO::getId, userId)
        );
        if (countByUsername > 0) {
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }

        Long countByEmail = userMapper.selectCount(
                new LambdaQueryWrapper<UserPO>()
                        .eq(UserPO::getEmail, dto.getEmail())
                        .ne(UserPO::getId, userId)
        );
        if (countByEmail > 0) {
            throw new BizException(ErrorCode.EMAIL_EXISTS);
        }

        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("用户信息更新 userId={} username={} email={}", userId, dto.getUsername(), dto.getEmail());
        return toUserVO(user);
    }

    @Override
    public void updatePassword(Long userId, UpdatePasswordDTO dto) {
        UserPO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.PASSWORD_INCORRECT);
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        user.setMustChangePassword(0);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("密码修改成功 userId={}", userId);
    }

    @Override
    public void forcePassword(Long userId, String newPassword) {
        UserPO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BizException(ErrorCode.PASSWORD_SAME_AS_OLD, "新密码不能与当前密码相同");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(0);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        log.info("强制密码修改成功 userId={}", userId);
    }

    @Override
    public String uploadAvatar(Long userId, MultipartFile file) {
        UserPO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        String ext = "png";
        String originalName = file.getOriginalFilename();
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf(".") + 1);
        }

        try {
            String baseDir = System.getProperty("user.dir");
            Path dir = Paths.get(baseDir, avatarDir).toAbsolutePath();
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            String fileName = userId + "_" + System.currentTimeMillis() + "." + ext;
            Path targetPath = dir.resolve(fileName);
            file.transferTo(targetPath.toFile());

            String avatarPath = "/uploads/avatars/" + fileName;
            user.setAvatar(avatarPath);
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.updateById(user);

            log.info("头像上传成功 userId={} path={}", userId, targetPath);
            return avatarPath;
        } catch (IOException e) {
            log.error("头像上传失败 userId={}", userId, e);
            throw new BizException(ErrorCode.SERVER_ERROR);
        }
    }

    @Override
    public UserVO getUserInfo(Long userId) {
        UserPO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        return toUserVO(user);
    }

    private UserVO toUserVO(UserPO user) {
        List<TenantSimpleVO> tenants = tenantService.listUserTenants(user.getId());
        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .avatar(user.getAvatar())
                .isSuperAdmin(user.getIsSuperAdmin())
                .mustChangePassword(user.getMustChangePassword())
                .tenants(tenants)
                .build();
    }
}
