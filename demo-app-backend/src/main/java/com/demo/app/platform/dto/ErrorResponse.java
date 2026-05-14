package com.demo.app.platform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private String code;
    private String message;
    private Map<String, String> details;
    private Instant timestamp;

    public static ErrorResponse of(String code, String message) {
        return ErrorResponse.builder()
                .code(code)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }

    public static ErrorResponse ofValidation(Map<String, String> fieldErrors) {
        return ErrorResponse.builder()
                .code("VALIDATION_ERROR")
                .message("One or more fields have invalid values")
                .details(fieldErrors)
                .timestamp(Instant.now())
                .build();
    }
}
