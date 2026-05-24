package com.demo.app.recruitment.dto;

import java.util.List;
import java.util.Map;

public record BoardResponse(
        Map<String, List<ApplicationResponse>> columns
) {}
