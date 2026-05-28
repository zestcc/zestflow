package com.zestflow.admin.controller;

import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.model.dto.*;
import com.zestflow.admin.model.vo.LoginVO;
import com.zestflow.admin.model.vo.UserVO;
import com.zestflow.admin.service.UserService;
import com.zestflow.admin.util.SecurityUtils;
import com.zestflow.common.exception.BizException;
import com.zestflow.common.model.Result;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        LoginVO vo = userService.login(dto);
        return Result.success(vo);
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

    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file, Authentication authentication) {
        Long userId = SecurityUtils.getUserId(authentication);
        String avatarPath = userService.uploadAvatar(userId, file);
        return Result.success(avatarPath);
    }
}
