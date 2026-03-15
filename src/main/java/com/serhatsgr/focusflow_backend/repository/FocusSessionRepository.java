package com.serhatsgr.focusflow_backend.repository;

import com.serhatsgr.focusflow_backend.entities.FocusSession;
import com.serhatsgr.focusflow_backend.entities.SessionStatus;
import com.serhatsgr.focusflow_backend.entities.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FocusSessionRepository extends JpaRepository<FocusSession, UUID> {

  /**
   * Belirli bir zaman aralığında en az 1 oturumu olan kullanıcıları
   * sayfalamalı (paginated) olarak döner.
   * Scheduler batch job'ı tarafından kullanılır — RAM'i korur.
   */
  @Query("""
      SELECT DISTINCT fs.user FROM FocusSession fs
      WHERE fs.startTimeUtc BETWEEN :start AND :end
      """)
  Page<User> findDistinctUsersWithSessionsBetween(
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end,
      Pageable pageable);

  /**
   * Kullanıcının belirli bir zaman aralığındaki oturumlarını interruptions ile
   * birlikte getirir.
   * JOIN FETCH → N+1 sorgusunu önler.
   */
  @Query("""
      SELECT DISTINCT fs FROM FocusSession fs
      LEFT JOIN FETCH fs.interruptions
      WHERE fs.user.id = :userId
        AND fs.startTimeUtc BETWEEN :start AND :end
      """)
  List<FocusSession> findByUserIdAndStartTimeUtcBetweenFetchInterruptions(
      @Param("userId") Long userId,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  /**
   * Kullanıcının ONGOING veya PAUSED durumunda oturumu var mı?
   * startSession() öncesinde çakışma kontrolü için kullanılır.
   */
  boolean existsByUserAndStatus(User user, SessionStatus status);

  /**
   * Belirli bir session'ı kullanıcı ID'si ile birlikte getirir.
   * Sahiplik doğrulaması: kullanıcının sadece kendi oturumuna erişmesini
   * garantiler.
   *
   * @param sessionId Güncellenecek oturumun UUID'si
   * @param userId    JWT'den alınan doğrulanmış kullanıcı ID'si
   */
  @Query("""
      SELECT fs FROM FocusSession fs
      LEFT JOIN FETCH fs.interruptions
      WHERE fs.id = :sessionId
        AND fs.user.id = :userId
      """)
  Optional<FocusSession> findByIdAndUserId(
      @Param("sessionId") UUID sessionId,
      @Param("userId") Long userId);

  List<FocusSession> findAllByUser_EmailAndStartTimeUtcBetween(String email, LocalDateTime startOfDay,
      LocalDateTime endOfDay);
}
