package com.mini.novel.common.exception;

public final class ErrorCode {
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int BUSINESS_ERROR = 1000;
    public static final int VIP_REQUIRED = 2001;

    private ErrorCode() {
    }
}
