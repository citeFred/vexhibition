package com.meta.vexhibition.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiResponseDto<T> {
    private final boolean success;
    private final int statusCode;
    private final String message;
    private final T data;

    // Success Res
    public ApiResponseDto(T data, String message) {
        this.success = true;
        this.statusCode = HttpStatus.OK.value(); // 200
        this.message = message;
        this.data = data;
    }

    // Exception Res
    public ApiResponseDto(HttpStatus status, String message) {
        this.success = false;
        this.statusCode = status.value();
        this.message = message;
        this.data = null;
    }
}