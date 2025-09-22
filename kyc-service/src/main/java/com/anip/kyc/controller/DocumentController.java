package com.anip.kyc.controller;

import com.anip.kyc.dto.document.*;
import com.anip.kyc.dto.common.ApiResponse;
import com.anip.kyc.dto.common.ValidationErrorResponse;
import com.anip.kyc.service.DocumentService;
import com.anip.kyc.exception.DocumentProcessingException;
import com.anip.kyc.exception.DocumentValidationException;
import com.anip.kyc.exception.UnsupportedDocumentTypeException;
import com.anip.kyc.security.RequiresRole;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.anip.kyc.exception.DocumentNotFoundException;
import com.anip.kyc.exception.UnauthorizedDocumentAccessException;
import com.anip.kyc.exception.DocumentAlreadyValidatedException;
import com.anip.kyc.exception.DocumentNotProcessableException;
import com.anip.kyc.exception.DocumentDeletionNotAllowedException;
import com.anip.kyc.exception.SessionNotFoundException;
import com.anip.kyc.validation.ValidationException;

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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/v1/documents")
@Validated
@Tag(name = "Documents", description = "API de gestion des documents d'identité KYC")
@SecurityRequirement(name = "oauth2")
public class DocumentController {

    private static final Logger logger = LoggerFactory.getLogger(DocumentController.class);
    private final DocumentService documentService;

    // Configuration limites upload
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final List<String> ALLOWED_MIME_TYPES = List.of(
            "image/jpeg", "image/png", "image/webp", "application/pdf"
    );

