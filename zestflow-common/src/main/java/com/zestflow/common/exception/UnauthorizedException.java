package com.zestflow.common.exception;

import java.io.Serial;

public class UnauthorizedException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public UnauthorizedException(String message) {
        super(401, message);
    }

    public UnauthorizedException() {
        super(401, "未登录或登录已过期");
    }
}
