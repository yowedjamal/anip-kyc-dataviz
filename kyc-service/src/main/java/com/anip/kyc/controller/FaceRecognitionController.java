package com.anip.kyc.controller;

import com.anip.kyc.dto.common.ApiResponse;
import com.anip.kyc.dto.common.ValidationErrorResponse;
import com.anip.kyc.dto.face.*;
import com.anip.kyc.exception.*;
import com.anip.kyc.security.RequiresRole;
import com.anip.kyc.service.FaceRecognitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Contrôleur REST pour la reconnaissance faciale et détection de vivacité
 * 
 * Endpoints sécurisés OAuth2 pour:
 * - Détection et analyse faciale avec OpenCV
 * - Comparaison biométrique avec FaceNet
 * - Tests de vivacité (liveness detection)
 * - Anti-spoofing et détection de fraude
 * - Gestion des templates biométriques
 * 
 * Fonctionnalités:
 * - Détection faciale temps réel
 * - Extraction d'encodages FaceNet
 * - Tests de vivacité multiples (blink, head movement, depth)
 * - Scoring de confiance avancé
 * - Chiffrement des données biométriques
 * 
 * Sécurité:
 * - OAuth2 JWT avec scopes biométriques
 * - Chiffrement AES-256 des templates
 * - Audit complet des opérations
 * - Rate limiting strict
 * - RGPD compliance (anonymisation)
 */
@RestController
@RequestMapping("/api/v1/face")
@Validated
@Tag(name = "Reconnaissance Faciale", description = "API de reconnaissance faciale et détection de vivacité")
@SecurityRequirement(name = "oauth2")
public class FaceRecognitionController {

    private static final Logger logger = LoggerFactory.getLogger(FaceRecognitionController.class);
    
    private final FaceRecognitionService faceRecognitionService;
    
    // Configuration limites
    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024; // 5 MB
    private static final List<String> ALLOWED_IMAGE_TYPES = List.of(
        "image/jpeg", "image/png", "image/webp"
    );
    private static final int MAX_FACE_DETECTION_TIMEOUT = 30; // secondes
    
    @Autowired
    public FaceRecognitionController(FaceRecognitionService faceRecognitionService) {
        this.faceRecognitionService = faceRecognitionService;
    }
    
