package com.serhatsgr.focusflow_backend.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token boş olamaz")
        String token
) {

}
