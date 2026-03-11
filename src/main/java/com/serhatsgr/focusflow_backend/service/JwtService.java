package com.serhatsgr.focusflow_backend.service;

import com.serhatsgr.focusflow_backend.dto.TokenPairDto;
import com.serhatsgr.focusflow_backend.entities.RefreshToken;
import com.serhatsgr.focusflow_backend.exception.BaseException;
import com.serhatsgr.focusflow_backend.exception.ErrorMessage;
import com.serhatsgr.focusflow_backend.exception.MessageType;
import com.serhatsgr.focusflow_backend.repository.RefreshTokenRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * JWT Access Token üretimi, doğrulaması ve Refresh Token yönetimi.
 *
 * Mimari karar:
 * - JwtService artık UserRepository'e bağımlı DEĞİL.
 * - generateAccessToken() authorities parametresini dışarıdan alır;
 * bu bilgiyi veren taraf (AuthService / GoogleAuthService) servistir.
 * - JJWT 0.12.x non-deprecated API kullanılmaktadır.
 */
@Service
public class JwtService {

    @Value("${jwt.key}")
    private String secret;

    /** Access token ömrü (saniye cinsinden). */
    @Value("${jwt.access-token-expiry}")
    private Long accessTokenExpirySeconds;

    /**
     * Refresh token ömrü (dakika cinsinden). Özellik adı birimi açıkça belirtir.
     */
    @Value("${jwt.refresh-token-expiry-minutes}")
    private Long refreshTokenExpiryMinutes;

    private final RefreshTokenRepository refreshTokenRepository;

    public JwtService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Key
    // ──────────────────────────────────────────────────────────────────────────

    private SecretKey getSignKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Access Token
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Access token üretir.
     *
     * @param email       JWT subject (kullanıcı emaili)
     * @param authorities Kullanıcı rolleri — çağıran servis tarafından sağlanır.
     *                    JwtService artık DB'ye gitmez.
     */
    public String generateAccessToken(String email, Collection<? extends GrantedAuthority> authorities) {
        List<String> roles = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        return Jwts.builder()
                .claim("authorities", roles)
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpirySeconds * 1_000))
                .signWith(getSignKey()) // 0.12.x: algoritma otomatik seçilir
                .compact();
    }

    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSignKey()) // 0.12.x
                    .build()
                    .parseSignedClaims(token) // 0.12.x
                    .getPayload();
        } catch (Exception e) {
            throw new BaseException(new ErrorMessage(MessageType.TOKEN_EXPIRED,
                    "Token geçersiz veya süresi dolmuş"));
        }
    }

    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(extractAllClaims(token));
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Access token'ı doğrular: imza geçerli mi ve kullanıcıyla eşleşiyor mu?
     */
    public boolean validateToken(String token, UserDetails userDetails) {
        final String email = extractEmail(token);
        return email.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Refresh Token
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Yeni bir refresh token üretir ve veritabanına kaydeder.
     * Önceki aktif token'lar "kullanıldı" olarak işaretlenir.
     */
    @Transactional
    public String generateRefreshToken(String email) {
        // Önce tüm aktif (kullanılmamış) token'ları iptal et
        refreshTokenRepository.markAllAsUsedByEmail(email);

        String tokenValue = UUID.randomUUID() + "-" + System.currentTimeMillis();
        LocalDateTime expiryDate = LocalDateTime.now().plusMinutes(refreshTokenExpiryMinutes);

        RefreshToken refreshToken = new RefreshToken(tokenValue, email, expiryDate);
        refreshTokenRepository.save(refreshToken);

        return tokenValue;
    }

    /**
     * Access + Refresh token çifti üretir.
     *
     * @param email       Kullanıcı emaili
     * @param authorities Kullanıcı rolleri (DB'den gelmez, çağıran tarafından
     *                    verilir)
     */
    public TokenPairDto generateTokenPair(String email, Collection<? extends GrantedAuthority> authorities) {
        String accessToken = generateAccessToken(email, authorities);
        String refreshToken = generateRefreshToken(email);
        return new TokenPairDto(accessToken, refreshToken);
    }

    /**
     * Refresh token'ı doğrular ve kullanıcı emailini döner.
     * Race condition'ı önlemek için: refresh token DB'de geçerli hale getirildikten
     * SONRA
     * "kullanıldı" işaretlenir. Transactional bütünlük sağlanır.
     *
     * @return Refresh token'a ait kullanıcı emaili
     */
    @Transactional
    public String refreshAccessToken(String refreshTokenValue) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new BaseException(new ErrorMessage(
                        MessageType.NOT_FOUND, "Refresh token bulunamadı")));

        if (refreshToken.isExpired() || refreshToken.isUsed()) {
            // Geçersiz token varsa tamamen sil
            refreshTokenRepository.delete(refreshToken);
            throw new BaseException(new ErrorMessage(
                    MessageType.UNAUTHORIZED, "Refresh token geçersiz veya süresi dolmuş"));
        }

        // Geçerli — önce kullanıldı işaretle, sonra kullanıcı emailini dön
        refreshToken.setUsed(true);
        refreshTokenRepository.save(refreshToken);

        return refreshToken.getEmail();
    }

    /**
     * Kullanıcının tüm refresh token'larını iptal eder (logout işlemi).
     */
    @Transactional
    public void revokeAllRefreshTokens(String email) {
        refreshTokenRepository.deleteByEmail(email);
    }

    /**
     * Süresi dolmuş token'ları temizler (örn. @Scheduled ile periyodik çağrılır).
     */
    @Transactional
    public void cleanupExpiredTokens() {
        refreshTokenRepository.deleteExpiredTokens(LocalDateTime.now());
    }
}