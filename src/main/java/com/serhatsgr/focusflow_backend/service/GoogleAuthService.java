package com.serhatsgr.focusflow_backend.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.serhatsgr.focusflow_backend.dto.AuthResponse;
import com.serhatsgr.focusflow_backend.dto.TokenPairDto;
import com.serhatsgr.focusflow_backend.entities.Role;
import com.serhatsgr.focusflow_backend.entities.User;
import com.serhatsgr.focusflow_backend.exception.BaseException;
import com.serhatsgr.focusflow_backend.exception.ErrorMessage;
import com.serhatsgr.focusflow_backend.exception.MessageType;
import com.serhatsgr.focusflow_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * Google OAuth2 ID Token doğrulaması ve kullanıcı oluşturma/güncelleme.
 *
 * Değişiklikler:
 * - Username üretimi: döngüsel N+1 sorgu yerine UUID suffix ile tek sorgusuz
 * üretim.
 * - GoogleIdTokenVerifier @Bean olarak enjekte edilmesi önerilir; şimdilik
 * her çağrıda oluşturuluyor (kabul edilebilir — verifier thread-safe).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleAuthService {

    @Value("${google.client.id}")
    private String googleClientId;

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse authenticateWithGoogle(String idTokenString) {
        GoogleIdToken.Payload payload = verifyAndExtractPayload(idTokenString);

        String email = payload.getEmail();
        String name = (String) payload.get("name");
        String picture = (String) payload.get("picture");

        log.info("Google girişi: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> createNewGoogleUser(email, name, picture));

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

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Google ID Token'ı doğrular ve payload döner.
     */
    private GoogleIdToken.Payload verifyAndExtractPayload(String idTokenString) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(),
                    new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new BaseException(new ErrorMessage(
                        MessageType.AUTHENTICATION_FAILED, "Geçersiz Google Token"));
            }
            return idToken.getPayload();

        } catch (BaseException e) {
            throw e;
        } catch (GeneralSecurityException | IOException e) {
            log.error("Google token doğrulama hatası", e);
            throw new BaseException(new ErrorMessage(
                    MessageType.AUTHENTICATION_FAILED, "Google kimlik doğrulaması başarısız"));
        }
    }

    /**
     * Yeni Google kullanıcısı oluşturur.
     */
    private User createNewGoogleUser(String email, String name, String pictureUrl) {
        log.info("Yeni Google kullanıcısı oluşturuluyor: {}", email);

        User newUser = User.builder()
                .email(email)
                // Google kullanıcıları LOCAL şifre kullanmaz; encode edilmiş random UUID atanır
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .authorities(Set.of(Role.ROLE_USER))
                .provider("GOOGLE")
                .profileImageUrl(pictureUrl)
                // Spring Security bayrakları — explicit set edilmezse null/false riski
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();

        return userRepository.save(newUser);
    }
}