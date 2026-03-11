package com.serhatsgr.focusflow_backend.service;

import com.serhatsgr.focusflow_backend.dto.AuthResponse;
import com.serhatsgr.focusflow_backend.dto.TokenPairDto;
import com.serhatsgr.focusflow_backend.entities.User;
import com.serhatsgr.focusflow_backend.exception.BaseException;
import com.serhatsgr.focusflow_backend.exception.ErrorMessage;
import com.serhatsgr.focusflow_backend.exception.MessageType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Kimlik doğrulama iş mantığını Controller'dan ayıran servis katmanı.
 *
 * Mimari karar:
 * - Login ve token-yenileme akışlarındaki iş mantığı (kullanıcı sorgulama,
 * rol çekme, AuthResponse oluşturma) AuthController'dan buraya taşındı.
 * - Controller artık sadece HTTP girdi/çıktı koordinasyonu yapar.
 */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final JwtService jwtService;

    public AuthService(AuthenticationManager authenticationManager,
            UserService userService,
            JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.jwtService = jwtService;
    }

    /**
     * Kullanıcı adı/şifre ile giriş yapar ve token çifti döner.
     *
     * @throws BaseException kimlik doğrulama başarısız olursa
     */
    public AuthResponse login(String email, String password) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password));
            // authenticate() başarısız olursa AuthenticationException fırlatır;
            // isAuthenticated() kontrolü gereksiz (ölü kod) — burada yok.

            User user = (User) authentication.getPrincipal();
            return buildAuthResponse(user);

        } catch (BadCredentialsException | UsernameNotFoundException e) {
            // Hata mesajını normalize et: saldırganlara kullanıcı varlığı bilgisi verme
            throw new BaseException(new ErrorMessage(
                    MessageType.AUTHENTICATION_FAILED, "Geçersiz email veya şifre"));
        }
    }

    /**
     * Geçerli bir refresh token karşılığında yeni token çifti üretir.
     *
     * @param refreshTokenValue istemcinin gönderdiği refresh token değeri
     */
    public AuthResponse refresh(String refreshTokenValue) {
        String email = jwtService.refreshAccessToken(refreshTokenValue);
        User user = userService.getUserByEmail(email);
        return buildAuthResponse(user);
    }

    /**
     * User entity'sinden AuthResponse DTO oluşturur.
     * Rol çekme mantığı tek yerde merkezileştirildi.
     */
    private AuthResponse buildAuthResponse(User user) {
        TokenPairDto tokenPair = jwtService.generateTokenPair(user.getEmail(), user.getAuthorities());

        String role = user.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse("ROLE_USER");

        return AuthResponse.of(
                tokenPair.accessToken(),
                tokenPair.refreshToken(),
                user.getEmail(),
                role);
    }
}
