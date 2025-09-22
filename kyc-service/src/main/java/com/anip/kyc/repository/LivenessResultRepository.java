package com.anip.kyc.repository;

import com.anip.kyc.models.LivenessResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LivenessResultRepository extends JpaRepository<LivenessResult, UUID> {
    List<LivenessResult> findBySessionIdOrderByCreatedAtDesc(UUID sessionId);
}
