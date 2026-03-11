package com.serhatsgr.focusflow_backend.mapper;

import com.serhatsgr.focusflow_backend.dto.CreateUserResponse;
import com.serhatsgr.focusflow_backend.entities.Role;
import com.serhatsgr.focusflow_backend.entities.User;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * User entity ↔ DTO dönüşüm yardımcısı.
 *
 * Değişiklikler:
 * - CreateUserResponse.success() / .error() kaldırıldı (DTO güncellendi);
 * CreateUserResponse.of() kullanılıyor.
 * - toDto metodu güncellendi.
 * - Kullanılmayan updateEntity metodu kaldırıldı (UserService doğrudan handle
 * ediyor).
 */
@Component
public class UserMapper {

    /**
     * User entity'sinden kayıt yanıtı DTO'su üretir.
     */
    public CreateUserResponse toCreateResponse(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User entity null olamaz");
        }

        String roles = user.getAuthorities() != null && !user.getAuthorities().isEmpty()
                ? user.getAuthorities().stream()
                        .map(a -> a.getAuthority())
                        .collect(Collectors.joining(", "))
                : Role.ROLE_USER.getAuthority();

        return CreateUserResponse.of(user.getUsername(), user.getEmail(), roles);
    }

    /**
     * DTO → Entity dönüşümü (harici kullanım için yardımcı).
     * Şifre hashleme bu metotta yapılmaz; çağıran servis sorumludur.
     */
    public User toEntity(String username, String email, String encodedPassword, Role role) {
        return User.builder()
                .username(username)
                .email(email)
                .password(encodedPassword)
                .authorities(Set.of(role))
                // Spring Security bayrakları — explicit set edilmezse null/false riski
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();
    }
}
