package com.serhatsgr.focusflow_backend.service;

import com.serhatsgr.focusflow_backend.dto.CreateUserRequest;
import com.serhatsgr.focusflow_backend.dto.CreateUserResponse;
import com.serhatsgr.focusflow_backend.dto.UpdateUserRequest;
import com.serhatsgr.focusflow_backend.dto.UserDto;
import com.serhatsgr.focusflow_backend.entities.Role;
import com.serhatsgr.focusflow_backend.entities.User;
import com.serhatsgr.focusflow_backend.exception.BaseException;
import com.serhatsgr.focusflow_backend.exception.ErrorMessage;
import com.serhatsgr.focusflow_backend.exception.MessageType;
import com.serhatsgr.focusflow_backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * FocusFlow kullanıcı yönetim servisi.
 *
 * Değişiklikler (kod inceleme bulgularına göre):
 * - PasswordEncoder arayüzü inject ediliyor (BCryptPasswordEncoder concrete
 * tipi değil)
 * - createUser(): authorities artık request'ten değil; ROLE_USER sabit atanır
 * - updateUser(): User entity yerine UserDto döner
 * - Catch-rethrow anti-pattern'leri temizlendi
 */
@Service
@Transactional(readOnly = true)
public class UserService implements UserDetailsService {

        private static final Logger log = LoggerFactory.getLogger(UserService.class);

        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder; // arayüze bağımlılık

