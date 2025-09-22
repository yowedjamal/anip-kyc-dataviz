package com.anip.kyc.controller;

import com.anip.kyc.dto.common.ApiResponse;
import com.anip.kyc.dto.common.ValidationErrorResponse;
import com.anip.kyc.dto.session.*;
import com.anip.kyc.exception.*;
import com.anip.kyc.security.RequiresRole;
import com.anip.kyc.service.KycSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Contrôleur REST pour la gestion des sessions KYC
 * 
 * Endpoints sécurisés OAuth2 pour:
 * - Création et gestion de sessions KYC complètes
 * - Orchestration des étapes de validation
 * - Scoring et finalisation automatisée
 * - Suivi des états et transitions
 * - Rapports de conformité et audit
 * 
 * Workflow KYC:
 * 1. INITIATED → DOCUMENT_UPLOAD
 * 2. DOCUMENT_UPLOAD → FACE_VERIFICATION
 * 3. FACE_VERIFICATION → LIVENESS_CHECK
 * 4. LIVENESS_CHECK → VALIDATION
 * 5. VALIDATION → COMPLETED/FAILED
 * 
 * Sécurité:
 * - OAuth2 JWT avec scopes spécialisés
 * - Validation des transitions d'état
 * - Audit complet des opérations
 * - Rate limiting par session
 */
@RestController
@RequestMapping("/api/v1/kyc/sessions")
@Validated
@Tag(name = "Sessions KYC", description = "API de gestion des sessions KYC complètes")
@SecurityRequirement(name = "oauth2")
public class KycSessionController {

    private static final Logger logger = LoggerFactory.getLogger(KycSessionController.class);
    
    private final KycSessionService kycSessionService;
    
    @Autowired
    public KycSessionController(KycSessionService kycSessionService) {
        this.kycSessionService = kycSessionService;
    }
    
    /**
     * Création d'une nouvelle session KYC
     */
    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_kyc:session:create')")
    @Operation(
        summary = "Création d'une session KYC",
        description = "Initialise une nouvelle session KYC avec configuration personnalisée"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Liste des sessions récupérée",
            content = @Content(schema = @Schema(implementation = SessionListResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Paramètres de création invalides",
            content = @Content(schema = @Schema(implementation = ValidationErrorResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "Session KYC déjà en cours pour cet utilisateur"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "429",
            description = "Limite de sessions dépassée"
        )
    })
    public ResponseEntity<ApiResponse<SessionCreationResponse>> createSession(
            @Parameter(description = "Paramètres de création de session", required = true)
            @RequestBody @Valid SessionCreationRequest creationRequest,
            
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {
        
        try {
            logger.info("Création session KYC initiée - userId: {}, type: {}", 
                       extractUserId(jwt), creationRequest.getSessionType());
            
            // Enrichissement de la requête avec contexte utilisateur
            creationRequest.setUserId(extractUserId(jwt));
            creationRequest.setClientIp(getClientIpAddress(request));
            creationRequest.setUserAgent(request.getHeader("User-Agent"));
            creationRequest.setRequestId(generateRequestId());
            
            // Création de la session
            SessionCreationResponse response = kycSessionService.createSession(creationRequest);
            
            logger.info("Session KYC créée avec succès - sessionId: {}, statut: {}", 
                       response.getSessionId(), response.getStatus());
            
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Session KYC créée avec succès"));
                
        } catch (DuplicateSessionException e) {
            logger.warn("Session KYC déjà en cours - userId: {}", extractUserId(jwt));
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("DUPLICATE_SESSION", "Session KYC déjà en cours"));
                
        } catch (SessionValidationException e) {
            logger.warn("Paramètres de création invalides - userId: {}, erreur: {}", 
                       extractUserId(jwt), e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("INVALID_PARAMETERS", e.getMessage()));
                
        } catch (SessionLimitExceededException e) {
            logger.warn("Limite de sessions dépassée - userId: {}", extractUserId(jwt));
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error("SESSION_LIMIT_EXCEEDED", "Limite de sessions dépassée"));
                
        } catch (Exception e) {
            logger.error("Erreur création session KYC - userId: {}", extractUserId(jwt), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "Erreur interne du serveur"));
        }
    }
    
