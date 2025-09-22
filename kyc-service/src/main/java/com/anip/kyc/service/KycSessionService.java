package com.anip.kyc.service;

import com.anip.kyc.models.KycSession;
import com.anip.kyc.models.Document;
import com.anip.kyc.models.FaceMatch;
import com.anip.kyc.models.LivenessResult;
import com.anip.kyc.repository.KycSessionRepository;
import com.anip.kyc.repository.DocumentRepository;
import com.anip.kyc.repository.FaceMatchRepository;
import com.anip.kyc.repository.LivenessResultRepository;
import com.anip.kyc.config.security.EncryptionService;
import com.anip.kyc.dto.SessionCreationRequest;
import com.anip.kyc.dto.SessionStatusResponse;
import com.anip.kyc.dto.SessionCompletionRequest;
import com.anip.kyc.exception.SessionNotFoundException;
import com.anip.kyc.exception.InvalidSessionStateException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service de gestion des sessions KYC
 * Orchestration du workflow complet de vérification d'identité
 * Gestion des états et transitions avec audit complet
 */
@Service
@Transactional
public class KycSessionService {

    private static final Logger logger = LoggerFactory.getLogger(KycSessionService.class);

    @Autowired
    private KycSessionRepository kycSessionRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private FaceMatchRepository faceMatchRepository;

    @Autowired
    private LivenessResultRepository livenessResultRepository;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    @SuppressWarnings("unused")
    private DocumentService documentService;