    /**
     * Détection faciale sur une image
     */
    @PostMapping(value = "/detect", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('SCOPE_kyc:face:detect')")
    @Operation(
        summary = "Détection faciale",
        description = "Détecte et analyse les visages dans une image avec métadonnées de qualité"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Détection réussie",
            content = @Content(schema = @Schema(implementation = FaceDetectionResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Comparaison des visages effectuée",
            content = @Content(schema = @Schema(implementation = FaceRecognitionService.FaceComparisonResult.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "413",
            description = "Image trop volumineuse"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "422",
            description = "Qualité d'image insuffisante"
        )
    })
    public ResponseEntity<ApiResponse<FaceDetectionResponse>> detectFace(
            @Parameter(description = "Image contenant le visage (JPEG, PNG, WebP)", required = true)
            @RequestParam("image") @NotNull MultipartFile image,
            
            @Parameter(description = "ID de session KYC associée", required = true)
            @RequestParam("sessionId") @NotNull UUID sessionId,
            
            @Parameter(description = "Seuil de confiance minimum (0.0-1.0)")
            @RequestParam(value = "confidenceThreshold", defaultValue = "0.7") 
            @DecimalMin("0.0") @DecimalMax("1.0") double confidenceThreshold,
            
            @Parameter(description = "Détecter plusieurs visages")
            @RequestParam(value = "detectMultiple", defaultValue = "false") boolean detectMultiple,
            
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {
        
        try {
            logger.info("Détection faciale initiée - sessionId: {}, taille: {}", 
                       sessionId, image.getSize());
            
            // Validation de l'image
            validateImageFile(image);
            
            // Création de la requête de détection
            FaceDetectionRequest detectionRequest = FaceDetectionRequest.builder()
                .image(image)
                .sessionId(sessionId)
                .confidenceThreshold(confidenceThreshold)
                .detectMultiple(detectMultiple)
                .userId(extractUserId(jwt))
                .clientIp(getClientIpAddress(request))
                .build();
            
            // Traitement de la détection faciale
            FaceDetectionResponse response = faceRecognitionService.detectFaces(detectionRequest);
            
            logger.info("Détection terminée - sessionId: {}, visages détectés: {}, confiance max: {}", 
                       sessionId, response.getDetectedFaces().size(), response.getMaxConfidence());
            
            return ResponseEntity.ok(ApiResponse.success(response, "Détection faciale réussie"));
            
        } catch (NoFaceDetectedException e) {
            logger.warn("Aucun visage détecté - sessionId: {}", sessionId);
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("NO_FACE_DETECTED", "Aucun visage détecté dans l'image"));
                
        } catch (MultipleFacesDetectedException e) {
            logger.warn("Plusieurs visages détectés - sessionId: {}", sessionId);
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("MULTIPLE_FACES", "Plusieurs visages détectés"));
                
        } catch (ImageQualityException e) {
            logger.warn("Qualité d'image insuffisante - sessionId: {}", sessionId);
            return ResponseEntity.unprocessableEntity()
                .body(ApiResponse.error("POOR_IMAGE_QUALITY", e.getMessage()));
                
        } catch (FaceProcessingException e) {
            logger.error("Erreur traitement facial - sessionId: {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("FACE_PROCESSING_ERROR", "Erreur de traitement facial"));
                
        } catch (Exception e) {
            logger.error("Erreur inattendue détection faciale - sessionId: {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "Erreur interne du serveur"));
        }
    }
    
    /**
     * Comparaison biométrique entre deux visages
     */
    @PostMapping("/compare")
    @PreAuthorize("hasAuthority('SCOPE_kyc:face:compare')")
    @Operation(
        summary = "Comparaison biométrique",
        description = "Compare deux visages et calcule un score de similarité biométrique"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Comparaison réussie",
            content = @Content(schema = @Schema(implementation = FaceComparisonResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Images invalides ou problème de comparaison"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "422",
            description = "Comparaison non possible (qualité insuffisante)"
        )
    })
    public ResponseEntity<ApiResponse<FaceComparisonResponse>> compareFaces(
            @Parameter(description = "Requête de comparaison biométrique", required = true)
            @RequestBody @Valid FaceComparisonRequest comparisonRequest,
            
            @AuthenticationPrincipal Jwt jwt) {
        
        try {
            logger.info("Comparaison biométrique initiée - sessionId: {}, documentId: {}", 
                       comparisonRequest.getSessionId(), comparisonRequest.getDocumentId());
            
            comparisonRequest.setUserId(extractUserId(jwt));
            
            // Traitement de la comparaison biométrique
            FaceComparisonResponse response = faceRecognitionService.compareFaces(comparisonRequest);
            
            logger.info("Comparaison terminée - sessionId: {}, score similarité: {}, match: {}", 
                       comparisonRequest.getSessionId(), response.getMatchScore(), response.isMatch());
            
            return ResponseEntity.ok(ApiResponse.success(response, "Comparaison biométrique réussie"));
            
        } catch (IncomparableFacesException e) {
            logger.warn("Visages non comparables - sessionId: {}, raison: {}", 
                       comparisonRequest.getSessionId(), e.getMessage());
            return ResponseEntity.unprocessableEntity()
                .body(ApiResponse.error("INCOMPARABLE_FACES", e.getMessage()));
                
        } catch (BiometricProcessingException e) {
            logger.error("Erreur traitement biométrique - sessionId: {}", 
                        comparisonRequest.getSessionId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("BIOMETRIC_ERROR", "Erreur de traitement biométrique"));
                
        } catch (Exception e) {
            logger.error("Erreur inattendue comparaison - sessionId: {}", 
                        comparisonRequest.getSessionId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "Erreur interne du serveur"));
        }
    }
    
    /**
     * Test de vivacité (liveness detection)
     */
    @PostMapping(value = "/liveness", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('SCOPE_kyc:face:liveness')")
    @Operation(
        summary = "Test de vivacité",
        description = "Effectue un test de vivacité pour détecter si le visage est réel (anti-spoofing)"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Test de vivacité terminé",
            content = @Content(schema = @Schema(implementation = LivenessDetectionResponse.class))
        ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Image invalide ou test non possible"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "Spoofing détecté"
        )
    })
    public ResponseEntity<ApiResponse<LivenessDetectionResponse>> performLivenessCheck(
            @Parameter(description = "Image ou vidéo pour test de vivacité", required = true)
            @RequestParam("media") @NotNull MultipartFile media,
            
            @Parameter(description = "ID de session KYC associée", required = true)
            @RequestParam("sessionId") @NotNull UUID sessionId,
            
            @Parameter(description = "Type de test de vivacité")
            @RequestParam(value = "testType", defaultValue = "COMPREHENSIVE") String testType,
            
            @Parameter(description = "Seuil de vivacité (0.0-1.0)")
            @RequestParam(value = "livenessThreshold", defaultValue = "0.8") 
            @DecimalMin("0.0") @DecimalMax("1.0") double livenessThreshold,
            
            @AuthenticationPrincipal Jwt jwt) {
        
        try {
            logger.info("Test de vivacité initié - sessionId: {}, type: {}", sessionId, testType);
            
            // Validation du média
            validateMediaFile(media);
            
            // Création de la requête de test de vivacité
            LivenessDetectionRequest livenessRequest = LivenessDetectionRequest.builder()
                .media(media)
                .sessionId(sessionId)
                .testType(testType)
                .livenessThreshold(livenessThreshold)
                .userId(extractUserId(jwt))
                .build();
            
            // Traitement asynchrone du test de vivacité
            CompletableFuture<LivenessDetectionResponse> livenessFuture = 
                faceRecognitionService.performLivenessDetection(livenessRequest);
            
            LivenessDetectionResponse response = livenessFuture.get();
            
            logger.info("Test de vivacité terminé - sessionId: {}, vivant: {}, score: {}", 
                       sessionId, response.isAlive(), response.getLivenessScore());
            
            if (!response.isAlive()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("SPOOFING_DETECTED", "Tentative de spoofing détectée", response));
            }
            
            return ResponseEntity.ok(ApiResponse.success(response, "Test de vivacité réussi"));
            
        } catch (LivenessDetectionException e) {
            logger.warn("Échec test de vivacité - sessionId: {}, raison: {}", sessionId, e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("LIVENESS_FAILED", e.getMessage()));
                
        } catch (SpoofingDetectedException e) {
            logger.warn("Spoofing détecté - sessionId: {}", sessionId);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("SPOOFING_DETECTED", "Tentative de spoofing détectée"));
                
        } catch (Exception e) {
            logger.error("Erreur inattendue test vivacité - sessionId: {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "Erreur interne du serveur"));
        }
    }
    
    /**
     * Extraction d'encodage biométrique
     */
    @PostMapping(value = "/extract-encoding", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('SCOPE_kyc:face:extract')")
    @Operation(
        summary = "Extraction d'encodage biométrique",
        description = "Extrait l'encodage biométrique FaceNet d'un visage pour stockage sécurisé"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Encodage extrait avec succès",
            content = @Content(schema = @Schema(implementation = BiometricEncodingResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Image invalide ou extraction impossible"
        )
    })
    public ResponseEntity<ApiResponse<BiometricEncodingResponse>> extractBiometricEncoding(
            @Parameter(description = "Image du visage pour extraction", required = true)
            @RequestParam("image") @NotNull MultipartFile image,
            
            @Parameter(description = "ID de session KYC associée", required = true)
            @RequestParam("sessionId") @NotNull UUID sessionId,
            
            @Parameter(description = "Qualité minimale requise")
            @RequestParam(value = "minQuality", defaultValue = "0.7") 
            @DecimalMin("0.0") @DecimalMax("1.0") double minQuality,
            
            @AuthenticationPrincipal Jwt jwt) {
        
        try {
            logger.info("Extraction encodage biométrique - sessionId: {}", sessionId);
            
            validateImageFile(image);
            
            BiometricExtractionRequest extractionRequest = BiometricExtractionRequest.builder()
                .image(image)
                .sessionId(sessionId)
                .minQuality(minQuality)
                .userId(extractUserId(jwt))
                .build();
            
            BiometricEncodingResponse response = faceRecognitionService.extractBiometricEncoding(extractionRequest);
            
            logger.info("Encodage extrait - sessionId: {}, qualité: {}, dimensions: {}", 
                       sessionId, response.getQualityScore(), response.getEncodingDimensions());
            
            return ResponseEntity.ok(ApiResponse.success(response, "Encodage biométrique extrait"));
            
        } catch (InsufficientQualityException e) {
            logger.warn("Qualité insuffisante pour extraction - sessionId: {}", sessionId);
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("INSUFFICIENT_QUALITY", e.getMessage()));
                
        } catch (BiometricProcessingException e) {
            logger.error("Erreur extraction biométrique - sessionId: {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("EXTRACTION_ERROR", "Erreur d'extraction biométrique"));
                
        } catch (Exception e) {
            logger.error("Erreur inattendue extraction - sessionId: {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "Erreur interne du serveur"));
        }
    }
    
    /**
     * Analyse de qualité faciale
     */
    @PostMapping(value = "/analyze-quality", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('SCOPE_kyc:face:analyze')")
    @Operation(
        summary = "Analyse de qualité faciale",
        description = "Analyse la qualité d'une image faciale avec scoring détaillé"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Analyse terminée",
            content = @Content(schema = @Schema(implementation = FaceQualityResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Image invalide"
        )
    })
    public ResponseEntity<ApiResponse<FaceQualityResponse>> analyzeFaceQuality(
            @Parameter(description = "Image à analyser", required = true)
            @RequestParam("image") @NotNull MultipartFile image,
            
            @Parameter(description = "Analyse détaillée des métriques")
            @RequestParam(value = "detailedAnalysis", defaultValue = "true") boolean detailedAnalysis,
            
            @AuthenticationPrincipal Jwt jwt) {
        
        try {
            logger.debug("Analyse qualité faciale initiée");
            
            validateImageFile(image);
            
            FaceQualityRequest qualityRequest = FaceQualityRequest.builder()
                .image(image)
                .detailedAnalysis(detailedAnalysis)
                .userId(extractUserId(jwt))
                .build();
            
            FaceQualityResponse response = faceRecognitionService.analyzeFaceQuality(qualityRequest);
            
            return ResponseEntity.ok(ApiResponse.success(response, "Analyse de qualité terminée"));
            
        } catch (Exception e) {
            logger.error("Erreur analyse qualité faciale", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "Erreur interne du serveur"));
        }
    }
    
    /**
     * Récupération des templates biométriques d'une session
     */
    @GetMapping("/session/{sessionId}/templates")
    @PreAuthorize("hasAuthority('SCOPE_kyc:face:templates')")
    @Operation(
        summary = "Templates biométriques d'une session",
        description = "Récupère les templates biométriques chiffrés d'une session KYC"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Templates récupérés",
            content = @Content(schema = @Schema(implementation = BiometricTemplatesResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Session non trouvée"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Accès non autorisé"
        )
    })
    public ResponseEntity<ApiResponse<BiometricTemplatesResponse>> getSessionBiometricTemplates(
            @Parameter(description = "ID de la session KYC", required = true)
            @PathVariable UUID sessionId,
            
            @Parameter(description = "Inclure les métadonnées détaillées")
            @RequestParam(value = "includeMetadata", defaultValue = "true") boolean includeMetadata,
            
            @AuthenticationPrincipal Jwt jwt) {
        
        try {
            logger.debug("Récupération templates biométriques - sessionId: {}", sessionId);
            
            BiometricTemplatesRequest templatesRequest = BiometricTemplatesRequest.builder()
                .sessionId(sessionId)
                .includeMetadata(includeMetadata)
                .requestingUserId(extractUserId(jwt))
                .build();
            
            BiometricTemplatesResponse response = faceRecognitionService.getSessionBiometricTemplates(templatesRequest);
            
            return ResponseEntity.ok(ApiResponse.success(response, "Templates récupérés"));
            
        } catch (SessionNotFoundException e) {
            return ResponseEntity.notFound().build();
            
        } catch (UnauthorizedAccessException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("UNAUTHORIZED_ACCESS", "Accès non autorisé"));
                
        } catch (Exception e) {
            logger.error("Erreur récupération templates - sessionId: {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "Erreur interne du serveur"));
        }
    }
    
    /**
     * Suppression sécurisée des données biométriques
     */
    @DeleteMapping("/session/{sessionId}/biometric-data")
    @PreAuthorize("hasAuthority('SCOPE_kyc:face:delete')")
    @RequiresRole("ADMIN")
    @Operation(
        summary = "Suppression des données biométriques",
        description = "Suppression sécurisée et irréversible des données biométriques (admin uniquement)"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "204",
            description = "Données supprimées avec succès"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Session non trouvée"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Droits insuffisants"
        )
    })
    public ResponseEntity<ApiResponse<Void>> deleteBiometricData(
            @Parameter(description = "ID de la session KYC", required = true)
            @PathVariable UUID sessionId,
            
            @Parameter(description = "Raison de la suppression", required = true)
            @RequestParam("reason") @NotNull String reason,
            
            @AuthenticationPrincipal Jwt jwt) {
        
        try {
            logger.warn("Suppression données biométriques - sessionId: {}, raison: {}", sessionId, reason);
            
            BiometricDeletionRequest deletionRequest = BiometricDeletionRequest.builder()
                .sessionId(sessionId)
                .reason(reason)
                .deletingUserId(extractUserId(jwt))
                .adminAction(true)
                .build();
            
            faceRecognitionService.deleteBiometricData(deletionRequest);
            
            logger.warn("Données biométriques supprimées - sessionId: {}", sessionId);
            
            return ResponseEntity.noContent().build();
            
        } catch (SessionNotFoundException e) {
            return ResponseEntity.notFound().build();
            
        } catch (BiometricDeletionNotAllowedException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("DELETION_NOT_ALLOWED", e.getMessage()));
                
        } catch (Exception e) {
            logger.error("Erreur suppression données biométriques - sessionId: {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "Erreur interne du serveur"));
        }
    }
    
    /**
     * Statistiques de reconnaissance faciale
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('SCOPE_kyc:stats:read')")
    @Operation(
        summary = "Statistiques de reconnaissance faciale",
        description = "Récupère les statistiques agrégées de reconnaissance faciale et vivacité"
    )
    public ResponseEntity<ApiResponse<FaceRecognitionStatsResponse>> getFaceRecognitionStats(
            @Parameter(description = "Date de début (YYYY-MM-DD)")
            @RequestParam(value = "startDate", required = false) String startDate,
            
            @Parameter(description = "Date de fin (YYYY-MM-DD)")
            @RequestParam(value = "endDate", required = false) String endDate,
            
            @AuthenticationPrincipal Jwt jwt) {
        
        try {
            FaceRecognitionStatsRequest statsRequest = FaceRecognitionStatsRequest.builder()
                .startDate(startDate)
                .endDate(endDate)
                .requestingUserId(extractUserId(jwt))
                .build();
            
            FaceRecognitionStatsResponse response = faceRecognitionService.getFaceRecognitionStats(statsRequest);
            
            return ResponseEntity.ok(ApiResponse.success(response, "Statistiques récupérées"));
            
        } catch (Exception e) {
            logger.error("Erreur récupération statistiques reconnaissance faciale", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "Erreur interne du serveur"));
        }
    }
    
    // Méthodes utilitaires privées
    
    private void validateImageFile(MultipartFile image) throws FaceValidationException {
        if (image.isEmpty()) {
            throw new FaceValidationException("Image vide");
        }
        
        if (image.getSize() > MAX_IMAGE_SIZE) {
            throw new FaceValidationException(
                String.format("Image trop volumineuse. Taille max: %d MB", MAX_IMAGE_SIZE / (1024 * 1024))
            );
        }
        
        String contentType = image.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new FaceValidationException(
                "Type d'image non supporté. Types autorisés: " + String.join(", ", ALLOWED_IMAGE_TYPES)
            );
        }
        
        // Validation basique de l'en-tête du fichier
        try {
            byte[] header = new byte[8];
            image.getInputStream().read(header);
            
            // Vérification signature JPEG
            if (contentType.equals("image/jpeg")) {
                if (header[0] != (byte) 0xFF || header[1] != (byte) 0xD8) {
                    throw new FaceValidationException("Fichier JPEG corrompu");
                }
            }
            // Vérification signature PNG
            else if (contentType.equals("image/png")) {
                if (header[0] != (byte) 0x89 || header[1] != 'P' || header[2] != 'N' || header[3] != 'G') {
                    throw new FaceValidationException("Fichier PNG corrompu");
                }
            }
        } catch (Exception e) {
            throw new FaceValidationException("Impossible de valider le fichier image");
        }
    }
    
    private void validateMediaFile(MultipartFile media) throws FaceValidationException {
        // Validation étendue pour vidéos (pour tests de vivacité)
        validateImageFile(media); // Validation de base
        
        String contentType = media.getContentType();
        if (contentType != null && contentType.startsWith("video/")) {
            // Validation spécifique vidéo
            if (media.getSize() > MAX_IMAGE_SIZE * 2) { // Limite plus élevée pour vidéo
                throw new FaceValidationException("Vidéo trop volumineuse");
            }
        }
    }
    
    private String extractUserId(Jwt jwt) {
        return jwt.getClaimAsString("sub");
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Gestion globale des exceptions pour ce contrôleur
     */
    @ExceptionHandler(FaceValidationException.class)
    public ResponseEntity<ApiResponse<ValidationErrorResponse>> handleFaceValidationException(
            FaceValidationException e) {
        
        logger.warn("Erreur de validation faciale: {}", e.getMessage());
        
        ValidationErrorResponse errorResponse = ValidationErrorResponse.builder()
            .field("image")
            .rejectedValue("invalid")
            .message(e.getMessage())
            .build();
        
        return ResponseEntity.badRequest()
            .body(ApiResponse.error("FACE_VALIDATION_ERROR", "Erreur de validation faciale", errorResponse));
    }
    
    @ExceptionHandler(FaceProcessingException.class)
    public ResponseEntity<ApiResponse<Void>> handleFaceProcessingException(FaceProcessingException e) {
        logger.error("Erreur de traitement facial: {}", e.getMessage());
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("FACE_PROCESSING_ERROR", "Erreur de traitement facial"));
    }
    
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<Void>> handleSecurityException(SecurityException e) {
        logger.warn("Violation de sécurité: {}", e.getMessage());
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error("SECURITY_VIOLATION", "Accès non autorisé"));
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception e) {
        logger.error("Erreur inattendue dans FaceRecognitionController", e);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("INTERNAL_ERROR", "Erreur interne du serveur"));
    }
}