    /**
     * Récupération d'une session KYC
     */
    @GetMapping("/{sessionId}")
    @PreAuthorize("hasAuthority('SCOPE_kyc:session:read')")
    @Operation(
        summary = "Récupération d'une session KYC",
        description = "Récupère les détails complets d'une session avec historique et scoring"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Session récupérée avec succès",
            content = @Content(schema = @Schema(implementation = SessionDetailsResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Session non trouvée"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Accès non autorisé à la session"
        )
    })
    public ResponseEntity<ApiResponse<SessionDetailsResponse>> getSession(
            @Parameter(description = "ID unique de la session", required = true)
            @PathVariable UUID sessionId,
            
            @Parameter(description = "Inclure l'historique détaillé")
            @RequestParam(value = "includeHistory", defaultValue = "true") boolean includeHistory,
            
            @Parameter(description = "Inclure les détails de scoring")
            @RequestParam(value = "includeScoring", defaultValue = "true") boolean includeScoring,
            
            @Parameter(description = "Inclure les documents associés")
            @RequestParam(value = "includeDocuments", defaultValue = "false") boolean includeDocuments,
            
            @AuthenticationPrincipal Jwt jwt) {
        
        try {
            logger.debug("Récupération session KYC - sessionId: {}", sessionId);
            
            SessionDetailsRequest detailsRequest = SessionDetailsRequest.builder()
                .sessionId(sessionId)
                .includeHistory(includeHistory)
                .includeScoring(includeScoring)
                .includeDocuments(includeDocuments)
                .requestingUserId(extractUserId(jwt))
                .build();
            
            SessionDetailsResponse response = kycSessionService.getSessionDetails(detailsRequest);
            
            return ResponseEntity.ok(ApiResponse.success(response, "Session récupérée avec succès"));
            
        } catch (SessionNotFoundException e) {
            logger.warn("Session non trouvée - sessionId: {}", sessionId);
            return ResponseEntity.notFound().build();
            
        } catch (UnauthorizedSessionAccessException e) {
            logger.warn("Accès non autorisé à la session - sessionId: {}, userId: {}", 
                       sessionId, extractUserId(jwt));
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("UNAUTHORIZED_ACCESS", "Accès non autorisé à la session"));
                
        } catch (Exception e) {
            logger.error("Erreur récupération session - sessionId: {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "Erreur interne du serveur"));
        }
    }
    
