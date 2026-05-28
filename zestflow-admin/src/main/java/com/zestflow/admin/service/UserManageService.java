package com.zestflow.admin.service;

import com.zestflow.admin.model.dto.AssignModuleRoleDTO;
import com.zestflow.admin.model.dto.UserCreateDTO;
import com.zestflow.admin.model.dto.UserUpdateDTO;
import com.zestflow.admin.model.vo.UserManageVO;

import java.util.List;

public interface UserManageService {

    List<UserManageVO> listAll();

    UserManageVO getById(Long id);

    UserManageVO create(UserCreateDTO dto);

    UserManageVO update(Long id, UserUpdateDTO dto);

    void delete(Long id);

    String resetPassword(Long id);

    void assignModuleRole(AssignModuleRoleDTO dto);

    void removeModuleRole(Long userId, Long moduleId);
}
