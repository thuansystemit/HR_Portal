package com.demo.app.cv.dto;

import java.util.UUID;

public record CvCandidateSimpleResult(UUID id, String fullName, String email) {}
