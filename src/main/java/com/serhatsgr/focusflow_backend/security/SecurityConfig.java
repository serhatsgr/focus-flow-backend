package com.serhatsgr.focusflow_backend.security;

import com.serhatsgr.focusflow_backend.service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * FocusFlow Spring Security konfigürasyonu.
 *
 * Değişiklikler (kod inceleme bulgularına göre):
 * - setHideUserNotFoundExceptions(false) KALDIRILDI → user enumeration
 * saldırısını önler
 * - anyRequest().authenticated() EKLENDİ → açık kalan tüm path'ler güvende
 * - PasswordEncoder concrete tip yerine arayüz inject ediliyor
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(UserService userService,
            PasswordEncoder passwordEncoder,
            JwtAuthFilter jwtAuthFilter) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // Kimlik doğrulama endpoint'leri — herkese açık
                        .requestMatchers("/auth/**").permitAll()
                        // Admin işlemleri — yalnızca ROLE_ADMIN
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // Tüm uygulama API'si — doğrulanmış kullanıcıya açık
                        .requestMatchers("/api/v1/**").authenticated()
                        // Geri kalan her şey de doğrulanmış olmalı
                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userService);
        provider.setPasswordEncoder(passwordEncoder);
        // setHideUserNotFoundExceptions(false) KALDIRILD — varsayılan true:
        // UsernameNotFoundException, BadCredentialsException olarak maskelenir.
        // Bu sayede saldırgan /auth/login ile kullanıcı adlarını tarayamaz.
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }
}