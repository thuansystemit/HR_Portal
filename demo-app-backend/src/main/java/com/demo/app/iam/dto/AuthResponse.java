package com.demo.app.iam.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
        UserInfo user,
        Boolean mfaRequired,
        String challengeToken,
        Boolean mfaEnrollmentRequired,
        String enrollmentToken
) {
    public AuthResponse(UserInfo user) {
        this(user, null, null, null, null);
    }

    public static AuthResponse mfaChallenge(String challengeToken) {
        return new AuthResponse(null, true, challengeToken, null, null);
    }

    public static AuthResponse mfaEnrollmentRequired(String enrollmentToken) {
        return new AuthResponse(null, null, null, true, enrollmentToken);
    }
}
