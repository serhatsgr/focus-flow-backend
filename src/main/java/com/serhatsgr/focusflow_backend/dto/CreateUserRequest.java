package com.serhatsgr.focusflow_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Kullanıcı kayıt isteği DTO'su.
 * authorities alanı KALDIRILDI — kullanıcı kendi rolünü belirleyemez.
 * Rol ataması UserService.createUser() içinde ROLE_USER olarak sabit yapılır.
 */
public record CreateUserRequest(

                @NotBlank(message = "{user.username.notblank}") @Size(min = 3, max = 32, message = "{user.username.size}") String username,

                @NotBlank(message = "{user.email.notblank}") @Email(message = "{user.email.invalid}") String email,

                @NotBlank(message = "{user.password.notblank}") @Size(min = 12, max = 128, message = "{user.password.size}") String password

) {
}
