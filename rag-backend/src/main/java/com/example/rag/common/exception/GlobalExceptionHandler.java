package com.example.rag.common.exception;

import com.example.rag.common.ApiResponse;
import com.example.rag.common.ErrorCode;
import com.example.rag.common.logging.StructuredLogMessage;
import com.example.rag.config.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 处理参数校验异常。 */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiResponse<Void>> handleValidationException(Exception ex, HttpServletRequest request) {
        log.warn(StructuredLogMessage.of("http.request.validation_failed")
                .field("requestId", requestId(request))
                .field("path", request.getRequestURI())
                .field("method", request.getMethod())
                .field("exception", ex.getClass().getSimpleName())
                .field("message", ex.getMessage())
                .build());
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(ErrorCode.INVALID_ARGUMENT.getCode(), ex.getMessage(), requestId(request)));
    }

    /** 处理业务异常。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        log.warn(StructuredLogMessage.of("http.request.business_failed")
                .field("requestId", requestId(request))
                .field("path", request.getRequestURI())
                .field("method", request.getMethod())
                .field("errorCode", ex.getErrorCode().getCode())
                .field("message", ex.getMessage())
                .build());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(ex.getErrorCode().getCode(), ex.getMessage(), requestId(request)));
    }

    /** 处理未预期异常。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception ex, HttpServletRequest request) {
        log.error(StructuredLogMessage.of("http.request.unexpected_failed")
                .field("requestId", requestId(request))
                .field("path", request.getRequestURI())
                .field("method", request.getMethod())
                .field("exception", ex.getClass().getSimpleName())
                .field("message", ex.getMessage())
                .build(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(ErrorCode.INTERNAL_ERROR.getCode(), ex.getMessage(), requestId(request)));
    }

    /** 从请求上下文中提取 requestId。 */
    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return value == null ? "unknown" : value.toString();
    }
}
