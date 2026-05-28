package com.zestflow.admin.model.dto;

import lombok.Data;

@Data
public class UserUpdateDTO {

    private String username;

    private String email;

    private Integer status;

    private Integer isSuperAdmin;
}
