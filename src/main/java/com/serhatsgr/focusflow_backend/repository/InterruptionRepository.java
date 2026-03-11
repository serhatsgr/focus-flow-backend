package com.serhatsgr.focusflow_backend.repository;

import com.serhatsgr.focusflow_backend.entities.Interruption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Interruption CRUD repository.
 * Şimdilik extends JpaRepository yeterlidir; ileride özel sorgular
 * eklenecektir.
 */
@Repository
public interface InterruptionRepository extends JpaRepository<Interruption, UUID> {
}
