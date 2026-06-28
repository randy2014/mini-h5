package com.mini.novel.core.handler;

import com.mini.novel.common.exception.BusinessException;
import com.mini.novel.common.exception.ErrorCode;
import com.mini.novel.common.result.Result;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException ex) {
        return Result.fail(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValidationException(Exception ex) {
        return Result.fail(ErrorCode.BUSINESS_ERROR, "参数校验失败");
    }
}
