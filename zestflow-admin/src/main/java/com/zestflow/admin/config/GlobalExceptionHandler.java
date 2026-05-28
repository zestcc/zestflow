package com.zestflow.admin.config;

import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.common.exception.BaseException;
import com.zestflow.common.exception.BizException;
import com.zestflow.common.exception.UnauthorizedException;
import com.zestflow.common.model.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e, HttpServletRequest request) {
        String resolvedMessage = resolveMessage(e.getErrorCode(), e.getArgs(), e.getMessage());
        log.warn("业务异常 errorCode={} uri={}", e.getErrorCode(), request.getRequestURI());
        return Result.fail(e.getCode(), e.getErrorCode(), resolvedMessage);
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleUnauthorizedException(UnauthorizedException e, HttpServletRequest request) {
        String resolvedMessage = resolveMessage(ErrorCode.UNAUTHORIZED, null, e.getMessage());
        log.warn("未授权 uri={}", request.getRequestURI());
        return Result.fail(e.getCode(), ErrorCode.UNAUTHORIZED, resolvedMessage);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e, HttpServletRequest request) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String field = fieldError != null ? fieldError.getField() : "";
        String code = fieldError != null ? fieldError.getCode() : "";

        // 尝试按 field 精确查找，回退到约束类型
        String resolvedMessage = resolveMessage(code + "." + field, null,
                resolveMessage(code, null,
                        fieldError != null ? fieldError.getDefaultMessage() : "Validation error"));

        log.warn("参数校验失败 field={} uri={}", field, request.getRequestURI());
        return Result.fail(400, ErrorCode.VALIDATION_ERROR, resolvedMessage);
    }

    @ExceptionHandler(BaseException.class)
    public Result<Void> handleBaseException(BaseException e, HttpServletRequest request) {
        log.error("系统异常 code={} uri={}", e.getCode(), request.getRequestURI(), e);
        return Result.fail(e.getCode(), ErrorCode.SERVER_ERROR, resolveMessage(ErrorCode.SERVER_ERROR, null, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("未预期异常 uri={}", request.getRequestURI(), e);
        return Result.fail(500, ErrorCode.SERVER_ERROR, resolveMessage(ErrorCode.SERVER_ERROR, null, null));
    }

    private String resolveMessage(String code, Object[] args, String defaultMessage) {
        try {
            return messageSource.getMessage(code, args, defaultMessage, LocaleContextHolder.getLocale());
        } catch (Exception ex) {
            return defaultMessage != null ? defaultMessage : code;
        }
    }
}