    /**
     * Progression vers l'étape suivante
     */
    @PostMapping("/{sessionId}/next-step")
    @PreAuthorize("hasAuthority('SCOPE_kyc:session:progress')")
    @Operation(
        summary = "Progression vers l'étape suivante",
        description = "Fait progresser la session vers l'étape suivante du workflow KYC"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Progression réussie",
            content = @Content(schema = @Schema(implementation = SessionProgressResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Session non trouvée"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "Transition d'état non autorisée"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "422",
            description = "Conditions préalables non remplies"
        )
    })
    public ResponseEntity<ApiResponse<SessionProgressResponse>> progressToNextStep(
            @Parameter(description = "ID unique de la session", required = true)
            @PathVariable UUID sessionId,
            
            @Parameter(description = "Données de progression")
            @RequestBody @Valid SessionProgressRequest progressRequest,
            
            @AuthenticationPrincipal Jwt jwt) {
        
        try {
            logger.info("Progression session KYC - sessionId: {}, étape cible: {}", 
                       sessionId, progressRequest.getTargetStep());
            
            progressRequest.setSessionId(sessionId);
            progressRequest.setProgressingUserId(extractUserId(jwt));
            
            SessionProgressResponse response = kycSessionService.progressSession(progressRequest);
            
            logger.info("Progression réussie - sessionId: {}, nouvel état: {}", 
                       sessionId, response.getNewStatus());
            
            return ResponseEntity.ok(ApiResponse.success(response, "Progression réussie"));
            
        } catch (SessionNotFoundException e) {
            return ResponseEntity.notFound().build();
            
        } catch (InvalidStateTransitionException e) {
            logger.warn("Transition d'état invalide - sessionId: {}, erreur: {}", 
                       sessionId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("INVALID_TRANSITION", e.getMessage()));
                
        } catch (PrerequisitesNotMetException e) {
            logger.warn("Conditions préalables non remplies - sessionId: {}, erreur: {}", 
                       sessionId, e.getMessage());
            return ResponseEntity.unprocessableEntity()
                .body(ApiResponse.error("PREREQUISITES_NOT_MET", e.getMessage()));
                
        } catch (Exception e) {
            logger.error("Erreur progression session - sessionId: {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "Erreur interne du serveur"));
        }
    }
    
    /**
     * Validation complète d'une session
     */
    @PostMapping("/{sessionId}/validate")
    @PreAuthorize("hasAuthority('SCOPE_kyc:session:validate')")
    @Operation(
        summary = "Validation complète d'une session KYC",
        description = "Lance la validation complète de tous les composants avec scoring final"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Validation terminée",
            content = @Content(schema = @Schema(implementation = SessionValidationResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Session non trouvée"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "Session non prête pour validation"
        )
    })
    public ResponseEntity<ApiResponse<SessionValidationResponse>> validateSession(
            @Parameter(description = "ID unique de la session", required = true)
            @PathVariable UUID sessionId,
            
            @Parameter(description = "Paramètres de validation")
            @RequestBody @Valid SessionValidationRequest validationRequest,
            
            @AuthenticationPrincipal Jwt jwt) {
        
        try {
            logger.info("Validation complète démarrée - sessionId: {}", sessionId);
            
            validationRequest.setSessionId(sessionId);
            validationRequest.setValidatingUserId(extractUserId(jwt));
            
            // Validation asynchrone pour les traitements longs
            CompletableFuture<SessionValidationResponse> validationFuture = 
                kycSessionService.performCompleteValidation(validationRequest);
            
            SessionValidationResponse response = validationFuture.get();
            
            logger.info("Validation terminée - sessionId: {}, score: {}, statut: {}", 
                       sessionId, response.getOverallScore(), response.getProcessingStatus());
            
            return ResponseEntity.ok(ApiResponse.success(response, "Validation terminée"));
            
        } catch (SessionNotFoundException e) {
            return ResponseEntity.notFound().build();
            
        } catch (SessionNotReadyForValidationException e) {
            logger.warn("Session non prête pour validation - sessionId: {}", sessionId);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("NOT_READY", "Session non prête pour validation"));
                
        } catch (SessionValidationException e) {
            logger.error("Erreur validation session - sessionId: {}", sessionId, e);
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("VALIDATION_ERROR", e.getMessage()));
                
        } catch (Exception e) {
            logger.error("Erreur inattendue validation - sessionId: {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "Erreur interne du serveur"));
        }
    }
    
