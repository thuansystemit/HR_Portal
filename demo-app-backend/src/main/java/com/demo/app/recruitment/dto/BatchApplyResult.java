package com.demo.app.recruitment.dto;

import java.util.List;
import java.util.UUID;

public record BatchApplyResult(
        List<ApplicationResponse> applied,
        List<UUID> skipped
) {}
