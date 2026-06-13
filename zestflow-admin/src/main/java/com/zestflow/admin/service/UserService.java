package com.zestflow.admin.service;

import com.zestflow.admin.model.dto.*;
import com.zestflow.admin.model.vo.LoginVO;
import com.zestflow.admin.model.vo.UserVO;
import io.jsonwebtoken.Claims;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {

    LoginVO login(LoginDTO dto);

    LoginVO loginBySso(String providerId, Claims claims);

    LoginVO register(RegisterDTO dto);

    void forgot(ForgotDTO dto);

    void resetPassword(ResetPasswordDTO dto);

    void verifyEmail(String token);

    UserVO getUserInfo(Long userId);

    UserVO updateProfile(Long userId, UpdateProfileDTO dto);

    void updatePassword(Long userId, UpdatePasswordDTO dto);

    void forcePassword(Long userId, String newPassword);

    String uploadAvatar(Long userId, MultipartFile file);
}
