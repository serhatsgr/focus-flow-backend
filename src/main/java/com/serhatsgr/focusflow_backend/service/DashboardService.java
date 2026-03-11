package com.serhatsgr.focusflow_backend.service;

import com.serhatsgr.focusflow_backend.dto.DashboardResponseDto;
import com.serhatsgr.focusflow_backend.entities.DailyAnalysis;
import com.serhatsgr.focusflow_backend.entities.FocusSession;
import com.serhatsgr.focusflow_backend.entities.SessionStatus;
import com.serhatsgr.focusflow_backend.entities.User;
import com.serhatsgr.focusflow_backend.repository.DailyAnalysisRepository;
import com.serhatsgr.focusflow_backend.repository.FocusSessionRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Dashboard ana ekranı iş mantığı servisi.
 *
 * Güvenlik prensibi:
 * Kullanıcı kimliği ASLA dışarıdan parametre olarak kabul edilmez.
 * SecurityContextHolder üzerinden doğrulanmış kullanıcı alınır.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

        private final FocusSessionRepository focusSessionRepository;
        private final DailyAnalysisRepository dailyAnalysisRepository;

        public DashboardService(FocusSessionRepository focusSessionRepository,
                        DailyAnalysisRepository dailyAnalysisRepository) {
                this.focusSessionRepository = focusSessionRepository;
                this.dailyAnalysisRepository = dailyAnalysisRepository;
        }

        /**
         * Giriş yapmış kullanıcının bugünkü dashboard verisini döner.
         *
         * İş akışı:
         * 1. SecurityContextHolder'dan doğrulanmış kullanıcıyı al.
         * 2. Bugün için UTC başlangıç ve bitiş zamanlarını hesapla.
         * 3. Kullanıcının bugünkü oturumlarını interruptions ile birlikte çek (JOIN
         * FETCH, N+1 yok).
         * 4. İstatistikleri hesapla (toplam oturum, dakika, kesinti).
         * 5. ONGOING oturum var mı? → isSessionOngoing flag'ini set et.
         * 6. Bugüne ait DailyAnalysis kaydını çek (yoksa null döner).
         * 7. DTO'yu oluştur ve dön.
         */
        public DashboardResponseDto getTodayDashboard() {
                User currentUser = getAuthenticatedUser();

                // Bugünün UTC sınırlarını hesapla
                LocalDate today = LocalDate.now();
                LocalDateTime dayStart = today.atStartOfDay(); // 00:00:00
                LocalDateTime dayEnd = today.atTime(LocalTime.MAX); // 23:59:59.999999999

                // Bugünkü oturumları interruptions ile birlikte getir (tek sorgu)
                List<FocusSession> todaySessions = focusSessionRepository
                                .findByUserIdAndStartTimeUtcBetweenFetchInterruptions(
                                                currentUser.getId(), dayStart, dayEnd);

                // İstatistik hesaplamaları
                int totalSessions = todaySessions.size();
                int totalDuration = todaySessions.stream()
                                .filter(s -> SessionStatus.COMPLETED.equals(s.getStatus()))
                                .mapToInt(s -> s.getActualDurationMinutes() != null ? s.getActualDurationMinutes() : 0)
                                .sum();
                int totalInterruptions = todaySessions.stream()
                                .mapToInt(s -> s.getInterruptions().size())
                                .sum();
                boolean isOngoing = todaySessions.stream()
                                .anyMatch(s -> SessionStatus.ONGOING.equals(s.getStatus()));

                // Günlük analiz kaydı
                Optional<DailyAnalysis> analysisOpt = dailyAnalysisRepository
                                .findByUser_IdAndDate(currentUser.getId(), today);

                DashboardResponseDto.MentalStateDto mentalStateDto = analysisOpt
                                .map(analysis -> DashboardResponseDto.MentalStateDto.builder()
                                                .title(analysis.getMentalStateTitle())
                                                .description(analysis.getMentalStateDescription())
                                                .build())
                                .orElse(null); // Analiz henüz yoksa null — @JsonInclude(NON_NULL) JSON'dan dışlar

                DashboardResponseDto.ActivityDto activityDto = DashboardResponseDto.ActivityDto.builder()
                                .totalSessions(totalSessions)
                                .totalDurationMinutes(totalDuration)
                                .totalInterruptions(totalInterruptions)
                                .build();

                return DashboardResponseDto.builder()
                                .mentalState(mentalStateDto)
                                .activity(activityDto)
                                .sessionOngoing(isOngoing)
                                .build();
        }

        // ──────────────────────────────────────────────────────────────────────────
        // Private: güvenli kullanıcı çözümlemesi
        // ──────────────────────────────────────────────────────────────────────────

        /**
         * SecurityContextHolder'dan doğrulanmış kullanıcıyı alır.
         * JwtAuthFilter her istekte UserDetails'i SecurityContext'e set eder;
         * bu metot o bağlamdan User entity'sini çıkarır.
         *
         * @throws IllegalStateException eğer güvenlik bağlamında kullanıcı yoksa
         *                               (bu durum SecurityConfig ile korunan
         *                               endpoint'lerde oluşmamalı)
         */
        private User getAuthenticatedUser() {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
                        throw new IllegalStateException("Güvenlik bağlamında doğrulanmış kullanıcı bulunamadı");
                }
                return user;
        }
}
