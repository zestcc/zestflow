package com.zestflow.admin.service;

import com.zestflow.admin.model.dto.*;
import com.zestflow.admin.model.vo.LoginVO;
import com.zestflow.admin.model.vo.UserVO;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {

    LoginVO login(LoginDTO dto);

    LoginVO register(RegisterDTO dto);

    void forgot(ForgotDTO dto);

    UserVO getUserInfo(Long userId);

    UserVO updateProfile(Long userId, UpdateProfileDTO dto);

    void updatePassword(Long userId, UpdatePasswordDTO dto);

    void forcePassword(Long userId, String newPassword);

    String uploadAvatar(Long userId, MultipartFile file);
}
