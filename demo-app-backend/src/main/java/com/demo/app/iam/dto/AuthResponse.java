package com.demo.app.iam.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
        UserInfo user,
        Boolean mfaRequired,
        String challengeToken,
        Boolean mfaEnrollmentRequired,
        String enrollmentToken,
        Boolean passwordExpired,
        String expireToken
) {
    public AuthResponse(UserInfo user) {
        this(user, null, null, null, null, null, null);
    }

    public static AuthResponse mfaChallenge(String challengeToken) {
        return new AuthResponse(null, true, challengeToken, null, null, null, null);
    }

    public static AuthResponse mfaEnrollmentRequired(String enrollmentToken) {
        return new AuthResponse(null, null, null, true, enrollmentToken, null, null);
    }

    public static AuthResponse passwordExpired(String expireToken) {
        return new AuthResponse(null, null, null, null, null, true, expireToken);
    }
}
