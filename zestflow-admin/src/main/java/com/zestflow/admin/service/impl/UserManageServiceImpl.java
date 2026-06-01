package com.zestflow.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zestflow.admin.constant.ErrorCode;

import com.zestflow.admin.model.dto.UserCreateDTO;
import com.zestflow.admin.model.dto.UserUpdateDTO;
import com.zestflow.admin.model.entity.RolePO;
import com.zestflow.admin.model.entity.UserAppRolePO;
import com.zestflow.admin.model.entity.UserPO;
import com.zestflow.admin.model.vo.UserManageVO;
import com.zestflow.admin.repository.RoleMapper;
import com.zestflow.admin.repository.UserAppRoleMapper;
import com.zestflow.admin.repository.UserMapper;
import com.zestflow.admin.service.UserManageService;
import com.zestflow.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserManageServiceImpl implements UserManageService {

    private final UserMapper userMapper;
    private final UserAppRoleMapper userAppRoleMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;

    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final int PASSWORD_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public List<UserManageVO> listAll() {
        List<UserPO> users = userMapper.selectList(null);
        if (users.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> userIds = users.stream().map(UserPO::getId).collect(Collectors.toList());
        List<UserAppRolePO> assignments = userAppRoleMapper.selectList(
                new LambdaQueryWrapper<UserAppRolePO>().in(UserAppRolePO::getUserId, userIds)
        );
        Map<Long, List<UserAppRolePO>> assignmentMap = assignments.stream()
                .collect(Collectors.groupingBy(UserAppRolePO::getUserId));

        List<RolePO> allRoles = roleMapper.selectList(null);
        Map<Long, RolePO> roleMap = allRoles.stream()
                .collect(Collectors.toMap(RolePO::getId, r -> r));

        return users.stream()
                .map(user -> toManageVO(user, assignmentMap.getOrDefault(user.getId(), Collections.emptyList()), roleMap))
                .collect(Collectors.toList());
    }

    @Override
    public IPage<UserManageVO> listPage(String username, String email, Integer status, Integer isSuperAdmin, int page, int size) {
        LambdaQueryWrapper<UserPO> wrapper = new LambdaQueryWrapper<UserPO>()
                .eq(username != null && !username.isEmpty(), UserPO::getUsername, username)
                .eq(email != null && !email.isEmpty(), UserPO::getEmail, email)
                .eq(status != null, UserPO::getStatus, status)
                .eq(isSuperAdmin != null, UserPO::getIsSuperAdmin, isSuperAdmin);

        Page<UserPO> poPage = userMapper.selectPage(new Page<>(page, size), wrapper);
        if (poPage.getRecords().isEmpty()) {
            return poPage.convert(u -> null);
        }

        List<Long> userIds = poPage.getRecords().stream().map(UserPO::getId).collect(Collectors.toList());
        List<UserAppRolePO> assignments = userAppRoleMapper.selectList(
                new LambdaQueryWrapper<UserAppRolePO>().in(UserAppRolePO::getUserId, userIds)
        );
        Map<Long, List<UserAppRolePO>> assignmentMap = assignments.stream()
                .collect(Collectors.groupingBy(UserAppRolePO::getUserId));

        List<RolePO> allRoles = roleMapper.selectList(null);
        Map<Long, RolePO> roleMap = allRoles.stream()
                .collect(Collectors.toMap(RolePO::getId, r -> r));

        return poPage.convert(user -> toManageVO(user, assignmentMap.getOrDefault(user.getId(), Collections.emptyList()), roleMap));
    }

    @Override
    public UserManageVO getById(Long id) {
        UserPO user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        List<UserAppRolePO> assignments = userAppRoleMapper.selectList(
                new LambdaQueryWrapper<UserAppRolePO>().eq(UserAppRolePO::getUserId, id)
        );

        Map<Long, RolePO> roleMap = roleMapper.selectList(null).stream()
                .collect(Collectors.toMap(RolePO::getId, r -> r));

        return toManageVO(user, assignments, roleMap);
    }

    @Override
    public UserManageVO create(UserCreateDTO dto) {
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

        String rawPassword = generateRandomPassword();

        UserPO user = new UserPO();
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setStatus(1);
        user.setIsSuperAdmin(dto.getIsSuperAdmin() != null ? dto.getIsSuperAdmin() : 0);
        user.setMustChangePassword(1);

        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);

        log.info("用户创建成功 userId={} username={} isSuperAdmin={}", user.getId(), user.getUsername(), user.getIsSuperAdmin());
        UserManageVO vo = getById(user.getId());
        vo.setGeneratedPassword(rawPassword);
        return vo;
    }

    @Override
    public UserManageVO update(Long id, UserUpdateDTO dto) {
        UserPO user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        if (dto.getUsername() != null) {
            Long count = userMapper.selectCount(
                    new LambdaQueryWrapper<UserPO>()
                            .eq(UserPO::getUsername, dto.getUsername())
                            .ne(UserPO::getId, id)
            );
            if (count > 0) {
                throw new BizException(ErrorCode.USERNAME_EXISTS);
            }
            user.setUsername(dto.getUsername());
        }

        if (dto.getEmail() != null) {
            Long count = userMapper.selectCount(
                    new LambdaQueryWrapper<UserPO>()
                            .eq(UserPO::getEmail, dto.getEmail())
                            .ne(UserPO::getId, id)
            );
            if (count > 0) {
                throw new BizException(ErrorCode.EMAIL_EXISTS);
            }
            user.setEmail(dto.getEmail());
        }

        if (dto.getStatus() != null) user.setStatus(dto.getStatus());
        if (dto.getIsSuperAdmin() != null) user.setIsSuperAdmin(dto.getIsSuperAdmin());

        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("用户更新成功 userId={}", id);
        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        UserPO user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        userAppRoleMapper.delete(
                new LambdaQueryWrapper<UserAppRolePO>().eq(UserAppRolePO::getUserId, id)
        );
        userMapper.deleteById(id);
        log.info("用户删除成功 userId={} username={}", id, user.getUsername());
    }

    @Override
    public String resetPassword(Long id) {
        UserPO user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        String rawPassword = generateRandomPassword();
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setMustChangePassword(1);

        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        log.info("用户密码重置成功 userId={}", id);
        return rawPassword;
    }

    @Override
    @CacheEvict(value = "permissions", allEntries = true)
    public void assignAppRole(Long userId, String appCode, Long roleId) {
        UserPO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        if (roleMapper.selectById(roleId) == null) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND);
        }

        UserAppRolePO existing = userAppRoleMapper.selectOne(
                new LambdaQueryWrapper<UserAppRolePO>()
                        .eq(UserAppRolePO::getUserId, userId)
                        .eq(UserAppRolePO::getAppCode, appCode)
                        .last("LIMIT 1")
        );

        if (existing != null) {
            existing.setRoleId(roleId);
            existing.setUpdatedAt(LocalDateTime.now());
            userAppRoleMapper.updateById(existing);
            log.info("用户应用角色更新 userId={} appCode={} roleId={}", userId, appCode, roleId);
        } else {
            UserAppRolePO assignment = new UserAppRolePO();
            assignment.setUserId(userId);
            assignment.setAppCode(appCode);
            assignment.setRoleId(roleId);
            assignment.setCreatedAt(LocalDateTime.now());
            assignment.setUpdatedAt(LocalDateTime.now());
            userAppRoleMapper.insert(assignment);
            log.info("用户应用角色分配 userId={} appCode={} roleId={}", userId, appCode, roleId);
        }
    }

    @Override
    @CacheEvict(value = "permissions", allEntries = true)
    public void removeAppRole(Long userId, String appCode) {
        userAppRoleMapper.delete(
                new LambdaQueryWrapper<UserAppRolePO>()
                        .eq(UserAppRolePO::getUserId, userId)
                        .eq(UserAppRolePO::getAppCode, appCode)
        );
        log.info("用户应用角色移除 userId={} appCode={}", userId, appCode);
    }

    private String generateRandomPassword() {
        StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    private UserManageVO toManageVO(UserPO user, List<UserAppRolePO> assignments,
                                     Map<Long, RolePO> roleMap) {
        List<UserManageVO.AppRoleAssignmentVO> roleVOs = assignments.stream()
                .map(a -> {
                    RolePO role = roleMap.get(a.getRoleId());
                    return UserManageVO.AppRoleAssignmentVO.builder()
                            .appCode(a.getAppCode())
                            .roleId(a.getRoleId())
                            .roleCode(role != null ? role.getCode() : null)
                            .roleName(role != null ? role.getName() : null)
                            .build();
                })
                .collect(Collectors.toList());

        return UserManageVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .avatar(user.getAvatar())
                .status(user.getStatus())
                .isSuperAdmin(user.getIsSuperAdmin())
                .appRoles(roleVOs)
                .mustChangePassword(user.getMustChangePassword())
                .updatedBy(user.getUpdatedBy())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