        public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
                this.userRepository = userRepository;
                this.passwordEncoder = passwordEncoder;
        }

        // ──────────────────────────────────────────────────────────────────────────
        // Spring Security — UserDetailsService
        // ──────────────────────────────────────────────────────────────────────────

        @Override
        public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
                return userRepository.findByUsername(username)
                                .orElseThrow(() -> new UsernameNotFoundException("Kullanıcı bulunamadı: " + username));
        }

        // ──────────────────────────────────────────────────────────────────────────
        // CREATE
        // ──────────────────────────────────────────────────────────────────────────

        /**
         * Yeni kullanıcı oluşturur.
         * Rol CLIENT tarafından belirlenemez — her yeni kayıt ROLE_USER olarak atanır.
         */
        @Transactional
        public CreateUserResponse createUser(CreateUserRequest request) {
                log.info("Yeni kullanıcı oluşturuluyor: {}", request.username());

                if (userRepository.findByUsername(request.username()).isPresent()) {
                        throw new BaseException(new ErrorMessage(
                                        MessageType.DUPLICATE_RESOURCE, "Bu kullanıcı adı zaten kullanımda"));
                }
                if (userRepository.findByEmail(request.email()).isPresent()) {
                        throw new BaseException(new ErrorMessage(
                                        MessageType.DUPLICATE_RESOURCE, "Bu email adresi zaten kullanımda"));
                }

                User user = User.builder()
                                .username(request.username())
                                .email(request.email())
                                .password(passwordEncoder.encode(request.password()))
                                .authorities(Set.of(Role.ROLE_USER)) // Sabit rol — güvenlik gereği
                                .provider("LOCAL")
                                // Spring Security bayrakları — @Builder.Default riskine karşı açıkça set et
                                .enabled(true)
                                .accountNonExpired(true)
                                .accountNonLocked(true)
                                .credentialsNonExpired(true)
                                .build();

                User saved = userRepository.save(user);
                log.info("Kullanıcı oluşturuldu: id={}", saved.getId());

                return CreateUserResponse.of(saved.getUsername(), saved.getEmail(), Role.ROLE_USER.getAuthority());
        }

        // ──────────────────────────────────────────────────────────────────────────
        // READ
        // ──────────────────────────────────────────────────────────────────────────

        public List<UserDto> getAllUsers() {
                return userRepository.findAll().stream()
                                .map(this::toDto)
                                .collect(Collectors.toList());
        }

        public UserDto getUserById(Long id) {
                return userRepository.findById(id)
                                .map(this::toDto)
                                .orElseThrow(() -> new BaseException(new ErrorMessage(
                                                MessageType.RESOURCE_NOT_FOUND, "Kullanıcı bulunamadı")));
        }

        /**
         * Dahili kullanım: entity döner (servis katmanı içi).
         * Controller'a ya da dışarıya asla entity çıkmaz.
         */
        public User getUserByUsername(String username) {
                return userRepository.findByUsername(username)
                                .orElseThrow(() -> new BaseException(new ErrorMessage(
                                                MessageType.RESOURCE_NOT_FOUND, "Kullanıcı bulunamadı: " + username)));
        }

        // ──────────────────────────────────────────────────────────────────────────
        // UPDATE
        // ──────────────────────────────────────────────────────────────────────────

        /**
         * Kullanıcı adı ve email günceller.
         * Dönüş tipi UserDto — entity asla Controller'a çıkmaz.
         */
        @Transactional
        public UserDto updateUser(String currentUsername, UpdateUserRequest request) {
                User user = userRepository.findByUsername(currentUsername)
                                .orElseThrow(() -> new BaseException(new ErrorMessage(
                                                MessageType.RESOURCE_NOT_FOUND, "Kullanıcı bulunamadı")));

                if (!user.getUsername().equals(request.username()) &&
                                userRepository.findByUsername(request.username()).isPresent()) {
                        throw new BaseException(new ErrorMessage(
                                        MessageType.DUPLICATE_RESOURCE, "Bu kullanıcı adı zaten kullanımda"));
                }
                if (!user.getEmail().equals(request.email()) &&
                                userRepository.findByEmail(request.email()).isPresent()) {
                        throw new BaseException(new ErrorMessage(
                                        MessageType.DUPLICATE_RESOURCE, "Bu email adresi zaten kullanımda"));
                }

                user.setUsername(request.username());
                user.setEmail(request.email());
                return toDto(userRepository.save(user));
        }

        // ──────────────────────────────────────────────────────────────────────────
        // ADMIN İŞLEMLERİ
        // ──────────────────────────────────────────────────────────────────────────

        @Transactional
        public UserDto toggleUserBan(Long id) {
                User user = userRepository.findById(id)
                                .orElseThrow(() -> new BaseException(new ErrorMessage(
                                                MessageType.RESOURCE_NOT_FOUND, "Kullanıcı bulunamadı")));
                user.setEnabled(!user.isEnabled());
                return toDto(userRepository.save(user));
        }

        @Transactional
        public UserDto toggleUserRole(Long id) {
                User user = userRepository.findById(id)
                                .orElseThrow(() -> new BaseException(new ErrorMessage(
                                                MessageType.RESOURCE_NOT_FOUND, "Kullanıcı bulunamadı")));

                boolean isAdmin = user.getAuthorities().stream()
                                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

                user.getAuthorities().clear();
                user.getAuthorities().add(isAdmin ? Role.ROLE_USER : Role.ROLE_ADMIN);

                return toDto(userRepository.save(user));
        }

        // ──────────────────────────────────────────────────────────────────────────
        // DELETE
        // ──────────────────────────────────────────────────────────────────────────

        @Transactional
        public void deleteUser(Long id) {
                if (!userRepository.existsById(id)) {
                        throw new BaseException(new ErrorMessage(
                                        MessageType.RESOURCE_NOT_FOUND, "Kullanıcı bulunamadı"));
                }
                userRepository.deleteById(id);
        }

        @Transactional
        public void deleteUserByUsername(String username) {
                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new BaseException(new ErrorMessage(
                                                MessageType.RESOURCE_NOT_FOUND, "Kullanıcı bulunamadı")));
                userRepository.deleteById(user.getId());
        }

        // ──────────────────────────────────────────────────────────────────────────
        // Private Helpers
        // ──────────────────────────────────────────────────────────────────────────

        private UserDto toDto(User user) {
                Set<String> roles = user.getAuthorities().stream()
                                .map(a -> a.getAuthority())
                                .collect(Collectors.toSet());
                return new UserDto(user.getId(), user.getUsername(), user.getEmail(),
                                user.isEnabled(), roles, null);
        }
}
