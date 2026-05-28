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

    private Integer isSuperAdmin;
}
