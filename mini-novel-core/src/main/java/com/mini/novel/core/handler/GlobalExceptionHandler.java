package com.mini.novel.core.handler;

import com.mini.novel.common.exception.BusinessException;
import com.mini.novel.common.exception.ErrorCode;
import com.mini.novel.common.result.Result;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case ErrorCode.UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case ErrorCode.FORBIDDEN, ErrorCode.VIP_REQUIRED -> HttpStatus.FORBIDDEN;
            case ErrorCode.NOT_FOUND -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.OK;
        };
        return ResponseEntity.status(status).body(Result.fail(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValidationException(Exception ex) {
        return Result.fail(ErrorCode.BUSINESS_ERROR, "参数校验失败");
    }

    /**
     * 唯一键冲突兜底：写接口未做幂等校验时，重复提交/重复命名会抛 DuplicateKeyException，
     * 默认会以 500 返回（如 subscribe_channel_novel.uk_channel_novel）。
     * 这里统一转为业务失败提示，避免把数据库约束暴露成服务端错误。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKeyException(DuplicateKeyException ex) {
        return Result.fail(ErrorCode.BUSINESS_ERROR, "数据已存在，请勿重复提交");
    }
}
