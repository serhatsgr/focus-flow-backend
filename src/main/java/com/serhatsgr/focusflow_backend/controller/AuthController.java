package com.serhatsgr.focusflow_backend.controller;

import com.serhatsgr.focusflow_backend.dto.*;
import com.serhatsgr.focusflow_backend.service.AuthService;
import com.serhatsgr.focusflow_backend.service.GoogleAuthService;
import com.serhatsgr.focusflow_backend.service.UserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FocusFlow kimlik doğrulama controller'ı.
 *
 * Değişiklikler (kod inceleme bulgularına göre):
 * - Login ve refresh iş mantığı AuthService'e taşındı.
 * - Controller artık sadece HTTP katmanını koordine eder (giriş doğrulama, HTTP
 * durum kodu).
 * - Ölü jwtService ve authenticationManager bağımlılıkları kaldırıldı.
 * - Ölü "if (!authentication.isAuthenticated())" kontrolü kaldırıldı.
 * - register 201 CREATED döner (200 OK yerine RESTful standart).
 */
@RestController
@RequestMapping("/auth")
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final GoogleAuthService googleAuthService;
    private final UserService userService;

    public AuthController(AuthService authService,
            GoogleAuthService googleAuthService,
            UserService userService) {
        this.authService = authService;
        this.googleAuthService = googleAuthService;
        this.userService = userService;
    }

    /**
     * Kullanıcı adı ve şifre ile giriş yapar.
     * Tüm iş mantığı AuthService.login() içindedir.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiSuccess<AuthResponse>> login(
            @Valid @RequestBody AuthRequest request) {

        AuthResponse response = authService.login(request.email(), request.password());
        return ResponseEntity.ok(ApiSuccess.of("Giriş başarılı", response));
    }

    /**
     * Google ID Token ile giriş / kayıt.
     */
    @PostMapping("/google")
    public ResponseEntity<ApiSuccess<AuthResponse>> googleLogin(
            @Valid @RequestBody GoogleLoginRequest request) {

        AuthResponse response = googleAuthService.authenticateWithGoogle(request.idToken());
        return ResponseEntity.ok(ApiSuccess.of("Google girişi başarılı", response));
    }

    /**
     * Refresh token ile yeni access + refresh token çifti üretir.
     */
    @PostMapping("/refresh-token")
    public ResponseEntity<ApiSuccess<AuthResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request) {

        AuthResponse response = authService.refresh(request.token());
        return ResponseEntity.ok(ApiSuccess.of("Token başarıyla yenilendi", response));
    }

    /**
     * Yeni kullanıcı kaydı.
     * Her kullanıcı ROLE_USER olarak kayıt olur — rol seçimi istemcide değil,
     * sunucuda.
     * HTTP 201 Created döner (RESTful standart).
     */
    @PostMapping("/register")
    public ResponseEntity<ApiSuccess<CreateUserResponse>> register(
            @Valid @RequestBody CreateUserRequest request) {

        CreateUserResponse response = userService.createUser(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiSuccess.of(201, "Kullanıcı başarıyla oluşturuldu", response));
    }
}