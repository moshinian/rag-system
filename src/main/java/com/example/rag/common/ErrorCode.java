package com.example.rag.common;

import lombok.Getter;

@Getter
public enum ErrorCode {
    INVALID_ARGUMENT("INVALID_ARGUMENT", "Invalid request argument"),
    BUSINESS_ERROR("BUSINESS_ERROR", "Business rule violated"),
    INTERNAL_ERROR("INTERNAL_ERROR", "Internal server error");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
}
