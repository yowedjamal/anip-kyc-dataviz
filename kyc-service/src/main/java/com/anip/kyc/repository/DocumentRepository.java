package com.anip.kyc.repository;

import com.anip.kyc.models.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findBySessionIdOrderByCreatedAtDesc(UUID sessionId);
}
