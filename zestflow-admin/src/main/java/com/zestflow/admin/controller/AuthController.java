package com.zestflow.admin.controller;

import com.zestflow.admin.config.TenantContextHolder;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.config.LoginRateLimiter;
import com.zestflow.admin.model.dto.*;
import com.zestflow.admin.model.vo.LoginVO;
import com.zestflow.admin.model.vo.TenantSimpleVO;
import com.zestflow.admin.model.vo.UserVO;
import com.zestflow.admin.service.TenantService;
import com.zestflow.admin.service.UserService;
import com.zestflow.admin.util.JwtUtils;
import com.zestflow.admin.util.SecurityUtils;
import com.zestflow.common.exception.BizException;
import com.zestflow.common.model.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final LoginRateLimiter loginRateLimiter;
    private final TenantService tenantService;
    private final JwtUtils jwtUtils;

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest request) {
        String ip = getClientIp(request);
        loginRateLimiter.check(ip);
        try {
            LoginVO vo = userService.login(dto);
            loginRateLimiter.reset(ip);
            return Result.success(vo);
        } catch (Exception e) {
            loginRateLimiter.recordFailure(ip);
            throw e;
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) return xf.split(",")[0].trim();
        String xr = request.getHeader("X-Real-IP");
        if (xr != null && !xr.isBlank()) return xr;
        return request.getRemoteAddr();
    }

    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterDTO dto) {
        LoginVO vo = userService.register(dto);
        return Result.success(vo);
    }

    @PostMapping("/forgot")
    public Result<Void> forgot(@Valid @RequestBody ForgotDTO dto) {
        userService.forgot(dto);
        return Result.success();
    }

    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordDTO dto) {
        userService.resetPassword(dto);
        return Result.success();
    }

    @GetMapping("/verify-email")
    public Result<Void> verifyEmail(@RequestParam String token) {
        userService.verifyEmail(token);
        return Result.success();
    }

    @GetMapping("/userinfo")
    public Result<UserVO> getUserInfo(Authentication authentication) {
        Long userId = SecurityUtils.getUserId(authentication);
        UserVO vo = userService.getUserInfo(userId);
        return Result.success(vo);
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.success();
    }

    @PutMapping("/profile")
    public Result<UserVO> updateProfile(@Valid @RequestBody UpdateProfileDTO dto, Authentication authentication) {
        Long userId = SecurityUtils.getUserId(authentication);
        UserVO vo = userService.updateProfile(userId, dto);
        return Result.success(vo);
    }

    @PutMapping("/password")
    public Result<Void> updatePassword(@Valid @RequestBody UpdatePasswordDTO dto, Authentication authentication) {
        Long userId = SecurityUtils.getUserId(authentication);
        userService.updatePassword(userId, dto);
        return Result.success();
    }

    @PutMapping("/force-password")
    public Result<Void> forcePassword(@RequestBody Map<String, String> body, Authentication authentication) {
        Long userId = SecurityUtils.getUserId(authentication);
        String newPassword = body.get("newPassword");
        if (newPassword == null || newPassword.length() < 6) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "密码至少6位");
        }
        userService.forcePassword(userId, newPassword);
        return Result.success();
    }

    /**
     * 获取当前用户可访问的租户列表
     */
    @GetMapping("/tenants")
    public Result<List<TenantSimpleVO>> getUserTenants(Authentication authentication) {
        Long userId = SecurityUtils.getUserId(authentication);
        List<TenantSimpleVO> tenants = tenantService.listUserTenants(userId);
        return Result.success(tenants);
    }

    /**
     * 切换到指定租户（返回新 JWT）
     */
    @PostMapping("/switch-tenant/{id}")
    public Result<LoginVO> switchTenant(@PathVariable Long id, Authentication authentication) {
        Long userId = SecurityUtils.getUserId(authentication);
        String username = authentication.getPrincipal() instanceof String s ? s : null;
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin(authentication);

        tenantService.switchTenant(userId, id);
        String newToken = jwtUtils.generateToken(userId, username, isSuperAdmin, id);

        UserVO userVO = userService.getUserInfo(userId);
        List<TenantSimpleVO> tenants = tenantService.listUserTenants(userId);
        TenantSimpleVO currentTenant = TenantSimpleVO.builder()
                .id(id)
                .current(true)
                .build();
        // 填充当前租户名称
        for (TenantSimpleVO t : tenants) {
            if (t.getId().equals(id)) {
                currentTenant.setName(t.getName());
                currentTenant.setCode(t.getCode());
                currentTenant.setTenantAdmin(t.isTenantAdmin());
                break;
            }
        }

        return Result.success(LoginVO.builder()
                .token(newToken)
                .user(userVO)
                .tenants(tenants)
                .currentTenant(currentTenant)
                .build());
    }

    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file, Authentication authentication) {
        Long userId = SecurityUtils.getUserId(authentication);
        String avatarPath = userService.uploadAvatar(userId, file);
        return Result.success(avatarPath);
    }
}
