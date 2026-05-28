package com.zestflow.common.exception;

import lombok.Getter;

import java.io.Serial;

@Getter
public class BizException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String errorCode;
    private final transient Object[] args;

    public BizException(String errorCode, String defaultMessage) {
        super(400, defaultMessage);
        this.errorCode = errorCode;
        this.args = null;
    }

    public BizException(String errorCode) {
        super(400, errorCode);
        this.errorCode = errorCode;
        this.args = null;
    }

    public BizException(String errorCode, Object[] args) {
        super(400, errorCode);
        this.errorCode = errorCode;
        this.args = args;
    }
}
