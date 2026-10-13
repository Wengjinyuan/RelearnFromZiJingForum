package org.example.rebuiltzijingforum.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 这里是用于处理业务异常的 如果发现了这个异常 那么对应的地方会再抛出异常给GlobalExceptionHandler处理
 */
public class BusinessException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public BusinessException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

