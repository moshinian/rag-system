package com.example.rag.common;

import lombok.Getter;

/**
 * 通用错误码定义。
 */
@Getter
public enum ErrorCode {
    INVALID_ARGUMENT("INVALID_ARGUMENT", "Invalid request argument"),
    BUSINESS_ERROR("BUSINESS_ERROR", "Business rule violated"),
    INTERNAL_ERROR("INTERNAL_ERROR", "Internal server error");

    private final String code;
    private final String defaultMessage;

    /** 初始化错误码及其默认消息。 */
    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
}
