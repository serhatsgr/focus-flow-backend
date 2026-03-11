package com.serhatsgr.focusflow_backend.dto;

/**
 * Kimlik doğrulama başarılı yanıt DTO'su.
 * message ve success alanları KALDIRILDI — bu bilgiler ApiSuccess wrapper'ında
 * taşınır.
 * AuthResponse sadece auth'a özgü veriyi (token çifti + kullanıcı özeti) taşır.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String username,
        String role) {
    public static AuthResponse of(String accessToken, String refreshToken,
            String username, String role) {
        return new AuthResponse(accessToken, refreshToken, username, role);
    }
}