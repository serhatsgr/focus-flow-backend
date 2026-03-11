package com.serhatsgr.focusflow_backend.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthRequest(

        @NotBlank(message = "Kullanıcı adınızı giriniz" )
        String username,

        @NotBlank (message = "Lütfen şifrenizi giriniz" )
        String password
) {
}
