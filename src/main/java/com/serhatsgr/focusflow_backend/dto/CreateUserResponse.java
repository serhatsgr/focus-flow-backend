package com.serhatsgr.focusflow_backend.dto;

/**
 * Kullanıcı kayıt yanıt DTO'su.
 * message ve success alanları KALDIRILDI.
 * Başarı/hata bilgisi ApiSuccess / GlobalExceptionHandler tarafından taşınır.
 */
public record CreateUserResponse(
        String username,
        String email,
        String role) {
    public static CreateUserResponse of(String username, String email, String role) {
        return new CreateUserResponse(username, email, role);
    }
}
