package com.serhatsgr.focusflow_backend.dto;

public record TokenPairDto(
        String accessToken,
        String refreshToken
) {
}
