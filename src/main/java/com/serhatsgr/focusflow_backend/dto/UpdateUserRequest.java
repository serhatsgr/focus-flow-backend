package com.serhatsgr.focusflow_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
                @NotBlank(message = "Email boş olamaz") @Email(message = "Geçerli bir email giriniz") String email) {

}
