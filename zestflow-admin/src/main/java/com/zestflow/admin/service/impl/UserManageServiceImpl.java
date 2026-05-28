package com.zestflow.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.model.dto.AssignModuleRoleDTO;
import com.zestflow.admin.model.dto.UserCreateDTO;
import com.zestflow.admin.model.dto.UserUpdateDTO;
import com.zestflow.admin.model.entity.ModulePO;
import com.zestflow.admin.model.entity.RolePO;
import com.zestflow.admin.model.entity.UserModuleRolePO;
import com.zestflow.admin.model.entity.UserPO;
import com.zestflow.admin.model.vo.UserManageVO;
import com.zestflow.admin.repository.ModuleMapper;
import com.zestflow.admin.repository.RoleMapper;
import com.zestflow.admin.repository.UserModuleRoleMapper;
import com.zestflow.admin.repository.UserMapper;
import com.zestflow.admin.service.UserManageService;
import com.zestflow.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final ModuleMapper moduleMapper;
    private final RoleMapper roleMapper;
    private final UserModuleRoleMapper userModuleRoleMapper;
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
        List<UserModuleRolePO> assignments = userModuleRoleMapper.selectList(
                new LambdaQueryWrapper<UserModuleRolePO>().in(UserModuleRolePO::getUserId, userIds)
        );
        Map<Long, List<UserModuleRolePO>> assignmentMap = assignments.stream()
                .collect(Collectors.groupingBy(UserModuleRolePO::getUserId));

        List<ModulePO> allModules = moduleMapper.selectList(null);
        Map<Long, ModulePO> moduleMap = allModules.stream()
                .collect(Collectors.toMap(ModulePO::getId, m -> m));

        List<RolePO> allRoles = roleMapper.selectList(null);
        Map<Long, RolePO> roleMap = allRoles.stream()
                .collect(Collectors.toMap(RolePO::getId, r -> r));

        return users.stream()
                .map(user -> toManageVO(user, assignmentMap.getOrDefault(user.getId(), Collections.emptyList()), moduleMap, roleMap))
                .collect(Collectors.toList());
    }

    @Override
    public UserManageVO getById(Long id) {
        UserPO user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        List<UserModuleRolePO> assignments = userModuleRoleMapper.selectList(
                new LambdaQueryWrapper<UserModuleRolePO>().eq(UserModuleRolePO::getUserId, id)
        );

        Map<Long, ModulePO> moduleMap = moduleMapper.selectList(null).stream()
                .collect(Collectors.toMap(ModulePO::getId, m -> m));
        Map<Long, RolePO> roleMap = roleMapper.selectList(null).stream()
                .collect(Collectors.toMap(RolePO::getId, r -> r));

        return toManageVO(user, assignments, moduleMap, roleMap);
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
        userModuleRoleMapper.delete(
                new LambdaQueryWrapper<UserModuleRolePO>().eq(UserModuleRolePO::getUserId, id)
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
    public void assignModuleRole(AssignModuleRoleDTO dto) {
        UserPO user = userMapper.selectById(dto.getUserId());
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        moduleMapper.selectById(dto.getModuleId());
        if (moduleMapper.selectById(dto.getModuleId()) == null) {
            throw new BizException(ErrorCode.MODULE_NOT_FOUND);
        }
        if (roleMapper.selectById(dto.getRoleId()) == null) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND);
        }

        UserModuleRolePO existing = userModuleRoleMapper.selectOne(
                new LambdaQueryWrapper<UserModuleRolePO>()
                        .eq(UserModuleRolePO::getUserId, dto.getUserId())
                        .eq(UserModuleRolePO::getModuleId, dto.getModuleId())
                        .last("LIMIT 1")
        );

        if (existing != null) {
            existing.setRoleId(dto.getRoleId());
            existing.setUpdatedAt(LocalDateTime.now());
            userModuleRoleMapper.updateById(existing);
            log.info("用户模块角色更新 userId={} moduleId={} roleId={}", dto.getUserId(), dto.getModuleId(), dto.getRoleId());
        } else {
            UserModuleRolePO assignment = new UserModuleRolePO();
            assignment.setUserId(dto.getUserId());
            assignment.setModuleId(dto.getModuleId());
            assignment.setRoleId(dto.getRoleId());
            assignment.setCreatedAt(LocalDateTime.now());
            assignment.setUpdatedAt(LocalDateTime.now());
            userModuleRoleMapper.insert(assignment);
            log.info("用户模块角色分配 userId={} moduleId={} roleId={}", dto.getUserId(), dto.getModuleId(), dto.getRoleId());
        }
    }

    @Override
    public void removeModuleRole(Long userId, Long moduleId) {
        userModuleRoleMapper.delete(
                new LambdaQueryWrapper<UserModuleRolePO>()
                        .eq(UserModuleRolePO::getUserId, userId)
                        .eq(UserModuleRolePO::getModuleId, moduleId)
        );
        log.info("用户模块角色移除 userId={} moduleId={}", userId, moduleId);
    }

    private String generateRandomPassword() {
        StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    private UserManageVO toManageVO(UserPO user, List<UserModuleRolePO> assignments,
                                     Map<Long, ModulePO> moduleMap, Map<Long, RolePO> roleMap) {
        List<UserManageVO.ModuleRoleAssignmentVO> roleVOs = assignments.stream()
                .map(a -> {
                    ModulePO module = moduleMap.get(a.getModuleId());
                    RolePO role = roleMap.get(a.getRoleId());
                    return UserManageVO.ModuleRoleAssignmentVO.builder()
                            .moduleId(a.getModuleId())
                            .moduleCode(module != null ? module.getCode() : null)
                            .moduleName(module != null ? module.getName() : null)
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
                .moduleRoles(roleVOs)
                .mustChangePassword(user.getMustChangePassword())
                .build();
    }
}
