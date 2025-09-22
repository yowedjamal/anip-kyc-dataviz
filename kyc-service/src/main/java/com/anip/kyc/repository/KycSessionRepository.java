package com.anip.kyc.repository;

import com.anip.kyc.models.KycSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.List;

@Repository
public interface KycSessionRepository extends JpaRepository<KycSession, UUID> {
    List<KycSession> findBySessionStatus(KycSession.SessionStatus status);

    // Find sessions where expiresAt is before now and not already marked expired
    @org.springframework.data.jpa.repository.Query("SELECT k FROM KycSession k WHERE k.expiresAt < CURRENT_TIMESTAMP AND k.sessionStatus <> 'EXPIRED'")
    List<KycSession> findExpiredSessions();
}
