package com.serhatsgr.focusflow_backend.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
        @NotBlank(message = "Google ID Token boş olamaz")
        String idToken
) {}