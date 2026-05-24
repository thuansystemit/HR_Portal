package com.demo.app.platform.handler;

import com.demo.app.platform.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleNotFound_returns404() {
        var ex = new ResourceNotFoundException("User", UUID.randomUUID());

        var response = handler.handleNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("NOT_FOUND");
    }

    @Test
    void handleConflict_returns409() {
        var ex = new ConflictException("Email already in use");

        var response = handler.handleConflict(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getCode()).isEqualTo("CONFLICT");
        assertThat(response.getBody().getMessage()).contains("Email already in use");
    }

    @Test
    void handleBuiltIn_returns409() {
        var ex = new BuiltInRoleException();

        var response = handler.handleBuiltIn(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getCode()).isEqualTo("BUILT_IN_ROLE");
    }

    @Test
    void handleWrongPassword_returns422() {
        var ex = new WrongPasswordException();

        var response = handler.handleWrongPassword(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().getCode()).isEqualTo("WRONG_PASSWORD");
    }

    @Test
    void handleForbidden_returns403() {
        var ex = new ForbiddenException("Access not allowed");

        var response = handler.handleForbidden(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getCode()).isEqualTo("FORBIDDEN");
    }

    @Test
    void handleBusinessRule_returns422() {
        var ex = new BusinessRuleException("Cannot delete role with assigned users");

        var response = handler.handleBusinessRule(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void handleValidation_returns400_withFieldErrors() throws NoSuchMethodException {
        var target = new Object();
        var bindingResult = new BeanPropertyBindingResult(target, "request");
        bindingResult.addError(new FieldError("request", "email", "must not be blank"));
        bindingResult.addError(new FieldError("request", "name", "size must be between 2 and 100"));

        var ex = new MethodArgumentNotValidException(null, bindingResult);

        var response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().getDetails()).containsKey("email");
        assertThat(response.getBody().getDetails()).containsKey("name");
        assertThat(response.getBody().getDetails().get("email")).isEqualTo("must not be blank");
    }

    @Test
    void handleAuth_returns401() {
        var ex = new BadCredentialsException("Bad credentials");

        var response = handler.handleAuth(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getCode()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void handleAccessDenied_returns403() {
        var ex = new AccessDeniedException("Access denied");

        var response = handler.handleAccessDenied(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getCode()).isEqualTo("FORBIDDEN");
    }

    @Test
    void handleValidation_returns400_withNullDefaultMessage() throws NoSuchMethodException {
        // Test the branch: fe.getDefaultMessage() != null ? ... : "Invalid value"
        var target = new Object();
        var bindingResult = new BeanPropertyBindingResult(target, "request");
        // Manually create a field error with null message
        var fieldError = new FieldError("request", "field", null, false, null, null, null);
        bindingResult.addError(fieldError);

        var ex = new MethodArgumentNotValidException(null, bindingResult);

        var response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getDetails()).containsKey("field");
        assertThat(response.getBody().getDetails().get("field")).isEqualTo("Invalid value");
    }

    @Test
    void handleGeneral_returns500() {
        var ex = new RuntimeException("Internal error: secret details");

        var response = handler.handleGeneral(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getCode()).isEqualTo("INTERNAL_ERROR");
        // Should not leak internal error details
        assertThat(response.getBody().getMessage()).doesNotContain("secret details");
    }

    @Test
    void handleIllegalState_returns422() {
        var ex = new IllegalStateException("Job posting is not open for applications");

        var response = handler.handleIllegalState(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().getMessage()).contains("not open");
    }

    @Test
    void handleIllegalArgument_returns400() {
        var ex = new IllegalArgumentException("Invalid stage: UNKNOWN");

        var response = handler.handleIllegalArgument(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo("BAD_REQUEST");
        assertThat(response.getBody().getMessage()).contains("Invalid stage");
    }
}
