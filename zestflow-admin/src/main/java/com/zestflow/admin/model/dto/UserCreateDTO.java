package com.zestflow.admin.model.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class UserCreateDTO {

    @NotBlank
    @Size(min = 2, max = 50)
    private String username;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 6, max = 100)
    private String password;

    private Integer isSuperAdmin;
}