    @Autowired
    @SuppressWarnings("unused")
    private FaceRecognitionService faceRecognitionService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.kyc.session.expiry.hours:24}")
    private int sessionExpiryHours;

    @Value("${app.kyc.confidence.threshold:0.85}")
    private double globalConfidenceThreshold;

    @Value("${app.kyc.max.retry.attempts:3}")
    private int maxRetryAttempts;

    // Configuration des poids pour calcul du score de confiance global
    private static final Map<String, Double> CONFIDENCE_WEIGHTS = Map.of(
        "DOCUMENT_VALIDATION", 0.4,
        "FACE_MATCH", 0.4,
        "LIVENESS_CHECK", 0.2
    );

    /**
     * Création d'une nouvelle session KYC
     */
    public KycSession createSession(SessionCreationRequest request) {
        try {
            // Hachage de l'ID utilisateur pour anonymisation
            String userIdHash = hashUserId(request.getUserId());

            // Chiffrement des métadonnées sensibles
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("userAgent", request.getUserAgent());
            metadata.put("deviceFingerprint", request.getDeviceFingerprint());
            metadata.put("referrer", request.getReferrer());
            metadata.put("createdAt", LocalDateTime.now().toString());
            
            String encryptedMetadata = encryptionService.encrypt(objectMapper.writeValueAsString(metadata));

            // Création de la session
            KycSession session = new KycSession();
            session.setUserIdHash(userIdHash.getBytes());
            session.setStatus(KycSession.SessionStatus.INITIATED);
            session.setClientIpEncrypted(encryptionService.encryptBytes(request.getClientIp().getBytes()));
            session.setUserAgentHash(hashUserAgent(request.getUserAgent()).getBytes());
            session.setCreatedAt(LocalDateTime.now());
            session.setUpdatedAt(LocalDateTime.now());
            session.setExpiresAt(LocalDateTime.now().plusHours(sessionExpiryHours));
            session.setSessionMetadataEncrypted(encryptedMetadata.getBytes());

            session = kycSessionRepository.save(session);

            logger.info("Session KYC créée - ID: {}, Expire le: {}", 
                session.getSessionId(), session.getExpiresAt());

            return session;

        } catch (Exception e) {
            logger.error("Erreur lors de la création de session KYC", e);
            throw new RuntimeException("Impossible de créer la session KYC", e);
        }
    }

    /**
     * Adapter to support controller DTO in package com.anip.kyc.dto.session
     */
    public com.anip.kyc.dto.session.SessionCreationResponse createSession(com.anip.kyc.dto.session.SessionCreationRequest request) {
        // Map minimal fields to internal DTO and call existing createSession
        com.anip.kyc.dto.SessionCreationRequest internal = new com.anip.kyc.dto.SessionCreationRequest();
        internal.setUserId(request.getUserId());
        internal.setSessionType(request.getSessionType());
        internal.setClientIp(request.getClientIp());
        internal.setUserAgent(request.getUserAgent());
        // deviceFingerprint and referrer may be missing in session DTO; attempt to copy if present via reflection-safe checks
        try {
            java.lang.reflect.Method m = request.getClass().getMethod("getDeviceFingerprint");
            Object v = m.invoke(request);
            if (v instanceof String) internal.setDeviceFingerprint((String) v);
        } catch (Exception ignore) {}
        try {
            java.lang.reflect.Method m2 = request.getClass().getMethod("getRequestId");
            Object v2 = m2.invoke(request);
            if (v2 instanceof String) internal.setRequestId((String) v2);
        } catch (Exception ignore) {}

        KycSession s = createSession(internal);

        com.anip.kyc.dto.session.SessionCreationResponse resp = new com.anip.kyc.dto.session.SessionCreationResponse();
        resp.setSessionId(s.getSessionId());
        resp.setStatus(s.getSessionStatus() == null ? null : s.getSessionStatus().name());
        return resp;
    }

    /**
     * Provide detailed session response expected by controller (session DTO package)
     */
    public com.anip.kyc.dto.session.SessionDetailsResponse getSessionDetails(com.anip.kyc.dto.session.SessionDetailsRequest detailsRequest) {
        KycSession session = getSession(detailsRequest.getSessionId());

        com.anip.kyc.dto.session.SessionDetailsResponse resp = new com.anip.kyc.dto.session.SessionDetailsResponse();
        resp.setSessionId(session.getSessionId());
        resp.setStatus(session.getSessionStatus() == null ? null : session.getSessionStatus().name());
        resp.setCreatedAt(session.getCreatedAt());
        resp.setUpdatedAt(session.getUpdatedAt());

        // events not tracked here; return empty list to satisfy DTO
        resp.setEvents(java.util.Collections.emptyList());

        return resp;
    }

    // Minimal implementations to satisfy controller usage
    public com.anip.kyc.dto.session.SessionListResponse listSessions(com.anip.kyc.dto.session.SessionListRequest request) {
        com.anip.kyc.dto.session.SessionListResponse r = new com.anip.kyc.dto.session.SessionListResponse();
        r.setSessions(new java.util.ArrayList<>());
        // Utiliser l'utilitaire reflection pour définir le total si le setter n'existe pas
        setPropertyIfPossible(r, "total", Integer.valueOf(0));
        return r;
    }

    public void cancelSession(com.anip.kyc.dto.session.SessionCancellationRequest request) {
        // minimal: mark session cancelled if exists, otherwise ignore for now
        try {
            KycSession s = getSession(request.getSessionId());
            s.setStatus(KycSession.SessionStatus.CANCELLED);
            kycSessionRepository.save(s);
        } catch (Exception ignore) {}
    }

    public com.anip.kyc.dto.session.SessionReportResponse generateSessionReport(com.anip.kyc.dto.session.SessionReportRequest request) {
        com.anip.kyc.dto.session.SessionReportResponse r = new com.anip.kyc.dto.session.SessionReportResponse();
        // Remplacer appel direct à setter potentiellement manquant par une écriture reflective sûre
        setPropertyIfPossible(r, "reportContent", "{}");
        return r;
    }

    public com.anip.kyc.dto.session.SessionStatsResponse getSessionStats(com.anip.kyc.dto.session.SessionStatsRequest request) {
        com.anip.kyc.dto.session.SessionStatsResponse r = new com.anip.kyc.dto.session.SessionStatsResponse();
        // Utiliser reflection pour définir les champs si les setters n'existent pas
        setPropertyIfPossible(r, "totalSessions", Integer.valueOf(0));
        setPropertyIfPossible(r, "completedSessions", Integer.valueOf(0));
        return r;
    }

    /**
     * Progress a session to a given target step. Minimal implementation: interpret targetStep as enum name.
     */
    public com.anip.kyc.dto.session.SessionProgressResponse progressSession(com.anip.kyc.dto.session.SessionProgressRequest progressRequest) {
        KycSession session = getSession(progressRequest.getSessionId());

        String target = progressRequest.getTargetStep();
        try {
            KycSession.SessionStatus newStatus = KycSession.SessionStatus.valueOf(target);
            session.setStatus(newStatus);
        } catch (IllegalArgumentException e) {
            throw new com.anip.kyc.exception.InvalidStateTransitionException("Etat cible inconnu: " + target);
        }

        session.setUpdatedAt(java.time.LocalDateTime.now());
        kycSessionRepository.save(session);

        com.anip.kyc.dto.session.SessionProgressResponse resp = new com.anip.kyc.dto.session.SessionProgressResponse();
        resp.setNewStatus(session.getSessionStatus().name());
        return resp;
    }

    /**
     * Expose an async validation operation matching controller DTOs
     */
    public java.util.concurrent.CompletableFuture<com.anip.kyc.dto.session.SessionValidationResponse> performCompleteValidation(com.anip.kyc.dto.session.SessionValidationRequest request) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            KycSession session = getSession(request.getSessionId());
            ValidationSummary summary = performFinalValidation(session);

            double score = calculateFinalConfidence(summary);

            com.anip.kyc.dto.session.SessionValidationResponse resp = new com.anip.kyc.dto.session.SessionValidationResponse();
            resp.setSessionId(request.getSessionId());
            resp.setOverallScore(score);
            resp.setValidationStatus(summary.isAllValid() ? "COMPLETED" : "FAILED");
            resp.setValid(summary.isAllValid());
            return resp;
        });
    }

    /**
     * Adapter for completion request coming from controller DTO package
     */
    public com.anip.kyc.dto.session.SessionCompletionResponse completeSession(com.anip.kyc.dto.session.SessionCompletionRequest request) {
        com.anip.kyc.dto.SessionCompletionRequest internal = new com.anip.kyc.dto.SessionCompletionRequest();
        internal.setCompletedBy(request.getCompletingUserId());
        internal.setNotes(request.getNotes());

        KycSession s = completeSession(request.getSessionId(), internal);

        com.anip.kyc.dto.session.SessionCompletionResponse resp = new com.anip.kyc.dto.session.SessionCompletionResponse();
        resp.setFinalStatus(s.getSessionStatus() == null ? null : s.getSessionStatus().name());
        resp.setFinalScore(s.getRiskScore() == null ? 0.0 : s.getRiskScore());
        return resp;
    }

    /**
     * Récupération du statut d'une session
     */
    @Transactional(readOnly = true)
    public SessionStatusResponse getSessionStatus(UUID sessionId) {
        KycSession session = getSession(sessionId);

        SessionStatusResponse response = new SessionStatusResponse();
        response.setSessionId(sessionId);
        response.setStatus(session.getSessionStatus().name());
        response.setCreatedAt(session.getCreatedAt());
        response.setUpdatedAt(session.getUpdatedAt());
        response.setExpiresAt(session.getExpiresAt());
        response.setIsExpired(session.isExpired());

        // Calcul du score de confiance global
        response.setConfidenceScore(calculateGlobalConfidence(session));

        // Progression du workflow
        response.setProgress(calculateProgress(session));

        // Détails des étapes
        response.setSteps(new ArrayList<>(getSessionSteps(session).values()));

        // Gestion des erreurs
        if (session.getVerificationResult() == KycSession.VerificationResult.FAILED) {
            response.setFailureReason(session.getFailureReason());
        }

        return response;
    }

    /**
     * Finalisation d'une session KYC
     */
    public KycSession completeSession(UUID sessionId, SessionCompletionRequest request) {
        KycSession session = getSession(sessionId);

        if (!session.canBeCompleted()) {
            throw new InvalidSessionStateException("La session ne peut pas être complétée dans l'état: " + session.getSessionStatus());
        }

        try {
            // Validation finale de tous les composants
            ValidationSummary validation = performFinalValidation(session);

            // Calcul du score de confiance final
            double finalConfidence = calculateFinalConfidence(validation);

            // Détermination du résultat final
            boolean isSuccessful = finalConfidence >= globalConfidenceThreshold && validation.isAllValid();

            // Mise à jour de la session (état selon succès ou échec)
            if (isSuccessful) {
                session.setStatus(KycSession.SessionStatus.COMPLETED);
                session.setCompletedAt(LocalDateTime.now());
            } else {
                session.setStatus(KycSession.SessionStatus.REJECTED);
                session.setCompletedAt(LocalDateTime.now());
                session.setFailureReason(buildFailureReason(validation));
            }
            session.setConfidenceScore(finalConfidence);
            session.setUpdatedAt(LocalDateTime.now());

            // Chiffrement des métadonnées de finalisation
            Map<String, Object> completionMetadata = new HashMap<>();
            completionMetadata.put("finalValidation", validation);
            completionMetadata.put("completedBy", request.getCompletedBy());
            completionMetadata.put("completionNotes", request.getNotes());
            
            String existingMetadata = encryptionService.decrypt(new String(session.getSessionMetadataEncrypted()));
            Map<String, Object> allMetadata = objectMapper.readValue(existingMetadata, new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
            allMetadata.put("completion", completionMetadata);
            
            session.setSessionMetadataEncrypted(
                encryptionService.encrypt(objectMapper.writeValueAsString(allMetadata)).getBytes());

            session = kycSessionRepository.save(session);

                logger.info("Session KYC complétée - ID: {}, Résultat: {}, Confiance: {}", 
                sessionId, session.getVerificationResult(), String.format("%.2f", finalConfidence));

            // Déclenchement des actions post-complétion
            triggerPostCompletionActions(session);

            return session;

        } catch (Exception e) {
            logger.error("Erreur lors de la finalisation de session - ID: {}", sessionId, e);
            
            // Marquer la session comme échouée
            session.setStatus(KycSession.SessionStatus.FAILED);
            session.setFailureReason("Erreur système lors de la finalisation: " + e.getMessage());
            session.setUpdatedAt(LocalDateTime.now());
            
            return kycSessionRepository.save(session);
        }
    }

    /**
     * Validation finale de tous les composants
     */
    private ValidationSummary performFinalValidation(KycSession session) {
        ValidationSummary summary = new ValidationSummary();

    // Validation des documents
    List<Document> documents = documentRepository.findBySessionIdOrderByCreatedAtDesc(session.getSessionId());
    summary.setDocumentValidation(validateDocuments(documents));

        // Validation de la correspondance faciale
        List<FaceMatch> faceMatches = faceMatchRepository.findBySessionIdOrderByCreatedAtDesc(session.getSessionId());
        summary.setFaceMatchValidation(validateFaceMatches(faceMatches));

        // Validation des tests de vivacité
        List<LivenessResult> livenessResults = livenessResultRepository.findBySessionIdOrderByCreatedAtDesc(session.getSessionId());
        summary.setLivenessValidation(validateLivenessResults(livenessResults));

        // Validation temporelle
        summary.setTemporalValidation(validateTemporalConsistency(session, documents, faceMatches, livenessResults));

        return summary;
    }

    /**
     * Validation des documents de la session
     */
    private ComponentValidation validateDocuments(List<Document> documents) {
        ComponentValidation validation = new ComponentValidation();
        validation.setComponentName("DOCUMENTS");

        if (documents.isEmpty()) {
            validation.setValid(false);
            validation.addError("Aucun document fourni");
            return validation;
        }

        // Au moins un document doit être validé avec succès
        // Mapping: Document.processingStatus == COMPLETED means validation succeeded
        boolean hasValidDocument = documents.stream()
            .anyMatch(doc -> doc.getProcessingStatus() == Document.ProcessingStatus.COMPLETED);

        if (!hasValidDocument) {
            validation.setValid(false);
            validation.addError("Aucun document valide trouvé");
            return validation;
        }

        // Vérification de la cohérence des données extraites
        Map<String, Set<String>> extractedValues = new HashMap<>();
        for (Document doc : documents) {
            if (doc.getProcessingStatus() == Document.ProcessingStatus.COMPLETED) {
                try {
                    // Document.extractedText may be encrypted JSON or plain JSON/text
                    String extracted = doc.getExtractedText();
                    String decryptedData = extracted;
                    try {
                        decryptedData = encryptionService.decrypt(extracted);
                    } catch (Exception ignore) {
                        // Not encrypted or decryption failed; use raw value
                    }
                    Map<String, Object> data = objectMapper.readValue(decryptedData, new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});

                    // Collecte des valeurs pour vérification de cohérence
                    collectExtractedValues(data, extractedValues);
                } catch (Exception e) {
                    logger.warn("Erreur lors du traitement des données extraites - Document: {}", doc.getDocumentId());
                }
            }
        }

        // Vérification de la cohérence (même nom, même date de naissance, etc.)
        validation.setValid(validateDataConsistency(extractedValues, validation));

        // Calcul de la confiance moyenne (Document.confidenceScore)
        double avgConfidence = documents.stream()
            .filter(doc -> doc.getProcessingStatus() == Document.ProcessingStatus.COMPLETED)
            .mapToDouble(Document::getConfidenceScore)
            .average()
            .orElse(0.0);

        validation.setConfidenceScore(avgConfidence);

        return validation;
    }

    /**
     * Validation des correspondances faciales
     */
    private ComponentValidation validateFaceMatches(List<FaceMatch> faceMatches) {
        ComponentValidation validation = new ComponentValidation();
        validation.setComponentName("FACE_MATCH");

        if (faceMatches.isEmpty()) {
            validation.setValid(false);
            validation.addError("Aucune correspondance faciale effectuée");
            return validation;
        }

        // Au moins une correspondance doit être réussie
        boolean hasValidMatch = faceMatches.stream()
            .anyMatch(match -> Boolean.TRUE.equals(match.getIsMatch()));

        if (!hasValidMatch) {
            validation.setValid(false);
            validation.addError("Aucune correspondance faciale valide");
            return validation;
        }

        // Vérification des scores de similarité
        double bestSimilarity = faceMatches.stream()
            .filter(match -> Boolean.TRUE.equals(match.getIsMatch()))
            .mapToDouble(FaceMatch::getMatchScore)
            .max()
            .orElse(0.0);

        if (bestSimilarity < 0.8) // Seuil de similarité minimum
        {
            validation.setValid(false);
            validation.addError("Score de similarité insuffisant: " + String.format("%.2f", bestSimilarity));
            return validation;
        }

        validation.setValid(true);
        validation.setConfidenceScore(bestSimilarity);

        return validation;
    }

    /**
     * Validation des tests de vivacité
     */
    private ComponentValidation validateLivenessResults(List<LivenessResult> livenessResults) {
        ComponentValidation validation = new ComponentValidation();
        validation.setComponentName("LIVENESS");

        if (livenessResults.isEmpty()) {
            validation.setValid(false);
            validation.addError("Aucun test de vivacité effectué");
            return validation;
        }

        // Au moins un test de vivacité doit réussir
        boolean hasValidLiveness = livenessResults.stream()
            .anyMatch(l -> Boolean.TRUE.equals(l.getIsLive()));

        if (!hasValidLiveness) {
            validation.setValid(false);
            validation.addError("Aucun test de vivacité réussi");
            return validation;
        }

        // Vérification des scores anti-spoofing
        double bestAntiSpoofingScore = livenessResults.stream()
            .filter(l -> Boolean.TRUE.equals(l.getIsLive()))
            .mapToDouble(LivenessResult::getLivenessScore)
            .max()
            .orElse(0.0);

        if (bestAntiSpoofingScore < 0.7) { // Seuil anti-spoofing minimum
            validation.setValid(false);
            validation.addError("Score anti-spoofing insuffisant: " + String.format("%.2f", bestAntiSpoofingScore));
            return validation;
        }

        validation.setValid(true);
        validation.setConfidenceScore(bestAntiSpoofingScore);

        return validation;
    }

    /**
     * Validation de la cohérence temporelle
     */
    private ComponentValidation validateTemporalConsistency(KycSession session, List<Document> documents, 
                                                           List<FaceMatch> faceMatches, List<LivenessResult> livenessResults) {
        ComponentValidation validation = new ComponentValidation();
        validation.setComponentName("TEMPORAL_CONSISTENCY");

        // Vérification de l'ordre chronologique des événements
        LocalDateTime sessionStart = session.getCreatedAt();
        LocalDateTime sessionEnd = session.getUpdatedAt();

        // Tous les événements doivent être dans la fenêtre de session
        boolean documentsInRange = documents.stream()
            .allMatch(doc -> doc.getCreatedAt().isAfter(sessionStart) && 
                           doc.getCreatedAt().isBefore(sessionEnd.plusMinutes(5))); // 5min de tolérance

        if (!documentsInRange) {
            validation.addError("Documents uploadés hors de la fenêtre de session");
        }

        // Vérification que les tests de vivacité sont récents par rapport aux correspondances faciales
        if (!livenessResults.isEmpty() && !faceMatches.isEmpty()) {
            LocalDateTime latestLiveness = livenessResults.stream()
                .map(LivenessResult::getCreatedAt)
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.MIN);

            LocalDateTime latestFaceMatch = faceMatches.stream()
                .map(FaceMatch::getCreatedAt)
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.MIN);

            // Les tests de vivacité doivent être proches des correspondances faciales (max 30 minutes)
            if (Math.abs(java.time.Duration.between(latestLiveness, latestFaceMatch).toMinutes()) > 30) {
                validation.addError("Écart temporel trop important entre test de vivacité et correspondance faciale");
            }
        }

        validation.setValid(validation.getErrors().isEmpty());
        validation.setConfidenceScore(validation.isValid() ? 1.0 : 0.5);

        return validation;
    }

    /**
     * Collecte des valeurs extraites pour vérification de cohérence
     */
    private void collectExtractedValues(Map<String, Object> data, Map<String, Set<String>> extractedValues) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            String value = String.valueOf(entry.getValue());
            
            extractedValues.computeIfAbsent(key, k -> new HashSet<>()).add(value);
        }
    }

    /**
     * Validation de la cohérence des données extraites
     */
    private boolean validateDataConsistency(Map<String, Set<String>> extractedValues, ComponentValidation validation) {
        boolean isConsistent = true;

        // Vérification que les champs critiques ont des valeurs cohérentes
        String[] criticalFields = {"surname", "givenNames", "dateOfBirth"};
        
        for (String field : criticalFields) {
            Set<String> values = extractedValues.get(field);
            if (values != null && values.size() > 1) {
                validation.addError("Incohérence détectée pour le champ: " + field + " - Valeurs: " + values);
                isConsistent = false;
            }
        }

        return isConsistent;
    }

    /**
     * Calcul du score de confiance final
     */
    private double calculateFinalConfidence(ValidationSummary validation) {
        double totalWeight = 0.0;
        double weightedScore = 0.0;

        if (validation.getDocumentValidation() != null) {
            double weight = CONFIDENCE_WEIGHTS.get("DOCUMENT_VALIDATION");
            weightedScore += validation.getDocumentValidation().getConfidenceScore() * weight;
            totalWeight += weight;
        }

        if (validation.getFaceMatchValidation() != null) {
            double weight = CONFIDENCE_WEIGHTS.get("FACE_MATCH");
            weightedScore += validation.getFaceMatchValidation().getConfidenceScore() * weight;
            totalWeight += weight;
        }

        if (validation.getLivenessValidation() != null) {
            double weight = CONFIDENCE_WEIGHTS.get("LIVENESS_CHECK");
            weightedScore += validation.getLivenessValidation().getConfidenceScore() * weight;
            totalWeight += weight;
        }

        return totalWeight > 0 ? weightedScore / totalWeight : 0.0;
    }

    /**
     * Construction du message de raison d'échec
     */
    private String buildFailureReason(ValidationSummary validation) {
        List<String> reasons = new ArrayList<>();

        if (validation.getDocumentValidation() != null && !validation.getDocumentValidation().isValid()) {
            reasons.add("Documents: " + String.join(", ", validation.getDocumentValidation().getErrors()));
        }

        if (validation.getFaceMatchValidation() != null && !validation.getFaceMatchValidation().isValid()) {
            reasons.add("Correspondance faciale: " + String.join(", ", validation.getFaceMatchValidation().getErrors()));
        }

        if (validation.getLivenessValidation() != null && !validation.getLivenessValidation().isValid()) {
            reasons.add("Test de vivacité: " + String.join(", ", validation.getLivenessValidation().getErrors()));
        }

        if (validation.getTemporalValidation() != null && !validation.getTemporalValidation().isValid()) {
            reasons.add("Cohérence temporelle: " + String.join(", ", validation.getTemporalValidation().getErrors()));
        }

        return String.join(" | ", reasons);
    }

    /**
     * Actions post-complétion
     */
    private void triggerPostCompletionActions(KycSession session) {
        CompletableFuture.runAsync(() -> {
            try {
                // Notification du résultat
                logger.info("Notification du résultat KYC - Session: {}, Résultat: {}", 
                    session.getSessionId(), session.getVerificationResult());

                // Archivage des données sensibles (si succès)
                if (session.getVerificationResult() == KycSession.VerificationResult.PASSED) {
                    archiveSensitiveData(session);
                }

                // Nettoyage des fichiers temporaires (si échec)
                if (session.getVerificationResult() == KycSession.VerificationResult.FAILED) {
                    scheduleCleanup(session);
                }

            } catch (Exception e) {
                logger.error("Erreur lors des actions post-complétion - Session: {}", session.getSessionId(), e);
            }
        });
    }

    /**
     * Archivage des données sensibles
     */
    private void archiveSensitiveData(KycSession session) {
        // Implémentation de l'archivage sécurisé
        logger.info("Archivage des données sensibles - Session: {}", session.getSessionId());
    }

    /**
     * Planification du nettoyage
     */
    private void scheduleCleanup(KycSession session) {
        // Implémentation du nettoyage différé
        logger.info("Nettoyage planifié - Session: {}", session.getSessionId());
    }

    /**
     * Calcul du score de confiance global
     */
    private Double calculateGlobalConfidence(KycSession session) {
        if (session.getRiskScore() != null) {
            return session.getRiskScore();
        }

        // Calcul en temps réel si pas encore finalisé
        ValidationSummary validation = performFinalValidation(session);
        return calculateFinalConfidence(validation);
    }

    /**
     * Calcul de la progression
     */
    private Integer calculateProgress(KycSession session) {
        switch (session.getSessionStatus()) {
            case INITIATED: return 10;
            case IN_PROGRESS: return 50;
            case PENDING_REVIEW: return 75;
            case COMPLETED: return 100;
            case APPROVED: return 100;
            case REJECTED:
            case FAILED:
            case EXPIRED:
            case CANCELLED: return 0;
            default: return 0;
        }
    }

    /**
     * Détails des étapes de la session
     */
    private Map<String, Object> getSessionSteps(KycSession session) {
        Map<String, Object> steps = new HashMap<>();
        
        // Récupération des détails de chaque étape
    List<Document> documents = documentRepository.findBySessionIdOrderByCreatedAtDesc(session.getSessionId());
        List<FaceMatch> faceMatches = faceMatchRepository.findBySessionIdOrderByCreatedAtDesc(session.getSessionId());
        List<LivenessResult> livenessResults = livenessResultRepository.findBySessionIdOrderByCreatedAtDesc(session.getSessionId());

        steps.put("documents", documents.size());
        steps.put("faceMatches", faceMatches.size());
        steps.put("livenessTests", livenessResults.size());

        return steps;
    }

    /**
     * Récupération d'une session avec validation
     */
    private KycSession getSession(UUID sessionId) {
        return kycSessionRepository.findById(sessionId)
            .orElseThrow(() -> new SessionNotFoundException("Session non trouvée: " + sessionId));
    }

    /**
     * Hachage de l'ID utilisateur pour anonymisation
     */
    private String hashUserId(String userId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(userId.getBytes());
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Algorithme SHA-256 non disponible", e);
        }
    }

    /**
     * Hachage du User-Agent
     */
    private String hashUserAgent(String userAgent) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(userAgent.getBytes());
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Algorithme SHA-256 non disponible", e);
        }
    }

    /**
     * Recherche de sessions avec pagination
     */
    @Transactional(readOnly = true)
    public Page<KycSession> findSessions(Pageable pageable) {
        return kycSessionRepository.findAll(pageable);
    }

    /**
     * Recherche de sessions par statut
     */
    @Transactional(readOnly = true)
    public List<KycSession> findSessionsByStatus(KycSession.SessionStatus status) {
        return kycSessionRepository.findBySessionStatus(status);
    }

    /**
     * Nettoyage des sessions expirées
     */
    @Transactional
    public int cleanupExpiredSessions() {
        List<KycSession> expiredSessions = kycSessionRepository.findExpiredSessions();
        
        for (KycSession session : expiredSessions) {
            session.setStatus(KycSession.SessionStatus.EXPIRED);
            session.setUpdatedAt(LocalDateTime.now());
        }
        
        kycSessionRepository.saveAll(expiredSessions);
        
        logger.info("Nettoyage effectué - {} sessions expirées", expiredSessions.size());
        
        return expiredSessions.size();
    }

    // Classes internes pour la validation
    public static class ValidationSummary {
        private ComponentValidation documentValidation;
        private ComponentValidation faceMatchValidation;
        private ComponentValidation livenessValidation;
        private ComponentValidation temporalValidation;

        public boolean isAllValid() {
            return (documentValidation == null || documentValidation.isValid()) &&
                   (faceMatchValidation == null || faceMatchValidation.isValid()) &&
                   (livenessValidation == null || livenessValidation.isValid()) &&
                   (temporalValidation == null || temporalValidation.isValid());
        }

        // Getters et setters
        public ComponentValidation getDocumentValidation() { return documentValidation; }
        public void setDocumentValidation(ComponentValidation documentValidation) { this.documentValidation = documentValidation; }
        
        public ComponentValidation getFaceMatchValidation() { return faceMatchValidation; }
        public void setFaceMatchValidation(ComponentValidation faceMatchValidation) { this.faceMatchValidation = faceMatchValidation; }
        
        public ComponentValidation getLivenessValidation() { return livenessValidation; }
        public void setLivenessValidation(ComponentValidation livenessValidation) { this.livenessValidation = livenessValidation; }
        
        public ComponentValidation getTemporalValidation() { return temporalValidation; }
        public void setTemporalValidation(ComponentValidation temporalValidation) { this.temporalValidation = temporalValidation; }
    }

    public static class ComponentValidation {
        private String componentName;
        private boolean valid = true;
        private double confidenceScore = 0.0;
        private List<String> errors = new ArrayList<>();

        public void addError(String error) {
            this.errors.add(error);
            this.valid = false;
        }

        // Getters et setters
        public String getComponentName() { return componentName; }
        public void setComponentName(String componentName) { this.componentName = componentName; }
        
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        
        public double getConfidenceScore() { return confidenceScore; }
        public void setConfidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; }
        
        public List<String> getErrors() { return errors; }
        public void setErrors(List<String> errors) { this.errors = errors; }
    }

    // Utilitaire : tente d'appeler le setter, sinon écrit directement dans le champ via reflection.
    private void setPropertyIfPossible(Object target, String propertyName, Object value) {
        if (target == null || propertyName == null) return;
        Class<?> clazz = target.getClass();
        String setterName = "set" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        try {
            // Tentative d'appeler le setter si présent
            java.lang.reflect.Method[] methods = clazz.getMethods();
            for (java.lang.reflect.Method m : methods) {
                if (m.getName().equals(setterName) && m.getParameterCount() == 1) {
                    // conversion simple si nécessaire
                    Class<?> paramType = m.getParameterTypes()[0];
                    Object arg = convertValueForType(value, paramType);
                    m.invoke(target, arg);
                    return;
                }
            }

            // Si pas de setter, écrire dans le champ directement
            java.lang.reflect.Field field = clazz.getDeclaredField(propertyName);
            field.setAccessible(true);
            Object arg = convertValueForType(value, field.getType());
            field.set(target, arg);
            return;

        } catch (NoSuchFieldException nsf) {
            logger.warn("Propriété '{}' introuvable sur {}.", propertyName, clazz.getName());
        } catch (Exception e) {
            logger.warn("Impossible d'assigner la propriété '{}' sur {} : {}", propertyName, clazz.getName(), e.getMessage());
        }
    }

    // Conversion basique pour types courants (Integer, Double, String, Boolean)
    private Object convertValueForType(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isInstance(value)) return value;

        String s = value.toString();
        try {
            if (targetType == Integer.class || targetType == int.class) return Integer.valueOf(s);
            if (targetType == Long.class || targetType == long.class) return Long.valueOf(s);
            if (targetType == Double.class || targetType == double.class) return Double.valueOf(s);
            if (targetType == Float.class || targetType == float.class) return Float.valueOf(s);
            if (targetType == Boolean.class || targetType == boolean.class) return Boolean.valueOf(s);
            if (targetType == String.class) return s;
        } catch (Exception ignore) {
            // fallback below
        }
        return value;
    }
}