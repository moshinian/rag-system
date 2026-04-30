package com.example.rag.common.exception;

import com.example.rag.common.ErrorCode;
import lombok.Getter;

/**
 * 业务异常。
 *
 * 用于表示可预期的业务失败场景。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /** 使用默认业务错误码构造异常。 */
    public BusinessException(String message) {
        this(ErrorCode.BUSINESS_ERROR, message);
    }

    /** 使用指定错误码构造异常。 */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