    @Autowired
    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('SCOPE_kyc:document:write')")
    @Operation(summary = "Upload d'un document d'identité",
            description = "Upload et traitement initial d'un document avec OCR et validation basique")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Document uploadé et traité avec succès",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = DocumentUploadResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Fichier invalide ou type non supporté",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ValidationErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "Fichier trop volumineux"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Limite de taux dépassée"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Erreur de traitement du document")
    })
    public ResponseEntity<ApiResponse<DocumentUploadResponse>> uploadDocument(
            @Parameter(description = "Fichier document (JPEG, PNG, WebP, PDF)", required = true)
            @RequestParam("file") @NotNull MultipartFile file,

            @Parameter(description = "Type de document attendu", required = true)
            @RequestParam("documentType") @NotNull String documentType,

            @Parameter(description = "ID de session KYC associée", required = true)
            @RequestParam("sessionId") @NotNull UUID sessionId,

            @Parameter(description = "Options de traitement avancées")
            @RequestParam(value = "processingOptions", required = false) String processingOptions,

            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {

        try {
            logger.info("Upload document initialisé - sessionId: {}, type: {}, size: {}",
                    sessionId, documentType, file.getSize());

            validateUploadFile(file);

            DocumentUploadRequest uploadRequest = DocumentUploadRequest.builder()
                    .file(file)
                    .documentType(documentType)
                    .sessionId(sessionId)
                    .processingOptions(processingOptions)
                    .userId(extractUserId(jwt))
                    .clientIp(getClientIpAddress(request))
                    .userAgent(request.getHeader("User-Agent"))
                    .build();

            CompletableFuture<DocumentUploadResponse> processingFuture = documentService.processDocumentUpload(uploadRequest);

            DocumentUploadResponse response = processingFuture.get();

            logger.info("Document traité avec succès - documentId: {}, confidence: {}",
                    response.getDocumentId(), response.getConfidenceScore());

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(response, "Document uploadé et traité avec succès"));

        } catch (DocumentValidationException e) {
            logger.warn("Validation document échouée - sessionId: {}, erreur: {}",
                    sessionId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("DOCUMENT_VALIDATION_FAILED", e.getMessage()));

        } catch (UnsupportedDocumentTypeException e) {
            logger.warn("Type de document non supporté - type: {}", documentType);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("UNSUPPORTED_DOCUMENT_TYPE", e.getMessage()));

        } catch (DocumentProcessingException e) {
            logger.error("Erreur traitement document - sessionId: {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("DOCUMENT_PROCESSING_ERROR", "Erreur de traitement du document"));

        } catch (Exception e) {
            logger.error("Erreur inattendue upload document - sessionId: {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "Erreur interne du serveur"));
        }
    }

    @GetMapping("/{documentId}")
    @PreAuthorize("hasAuthority('SCOPE_kyc:document:read')")
    @Operation(summary = "Récupération d'un document", description = "Récupère les détails complets d'un document traité avec métadonnées")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Document récupéré avec succès", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = DocumentDetailsResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Document non trouvé"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Accès non autorisé au document")
    })
    public ResponseEntity<ApiResponse<DocumentDetailsResponse>> getDocument(
            @Parameter(description = "ID unique du document", required = true)
            @PathVariable UUID documentId,

            @Parameter(description = "Inclure les données OCR brutes")
            @RequestParam(value = "includeOcrData", defaultValue = "false") boolean includeOcrData,

            @Parameter(description = "Inclure l'historique de validation")
            @RequestParam(value = "includeValidationHistory", defaultValue = "false") boolean includeValidationHistory,

            @AuthenticationPrincipal Jwt jwt) {

        try {
            logger.debug("Récupération document - documentId: {}", documentId);

            DocumentDetailsRequest detailsRequest = DocumentDetailsRequest.builder()
                    .documentId(documentId)
                    .includeOcrData(includeOcrData)
                    .includeValidationHistory(includeValidationHistory)
                    .requestingUserId(extractUserId(jwt))
                    .build();

            DocumentDetailsResponse response = documentService.getDocumentDetails(detailsRequest);

            return ResponseEntity.ok(ApiResponse.success(response, "Document récupéré avec succès"));

        } catch (DocumentNotFoundException e) {
            logger.warn("Document non trouvé - documentId: {}", documentId);
            return ResponseEntity.notFound().build();

        } catch (UnauthorizedDocumentAccessException e) {
            logger.warn("Accès non autorisé au document - documentId: {}, userId: {}", documentId, extractUserId(jwt));
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("UNAUTHORIZED_ACCESS", "Accès non autorisé au document"));

        } catch (Exception e) {
            logger.error("Erreur récupération document - documentId: {}", documentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("INTERNAL_ERROR", "Erreur interne du serveur"));
        }
    }

    @PostMapping("/{documentId}/validate")
    @PreAuthorize("hasAuthority('SCOPE_kyc:document:validate')")
    @Operation(summary = "Validation avancée d'un document", description = "Lance une validation approfondie avec vérifications multiples et scoring")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Validation terminée avec succès", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = DocumentValidationResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Document non trouvé"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Document déjà validé ou en cours de validation")
    })
    public ResponseEntity<ApiResponse<DocumentValidationResponse>> validateDocument(
            @Parameter(description = "ID unique du document", required = true)
            @PathVariable UUID documentId,

            @Parameter(description = "Paramètres de validation avancée")
            @RequestBody @Valid DocumentValidationRequest validationRequest,

            @AuthenticationPrincipal Jwt jwt) {

        try {
            logger.info("Validation avancée démarrée - documentId: {}", documentId);

            validationRequest.setDocumentId(documentId);
            validationRequest.setValidatingUserId(extractUserId(jwt));

            DocumentValidationResponse response = documentService.performAdvancedValidation(validationRequest);

            logger.info("Validation terminée - documentId: {}, score: {}, statut: {}", documentId, response.getConfidenceScore(), response.getProcessingStatus());

            return ResponseEntity.ok(ApiResponse.success(response, "Validation terminée avec succès"));

        } catch (DocumentNotFoundException e) {
            return ResponseEntity.notFound().build();

        } catch (DocumentAlreadyValidatedException e) {
            logger.warn("Document déjà validé - documentId: {}", documentId);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error("ALREADY_VALIDATED", "Document déjà validé"));

        } catch (DocumentValidationException e) {
            logger.error("Erreur validation document - documentId: {}", documentId, e);
            return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION_ERROR", e.getMessage()));

        } catch (Exception e) {
            logger.error("Erreur inattendue validation - documentId: {}", documentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("INTERNAL_ERROR", "Erreur interne du serveur"));
        }
    }

    @PostMapping("/{documentId}/extract")
    @PreAuthorize("hasAuthority('SCOPE_kyc:document:extract')")
    @Operation(summary = "Extraction de données structurées", description = "Extrait les données structurées d'un document (nom, dates, numéros, etc.)")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Extraction réussie", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = DocumentExtractionResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Document non trouvé"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Document non traitable pour l'extraction")
    })
    public ResponseEntity<ApiResponse<DocumentExtractionResponse>> extractDocumentData(
            @Parameter(description = "ID unique du document", required = true)
            @PathVariable UUID documentId,

            @Parameter(description = "Paramètres d'extraction")
            @RequestBody @Valid DocumentExtractionRequest extractionRequest,

            @AuthenticationPrincipal Jwt jwt) {

        try {
            logger.info("Extraction de données démarrée - documentId: {}", documentId);

            extractionRequest.setDocumentId(documentId);
            extractionRequest.setRequestingUserId(extractUserId(jwt));

            DocumentExtractionResponse response = documentService.extractStructuredData(extractionRequest);

            logger.info("Extraction terminée - documentId: {}, champs extraits: {}", documentId, response.getExtractedFields().size());

            return ResponseEntity.ok(ApiResponse.success(response, "Extraction terminée avec succès"));

        } catch (DocumentNotFoundException e) {
            return ResponseEntity.notFound().build();

        } catch (DocumentNotProcessableException e) {
            logger.warn("Document non traitable - documentId: {}, raison: {}", documentId, e.getMessage());
            return ResponseEntity.unprocessableEntity().body(ApiResponse.error("NOT_PROCESSABLE", e.getMessage()));

        } catch (Exception e) {
            logger.error("Erreur extraction données - documentId: {}", documentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("INTERNAL_ERROR", "Erreur interne du serveur"));
        }
    }

    @GetMapping("/session/{sessionId}")
    @PreAuthorize("hasAuthority('SCOPE_kyc:session:read')")
    @Operation(summary = "Documents d'une session KYC", description = "Récupère tous les documents associés à une session KYC")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Liste récupérée avec succès", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = DocumentListResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Session non trouvée")
    })
    public ResponseEntity<ApiResponse<DocumentListResponse>> getSessionDocuments(
            @Parameter(description = "ID de la session KYC", required = true)
            @PathVariable UUID sessionId,

            @Parameter(description = "Type de document à filtrer")
            @RequestParam(value = "documentType", required = false) String documentType,

            @Parameter(description = "Statut de validation à filtrer")
            @RequestParam(value = "validationStatus", required = false) String validationStatus,

            @AuthenticationPrincipal Jwt jwt) {

        try {
            logger.debug("Récupération documents session - sessionId: {}", sessionId);

            DocumentListRequest listRequest = DocumentListRequest.builder()
                    .sessionId(sessionId)
                    .documentType(documentType)
                    .validationStatus(validationStatus)
                    .requestingUserId(extractUserId(jwt))
                    .build();

            DocumentListResponse response = documentService.getSessionDocuments(listRequest);

            return ResponseEntity.ok(ApiResponse.success(response, String.format("Liste récupérée: %d documents", response.getDocuments().size())));

        } catch (SessionNotFoundException e) {
            logger.warn("Session non trouvée - sessionId: {}", sessionId);
            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            logger.error("Erreur récupération documents session - sessionId: {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("INTERNAL_ERROR", "Erreur interne du serveur"));
        }
    }

    @DeleteMapping("/{documentId}")
    @PreAuthorize("hasAuthority('SCOPE_kyc:document:delete')")
    @RequiresRole("ADMIN")
    @Operation(summary = "Suppression d'un document", description = "Suppression sécurisée d'un document avec audit (admin uniquement)")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Document supprimé avec succès"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Document non trouvé"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Droits insuffisants"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Document ne peut pas être supprimé (utilisé dans validation)")
    })
    public ResponseEntity<ApiResponse<Void>> deleteDocument(
            @Parameter(description = "ID unique du document", required = true)
            @PathVariable UUID documentId,

            @Parameter(description = "Raison de la suppression", required = true)
            @RequestParam("reason") @NotNull @Size(min = 10, max = 500) String reason,

            @AuthenticationPrincipal Jwt jwt) {

        try {
            logger.warn("Suppression document initiée - documentId: {}, raison: {}", documentId, reason);

            DocumentDeletionRequest deletionRequest = DocumentDeletionRequest.builder()
                    .documentId(documentId)
                    .reason(reason)
                    .deletingUserId(extractUserId(jwt))
                    .adminAction(true)
                    .build();

            documentService.deleteDocument(deletionRequest);

            logger.warn("Document supprimé avec succès - documentId: {}", documentId);

            return ResponseEntity.noContent().build();

        } catch (DocumentNotFoundException e) {
            return ResponseEntity.notFound().build();

        } catch (DocumentDeletionNotAllowedException e) {
            logger.warn("Suppression non autorisée - documentId: {}, raison: {}", documentId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error("DELETION_NOT_ALLOWED", e.getMessage()));

        } catch (Exception e) {
            logger.error("Erreur suppression document - documentId: {}", documentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("INTERNAL_ERROR", "Erreur interne du serveur"));
        }
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('SCOPE_kyc:stats:read')")
    @Operation(summary = "Statistiques de traitement", description = "Récupère les statistiques de traitement des documents")
    public ResponseEntity<ApiResponse<DocumentStatsResponse>> getDocumentStats(
            @Parameter(description = "Date de début (YYYY-MM-DD)")
            @RequestParam(value = "startDate", required = false) String startDate,

            @Parameter(description = "Date de fin (YYYY-MM-DD)")
            @RequestParam(value = "endDate", required = false) String endDate,

            @AuthenticationPrincipal Jwt jwt) {

        try {
            DocumentStatsRequest statsRequest = DocumentStatsRequest.builder()
                    .startDate(startDate)
                    .endDate(endDate)
                    .requestingUserId(extractUserId(jwt))
                    .build();

            DocumentStatsResponse response = documentService.getDocumentStats(statsRequest);

            return ResponseEntity.ok(ApiResponse.success(response, "Statistiques récupérées"));

        } catch (Exception e) {
            logger.error("Erreur récupération statistiques documents", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("INTERNAL_ERROR", "Erreur interne du serveur"));
        }
    }

    private void validateUploadFile(MultipartFile file) throws DocumentValidationException {
        if (file.isEmpty()) {
            throw new DocumentValidationException("Fichier vide");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new DocumentValidationException(String.format("Fichier trop volumineux. Taille max: %d MB", MAX_FILE_SIZE / (1024 * 1024)));
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType)) {
            throw new DocumentValidationException("Type de fichier non supporté. Types autorisés: " + String.join(", ", ALLOWED_MIME_TYPES));
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.trim().isEmpty()) {
            throw new DocumentValidationException("Nom de fichier manquant");
        }

        if (filename.length() > 255) {
            throw new DocumentValidationException("Nom de fichier trop long");
        }

        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new DocumentValidationException("Nom de fichier contient des caractères non autorisés");
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

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<ValidationErrorResponse>> handleValidationException(ValidationException e) {
        logger.warn("Erreur de validation: {}", e.getMessage());

        ValidationErrorResponse errorResponse = ValidationErrorResponse.builder()
                .field(e.getField())
                .rejectedValue(e.getRejectedValue())
                .message(e.getMessage())
                .build();

        return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION_ERROR", "Erreur de validation", errorResponse));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<Void>> handleSecurityException(SecurityException e) {
        logger.warn("Violation de sécurité: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("SECURITY_VIOLATION", "Accès non autorisé"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception e) {
        logger.error("Erreur inattendue dans DocumentController", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("INTERNAL_ERROR", "Erreur interne du serveur"));
    }
}