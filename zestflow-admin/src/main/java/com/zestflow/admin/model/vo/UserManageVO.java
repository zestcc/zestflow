package com.zestflow.admin.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserManageVO {

    private Long id;
    private String username;
    private String email;
    private String avatar;
    private Integer status;
    private Integer isSuperAdmin;
    private Integer mustChangePassword;
    private String generatedPassword;
    private List<ModuleRoleAssignmentVO> moduleRoles;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModuleRoleAssignmentVO {
        private Long moduleId;
        private String moduleCode;
        private String moduleName;
        private Long roleId;
        private String roleCode;
        private String roleName;
    }
}
