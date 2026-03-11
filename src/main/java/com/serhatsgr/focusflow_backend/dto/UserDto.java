package com.serhatsgr.focusflow_backend.dto;

import java.time.LocalDateTime;
import java.util.Set;

public record UserDto(
                Long id,
                String email,
                boolean isEnabled,
                Set<String> roles,
                LocalDateTime createdAt) {
}
