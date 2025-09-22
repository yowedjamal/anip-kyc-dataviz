package com.anip.kyc.repository;

import com.anip.kyc.models.FaceMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FaceMatchRepository extends JpaRepository<FaceMatch, UUID> {
    List<FaceMatch> findBySessionIdOrderByCreatedAtDesc(UUID sessionId);
}
