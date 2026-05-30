package com.demo.app.platform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private String code;
    private String message;
    private Map<String, String> details;
    private List<String> violations;
    private Instant timestamp;

    public static ErrorResponse of(String code, String message) {
        return ErrorResponse.builder()
                .code(code)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }

    public static ErrorResponse ofPasswordPolicy(List<String> violations) {
        return ErrorResponse.builder()
                .code("PASSWORD_POLICY")
                .message("Password does not meet policy requirements")
                .violations(violations)
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
