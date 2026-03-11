package com.serhatsgr.focusflow_backend.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthRequest(

                @NotBlank(message = "Lütfen email adresinizi giriniz") @jakarta.validation.constraints.Email(message = "Geçerli bir email adresi giriniz") String email,

                @NotBlank(message = "Lütfen şifrenizi giriniz") String password) {
}
