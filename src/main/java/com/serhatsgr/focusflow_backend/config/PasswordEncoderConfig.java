package com.serhatsgr.focusflow_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Şifre hashleme konfigürasyonu.
 * Bean tipi PasswordEncoder arayüzü olarak döndürülür — concrete BCrypt'e
 * değil,
 * arayüze bağımlılık prensibine uygun.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
