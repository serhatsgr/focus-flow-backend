package com.serhatsgr.focusflow_backend.repository;

import com.serhatsgr.focusflow_backend.entities.DailyAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyAnalysisRepository extends JpaRepository<DailyAnalysis, UUID> {

    /**
     * Kullanıcının belirli bir tarihteki bireysel analizini döner.
     * Her kullanıcı için günde en fazla bir kayıt olduğu unique constraint ile
     * güvence altına alınmıştır.
     */
    Optional<DailyAnalysis> findByUser_IdAndDate(Long userId, LocalDate date);
}