    /**
     * Finalisation d'une session KYC
     */
    @PostMapping("/{sessionId}/complete")
    @PreAuthorize("hasAuthority('SCOPE_kyc:session:complete')")
    @Operation(
        summary = "Finalisation d'une session KYC",
        description = "Finalise une session KYC avec génération du rapport final"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Session finalisée avec succès",
            content = @Content(schema = @Schema(implementation = SessionCompletionResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Session non trouvée"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "Session non validée ou déjà complétée"
        )
    })
    public ResponseEntity<ApiResponse<SessionCompletionResponse>> completeSession(
            @Parameter(description = "ID unique de la session", required = true)
            @PathVariable UUID sessionId,
            
            @Parameter(description = "Paramètres de finalisation")
            @RequestBody @Valid SessionCompletionRequest completionRequest,
            
            @AuthenticationPrincipal Jwt jwt) {
        
        try {
            logger.info("Finalisation session KYC - sessionId: {}", sessionId);
            
            completionRequest.setSessionId(sessionId);
            completionRequest.setCompletingUserId(extractUserId(jwt));
            
            SessionCompletionResponse response = kycSessionService.completeSession(completionRequest);
            
            logger.info("Session finalisée - sessionId: {}, résultat: {}, score final: {}", 
                       sessionId, response.getFinalStatus(), response.getFinalScore());
            
            return ResponseEntity.ok(ApiResponse.success(response, "Session finalisée avec succès"));
            
        } catch (SessionNotFoundException e) {
            return ResponseEntity.notFound().build();
            
        } catch (SessionNotValidatedException e) {
            logger.warn("Session non validée - sessionId: {}", sessionId);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("NOT_VALIDATED", "Session non validée"));
                
        } catch (SessionAlreadyCompletedException e) {
            logger.warn("Session déjà complétée - sessionId: {}", sessionId);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("ALREADY_COMPLETED", "Session déjà complétée"));
                
        } catch (SessionCompletionException e) {
            logger.error("Erreur finalisation session - sessionId: {}", sessionId, e);
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("COMPLETION_ERROR", e.getMessage()));
                
        } catch (Exception e) {
            logger.error("Erreur inattendue finalisation - sessionId: {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "Erreur interne du serveur"));
        }
    }
    
    /**
     * Liste des sessions avec pagination et filtres
     */
    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_kyc:session:list')")
    @Operation(
        summary = "Liste des sessions KYC",
        description = "Récupère la liste paginée des sessions avec filtres avancés"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Liste récupérée avec succès",
            content = @Content(schema = @Schema(implementation = SessionListResponse.class))
        )
    })
    public ResponseEntity<ApiResponse<SessionListResponse>> listSessions(
            @Parameter(description = "Statut des sessions à filtrer")
            @RequestParam(value = "status", required = false) String status,
            
            @Parameter(description = "Type de session à filtrer")
            @RequestParam(value = "sessionType", required = false) String sessionType,
            
            @Parameter(description = "Date de début (YYYY-MM-DD)")
            @RequestParam(value = "startDate", required = false) String startDate,
            
            @Parameter(description = "Date de fin (YYYY-MM-DD)")
            @RequestParam(value = "endDate", required = false) String endDate,
            
            @Parameter(description = "ID utilisateur à filtrer (admin uniquement)")
            @RequestParam(value = "userId", required = false) String userId,
            
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        
        try {
            logger.debug("Liste sessions KYC - statut: {}, type: {}", status, sessionType);
            
            SessionListRequest listRequest = SessionListRequest.builder()
                .status(status)
                .sessionType(sessionType)
                .startDate(startDate)
                .endDate(endDate)
                .userId(userId)
                .requestingUserId(extractUserId(jwt))
                .pageable(pageable)
                .build();
            
            SessionListResponse response = kycSessionService.listSessions(listRequest);
            
            return ResponseEntity.ok(ApiResponse.success(response, 
                String.format("Liste récupérée: %d sessions", response.getSessions().size())));
            
        } catch (Exception e) {
            logger.error("Erreur récupération liste sessions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "Erreur interne du serveur"));
        }
    }
    
    /**
     * Annulation d'une session KYC
     */
    @PostMapping("/{sessionId}/cancel")
    @PreAuthorize("hasAuthority('SCOPE_kyc:session:cancel')")
    @Operation(
        summary = "Annulation d'une session KYC",
        description = "Annule une session KYC en cours avec raison documentée"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Session annulée avec succès"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Session non trouvée"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "Session ne peut pas être annulée (déjà complétée)"
        )
    })
    public ResponseEntity<ApiResponse<Void>> cancelSession(
            @Parameter(description = "ID unique de la session", required = true)
            @PathVariable UUID sessionId,
            
            @Parameter(description = "Raison de l'annulation", required = true)
            @RequestParam("reason") @NotNull @Size(min = 10, max = 500) String reason,
            
            @AuthenticationPrincipal Jwt jwt) {
        
        try {
            logger.info("Annulation session KYC - sessionId: {}, raison: {}", sessionId, reason);
            
            SessionCancellationRequest cancellationRequest = SessionCancellationRequest.builder()
                .sessionId(sessionId)
                .reason(reason)
                .cancellingUserId(extractUserId(jwt))
                .build();
            
            kycSessionService.cancelSession(cancellationRequest);
            
            logger.info("Session annulée avec succès - sessionId: {}", sessionId);
            
            return ResponseEntity.ok(ApiResponse.success(null, "Session annulée avec succès"));
            
        } catch (SessionNotFoundException e) {
            return ResponseEntity.notFound().build();
            
        } catch (SessionCannotBeCancelledException e) {
            logger.warn("Session ne peut pas être annulée - sessionId: {}, raison: {}", 
                       sessionId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("CANNOT_CANCEL", e.getMessage()));
                
        } catch (Exception e) {
            logger.error("Erreur annulation session - sessionId: {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "Erreur interne du serveur"));
        }
    }
    
    /**
     * Rapport détaillé d'une session
     */
    @GetMapping("/{sessionId}/report")
    @PreAuthorize("hasAuthority('SCOPE_kyc:session:report')")
    @Operation(
        summary = "Rapport détaillé d'une session",
        description = "Génère un rapport complet de la session avec tous les détails de validation"
    )
    public ResponseEntity<ApiResponse<SessionReportResponse>> getSessionReport(
            @Parameter(description = "ID unique de la session", required = true)
            @PathVariable UUID sessionId,
            
            @Parameter(description = "Format du rapport")
            @RequestParam(value = "format", defaultValue = "DETAILED") String format,
            
            @Parameter(description = "Inclure les données brutes")
            @RequestParam(value = "includeRawData", defaultValue = "false") boolean includeRawData,
            
            @AuthenticationPrincipal Jwt jwt) {
        
        try {
            SessionReportRequest reportRequest = SessionReportRequest.builder()
                .sessionId(sessionId)
                .format(format)
                .includeRawData(includeRawData)
                .requestingUserId(extractUserId(jwt))
                .build();
            
            SessionReportResponse response = kycSessionService.generateSessionReport(reportRequest);
            
            return ResponseEntity.ok(ApiResponse.success(response, "Rapport généré avec succès"));
            
        } catch (SessionNotFoundException e) {
            return ResponseEntity.notFound().build();
            
        } catch (Exception e) {
            logger.error("Erreur génération rapport session - sessionId: {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "Erreur interne du serveur"));
        }
    }
    
    /**
     * Statistiques des sessions KYC
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('SCOPE_kyc:stats:read')")
    @RequiresRole("ANALYST")
    @Operation(
        summary = "Statistiques des sessions KYC",
        description = "Récupère les statistiques agrégées des sessions KYC"
    )
    public ResponseEntity<ApiResponse<SessionStatsResponse>> getSessionStats(
            @Parameter(description = "Date de début (YYYY-MM-DD)")
            @RequestParam(value = "startDate", required = false) String startDate,
            
            @Parameter(description = "Date de fin (YYYY-MM-DD)")
            @RequestParam(value = "endDate", required = false) String endDate,
            
            @Parameter(description = "Granularité des statistiques")
            @RequestParam(value = "granularity", defaultValue = "DAILY") String granularity,
            
            @AuthenticationPrincipal Jwt jwt) {
        
        try {
            SessionStatsRequest statsRequest = SessionStatsRequest.builder()
                .startDate(startDate)
                .endDate(endDate)
                .granularity(granularity)
                .requestingUserId(extractUserId(jwt))
                .build();
            
            SessionStatsResponse response = kycSessionService.getSessionStats(statsRequest);
            
            return ResponseEntity.ok(ApiResponse.success(response, "Statistiques récupérées"));
            
        } catch (Exception e) {
            logger.error("Erreur récupération statistiques sessions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "Erreur interne du serveur"));
        }
    }
    
    // Méthodes utilitaires privées
    
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
    
    private String generateRequestId() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * Gestion globale des exceptions pour ce contrôleur
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<ValidationErrorResponse>> handleValidationException(
            ValidationException e) {
        
        logger.warn("Erreur de validation: {}", e.getMessage());
        
        ValidationErrorResponse errorResponse = ValidationErrorResponse.builder()
            .field(e.getField())
            .rejectedValue(e.getRejectedValue())
            .message(e.getMessage())
            .build();
        
        return ResponseEntity.badRequest()
            .body(ApiResponse.error("VALIDATION_ERROR", "Erreur de validation", errorResponse));
    }
    
    @ExceptionHandler(SessionStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleSessionStateException(SessionStateException e) {
        logger.warn("Erreur d'état de session: {}", e.getMessage());
        
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error("SESSION_STATE_ERROR", e.getMessage()));
    }
    
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<Void>> handleSecurityException(SecurityException e) {
        logger.warn("Violation de sécurité: {}", e.getMessage());
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error("SECURITY_VIOLATION", "Accès non autorisé"));
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception e) {
        logger.error("Erreur inattendue dans KycSessionController", e);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("INTERNAL_ERROR", "Erreur interne du serveur"));
    }
}