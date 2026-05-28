package com.zestflow.common.model;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private int code;
    private String message;
    private String errorCode;
    private T data;

    private Result() {}

    private Result(int code, String message, String errorCode, T data) {
        this.code = code;
        this.message = message;
        this.errorCode = errorCode;
        this.data = data;
    }

    public static <T> Result<T> success() {
        return new Result<>(200, "success", null, null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", null, data);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null, null);
    }

    public static <T> Result<T> fail(int code, String errorCode, String message) {
        return new Result<>(code, message, errorCode, null);
    }
}
