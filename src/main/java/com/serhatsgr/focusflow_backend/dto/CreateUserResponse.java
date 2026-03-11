package com.serhatsgr.focusflow_backend.dto;

/**
 * Kullanıcı kayıt yanıt DTO'su.
 * message ve success alanları KALDIRILDI.
 * Başarı/hata bilgisi ApiSuccess / GlobalExceptionHandler tarafından taşınır.
 */
public record CreateUserResponse(
        String email,
        String role) {
    public static CreateUserResponse of(String email, String role) {
        return new CreateUserResponse(email, role);
    }
}
