package com.mini.novel.common.result;

public record Result<T>(int code, String message, T data) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "success", data);
    }

    public static Result<Void> ok() {
        return new Result<>(0, "success", null);
    }

    public static Result<Void> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}
