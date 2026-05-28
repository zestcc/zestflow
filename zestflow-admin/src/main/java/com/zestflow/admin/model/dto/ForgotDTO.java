package com.zestflow.admin.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotDTO {

    @NotBlank
    @Email
    private String email;
}
