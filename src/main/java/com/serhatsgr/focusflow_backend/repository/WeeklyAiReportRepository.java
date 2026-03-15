package com.serhatsgr.focusflow_backend.repository;

import com.serhatsgr.focusflow_backend.entities.WeeklyAiReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WeeklyAiReportRepository extends JpaRepository<WeeklyAiReport, UUID> {

    /**
     * Kullanıcının belirli bir hafta başlangıç tarihine ait raporunu getirir.
     * Cache-first okuma mantığında kullanılır.
     */
    Optional<WeeklyAiReport> findByUserIdAndWeekStartDate(Long userId, LocalDate weekStartDate);

    /**
     * Kullanıcının belirli bir hafta için raporu zaten üretilmiş mi?
     * Scheduler'ın mükerrer rapor üretmesini engellemek için kullanılır.
     */
    boolean existsByUserIdAndWeekStartDate(Long userId, LocalDate weekStartDate);
}
