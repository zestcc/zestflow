package com.zestflow.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.common.model.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 未登录访问受保护 Admin API 时统一返回 HTTP 401 + AUTH_UNAUTHORIZED（对标 REST 惯例，修复 BB-01）。
 */
@Component
@RequiredArgsConstructor
public class JwtUnauthorizedEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Result<Void> body = Result.fail(401, ErrorCode.UNAUTHORIZED, "Not logged in or session expired");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
