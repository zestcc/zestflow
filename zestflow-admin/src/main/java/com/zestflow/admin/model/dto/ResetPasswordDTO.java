package com.zestflow.admin.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重置密码请求 DTO
 */
@Data
public class ResetPasswordDTO {

    @NotBlank(message = "重置令牌不能为空")
    private String token;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度至少6位")
    private String password;
}
