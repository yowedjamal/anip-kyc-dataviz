package com.anip.kyc.service;

import com.anip.kyc.models.Document;
import com.anip.kyc.models.KycSession;
import com.anip.kyc.repository.DocumentRepository;
import com.anip.kyc.repository.KycSessionRepository;
import com.anip.kyc.exception.DocumentValidationException;
import com.anip.kyc.exception.UnsupportedDocumentTypeException;
import com.anip.kyc.config.security.EncryptionService;
import com.anip.kyc.dto.DocumentUploadRequest;
import com.anip.kyc.dto.DocumentValidationResult;
import com.anip.kyc.dto.ExtractedDocumentData;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

/**
 * Service de gestion des documents avec OCR et validation
 * Utilise Tesseract pour l'extraction de texte et OpenCV pour le traitement d'image
 * Conforme aux exigences RGPD avec chiffrement des données extraites
 */
@Service
@Transactional
public class DocumentService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentService.class);

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private KycSessionRepository kycSessionRepository;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Value("${app.storage.documents.path}")
    private String documentStoragePath;

    @Value("${app.ocr.tesseract.datapath}")
    private String tesseractDataPath;

    @Value("${app.ocr.confidence.threshold:0.7}")
    private double ocrConfidenceThreshold;

    // Types de documents supportés
    private static final Map<String, List<String>> SUPPORTED_MIME_TYPES = Map.of(
        "PASSPORT", Arrays.asList("image/jpeg", "image/png", "image/tiff", "application/pdf"),
        "ID_CARD", Arrays.asList("image/jpeg", "image/png", "image/tiff"),
        "DRIVING_LICENSE", Arrays.asList("image/jpeg", "image/png", "image/tiff")
    );

    // Taille maximale des fichiers (10MB)
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    // Patterns pour validation des données extraites
    private static final Map<String, Pattern> VALIDATION_PATTERNS = Map.of(
        "PASSPORT_NUMBER", Pattern.compile("^[A-Z0-9]{6,12}$"),
        "ID_CARD_NUMBER", Pattern.compile("^[A-Z0-9]{8,15}$"),
        "DRIVING_LICENSE_NUMBER", Pattern.compile("^[A-Z0-9]{8,20}$"),
        "DATE", Pattern.compile("^\\d{2}/\\d{2}/\\d{4}$"),
        "NAME", Pattern.compile("^[A-ZÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖØÙÚÛÜÝÞŸ\\s\\-']{2,50}$")
    );

    static {
        // Tentative de chargement via nu.pattern.OpenCV si présent (openpnp wrapper).
        // Si absent, Bytedeco opencv-platform gère le chargement natif automatiquement.
        try {
            Class<?> openCvWrapper = Class.forName("nu.pattern.OpenCV");
            try {
                java.lang.reflect.Method m = openCvWrapper.getMethod("loadShared");
                m.invoke(null);
                logger.info("Loaded OpenCV native library via nu.pattern.OpenCV.loadShared() (DocumentService)");
            } catch (NoSuchMethodException nsme1) {
                try {
                    java.lang.reflect.Method m2 = openCvWrapper.getMethod("loadLocally");
                    m2.invoke(null);
                    logger.info("Loaded OpenCV native library via nu.pattern.OpenCV.loadLocally() (DocumentService)");
                } catch (Throwable nsme2) {
                    logger.warn("nu.pattern.OpenCV present but no known loader method succeeded in DocumentService: {}", nsme2 == null ? "unknown" : nsme2.getMessage());
                }
            } catch (Throwable t) {
                logger.warn("nu.pattern.OpenCV.loadShared() invocation failed in DocumentService: {}", t.getMessage());
            }
        } catch (ClassNotFoundException ignored) {
            logger.debug("nu.pattern.OpenCV wrapper not found on classpath (DocumentService)");
        } catch (Throwable t) {
            logger.warn("Unexpected error while attempting to use nu.pattern.OpenCV in DocumentService: {}", t.getMessage());
        }

        try {
            System.loadLibrary(org.opencv.core.Core.NATIVE_LIBRARY_NAME);
            logger.info("Loaded OpenCV native library via System.loadLibrary (DocumentService): {}", org.opencv.core.Core.NATIVE_LIBRARY_NAME);
        } catch (UnsatisfiedLinkError ule) {
            logger.debug("System.loadLibrary failed in DocumentService: {}", ule.getMessage());
        } catch (Throwable t) {
            logger.warn("Unexpected error during System.loadLibrary for OpenCV in DocumentService: {}", t.getMessage());
        }
    }

    /**
     * Upload et traitement d'un document
     */
    public CompletableFuture<Document> uploadDocument(UUID sessionId, DocumentUploadRequest request, MultipartFile file) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Validation de la session
                KycSession session = kycSessionRepository.findById(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("Session KYC non trouvée: " + sessionId));

                if (!session.canUploadDocument()) {
                    throw new IllegalStateException("La session ne permet pas l'upload de documents: " + session.getSessionStatus());
                }

                // Validation du fichier
                validateFile(file, request.getDocumentType());

                // Sauvegarde du fichier
                String encryptedFilePath = saveEncryptedFile(file, sessionId, request.getDocumentType());

                // Calcul du hash du fichier (non utilisé actuellement)
                // String fileHash = calculateFileHash(file.getBytes());

                // Création de l'entité Document
                Document document = new Document();
                document.setSessionId(sessionId);
                document.setDocumentType(Document.DocumentType.valueOf(request.getDocumentType()));
                // Store encrypted file path (file_path is expected to be encrypted per model comment)
                document.setFilePath(encryptionService.encrypt(encryptedFilePath));
                document.setFileSize(file.getSize());
                document.setMimeType(file.getContentType());
                document.setProcessingStatus(Document.ProcessingStatus.PENDING);

                // Sauvegarde en base
                document = documentRepository.save(document);

                // Traitement OCR asynchrone
                processDocumentAsync(document, file.getBytes());

                logger.info("Document uploadé avec succès - ID: {}, Session: {}, Type: {}", 
                    document.getDocumentId(), sessionId, request.getDocumentType());

                return document;

            } catch (Exception e) {
                logger.error("Erreur lors de l'upload du document - Session: {}, Type: {}", 
                    sessionId, request.getDocumentType(), e);
                throw new DocumentValidationException("Erreur lors de l'upload: " + e.getMessage(), e);
            }
        });
    }

    // --- Adapter methods for controller DTO package com.anip.kyc.dto.document ---
    public java.util.concurrent.CompletableFuture<com.anip.kyc.dto.document.DocumentUploadResponse> processDocumentUpload(com.anip.kyc.dto.document.DocumentUploadRequest uploadRequest) {
        // Delegate to internal uploadDocument method
        try {
            java.util.UUID sessionId = uploadRequest.getSessionId();
            org.springframework.web.multipart.MultipartFile file = uploadRequest.getFile();

            // Only implement the getters actually used by internal uploadDocument
            java.util.concurrent.CompletableFuture<Document> f = uploadDocument(sessionId, new com.anip.kyc.dto.DocumentUploadRequest(){
                public org.springframework.web.multipart.MultipartFile getFile(){ return file; }
                public String getDocumentType(){ return uploadRequest.getDocumentType(); }
                public java.util.UUID getSessionId(){ return sessionId; }
            }, file);

            return f.thenApply(doc -> {
                com.anip.kyc.dto.document.DocumentUploadResponse resp = new com.anip.kyc.dto.document.DocumentUploadResponse();
                resp.setDocumentId(doc.getDocumentId());
                resp.setConfidenceScore(doc.getConfidenceScore() == null ? 0.0 : doc.getConfidenceScore());
                return resp;
            });
        } catch (Exception e) {
            java.util.concurrent.CompletableFuture<com.anip.kyc.dto.document.DocumentUploadResponse> failed = new java.util.concurrent.CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }

    public com.anip.kyc.dto.document.DocumentDetailsResponse getDocumentDetails(com.anip.kyc.dto.document.DocumentDetailsRequest detailsRequest) {
        Document doc = getDocument(detailsRequest.getDocumentId());
        com.anip.kyc.dto.document.DocumentDetailsResponse r = new com.anip.kyc.dto.document.DocumentDetailsResponse();
        r.setDocumentId(doc.getDocumentId());
        r.setDocumentType(doc.getDocumentType() == null ? null : doc.getDocumentType().name());
        // DocumentDetailsResponse exposes OCR lines and basic metadata; include OCR lines if extracted
        if (doc.hasExtractedText()) {
            // split extracted text by lines (best-effort)
            String extracted = doc.getExtractedText();
            java.util.List<String> lines = java.util.Arrays.asList(extracted.split("\\r?\\n"));
            r.setOcrLines(lines);
        }
        return r;
    }

    public com.anip.kyc.dto.document.DocumentValidationResponse performAdvancedValidation(com.anip.kyc.dto.document.DocumentValidationRequest validationRequest) {
        // reuse internal extraction and validation
        Document doc = getDocument(validationRequest.getDocumentId());
        ExtractedDocumentData extracted = extractStructuredData(doc.getExtractedText(), doc.getDocumentType());
        DocumentValidationResult result = validateExtractedData(extracted, doc.getDocumentType());
        com.anip.kyc.dto.document.DocumentValidationResponse resp = new com.anip.kyc.dto.document.DocumentValidationResponse();
        // DocumentValidationResponse uses validationScore/validationStatus aliases
        resp.setValidationScore(result.getConfidenceScore());
        resp.setValidationStatus(result.isValid() ? "VALID" : "INVALID");
        return resp;
    }

    public com.anip.kyc.dto.document.DocumentExtractionResponse extractStructuredData(com.anip.kyc.dto.document.DocumentExtractionRequest extractionRequest) {
        Document doc = getDocument(extractionRequest.getDocumentId());
        ExtractedDocumentData extracted = extractStructuredData(doc.getExtractedText(), doc.getDocumentType());
        com.anip.kyc.dto.document.DocumentExtractionResponse resp = new com.anip.kyc.dto.document.DocumentExtractionResponse();
        // ExtractedDocumentData exposes getExtractedFields()
        resp.setExtractedFields(extracted.getExtractedFields());
        return resp;
    }

    public com.anip.kyc.dto.document.DocumentListResponse getSessionDocuments(com.anip.kyc.dto.document.DocumentListRequest listRequest) {
        List<Document> docs = getDocumentsBySession(listRequest.getSessionId());
        com.anip.kyc.dto.document.DocumentListResponse resp = new com.anip.kyc.dto.document.DocumentListResponse();
        java.util.List<com.anip.kyc.dto.document.DocumentSummary> sums = new java.util.ArrayList<>();
        for (Document d : docs) {
            com.anip.kyc.dto.document.DocumentSummary s = new com.anip.kyc.dto.document.DocumentSummary();
            s.setDocumentId(d.getDocumentId());
            s.setDocumentType(d.getDocumentType() == null ? null : d.getDocumentType().name());
            s.setProcessingStatus(d.getProcessingStatus() == null ? null : d.getProcessingStatus().name());
            sums.add(s);
        }
        resp.setDocuments(sums);
        return resp;
    }

    public com.anip.kyc.dto.document.DocumentStatsResponse getDocumentStats(com.anip.kyc.dto.document.DocumentStatsRequest statsRequest) {
        com.anip.kyc.dto.document.DocumentStatsResponse r = new com.anip.kyc.dto.document.DocumentStatsResponse();
        r.setTotalDocuments(getDocumentsBySession(statsRequest.getSessionId()).size());
        return r;
    }

    /**
     * Traitement OCR asynchrone d'un document
     */
    private void processDocumentAsync(Document document, byte[] fileBytes) {
        CompletableFuture.runAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();

                // Prétraitement de l'image avec OpenCV
                Mat processedImage = preprocessImage(fileBytes);

                // Extraction OCR avec Tesseract
                String extractedText = performOCR(processedImage);

                // Validation et extraction des données structurées
                ExtractedDocumentData extractedData = extractStructuredData(extractedText, document.getDocumentType());

                // Validation des données extraites
                DocumentValidationResult validationResult = validateExtractedData(extractedData, document.getDocumentType());

                // Mise à jour du document (conformes au modèle Document)
                // Store extracted text (encrypted) and structured JSON in metadata
                document.setExtractedText(encryptionService.encrypt(extractedData.toJson()));
                document.setConfidenceScore(extractedData.getConfidenceScore());
                document.setProcessingStatus(validationResult.isValid() ? Document.ProcessingStatus.COMPLETED : Document.ProcessingStatus.FAILED);

                Map<String, Object> meta = new HashMap<>();
                meta.put("ocrTextEncrypted", encryptionService.encrypt(extractedText));
                meta.put("errors", validationResult.getErrors());
                meta.put("processingDurationMs", (int)(System.currentTimeMillis() - startTime));
                try {
                    document.setMetadata(objectMapper.writeValueAsString(meta));
                } catch (com.fasterxml.jackson.core.JsonProcessingException jpe) {
                    logger.warn("Erreur sérialisation metadata OCR", jpe);
                    document.setMetadata("{}");
                }
                document.setProcessedAt(java.time.LocalDateTime.now());
                document.setUpdatedAt(java.time.LocalDateTime.now());

                documentRepository.save(document);

                // Mise à jour du statut de la session
                updateSessionStatus(document.getSessionId(), validationResult.isValid());

                logger.info("Traitement OCR terminé - Document: {}, Confiance: {:.2f}, Statut: {}", 
                    document.getDocumentId(), extractedData.getConfidenceScore(), document.getProcessingStatus());

                } catch (Exception e) {
                logger.error("Erreur lors du traitement OCR - Document: {}", document.getDocumentId(), e);
                
                // Mise à jour du statut en erreur
                document.setProcessingStatus(Document.ProcessingStatus.FAILED);
                Map<String, Object> meta = new HashMap<>();
                meta.put("error", "Erreur de traitement OCR: " + e.getMessage());
                try {
                    document.setMetadata(objectMapper.writeValueAsString(meta));
                } catch (com.fasterxml.jackson.core.JsonProcessingException jpe) {
                    logger.warn("Erreur sérialisation metadata erreur OCR", jpe);
                    document.setMetadata("{}");
                }
                document.setUpdatedAt(java.time.LocalDateTime.now());
                documentRepository.save(document);
            }
        });
    }

    /**
     * Prétraitement de l'image avec OpenCV
     */
    private Mat preprocessImage(byte[] imageBytes) {
        // Chargement de l'image
        Mat image = Imgcodecs.imdecode(new MatOfByte(imageBytes), Imgcodecs.IMREAD_COLOR);
        
        if (image.empty()) {
            throw new DocumentValidationException("Impossible de charger l'image");
        }

        // Conversion en niveaux de gris
        Mat gray = new Mat();
        Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);

        // Amélioration du contraste
        Mat enhanced = new Mat();
        Imgproc.equalizeHist(gray, enhanced);

        // Débruitage
        Mat denoised = new Mat();
        Imgproc.bilateralFilter(enhanced, denoised, 9, 75, 75);

        // Détection des contours pour améliorer la lisibilité
        Mat edges = new Mat();
        Imgproc.Canny(denoised, edges, 50, 150);

        // Fermeture morphologique pour connecter les caractères
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2, 2));
        Mat closed = new Mat();
        Imgproc.morphologyEx(edges, closed, Imgproc.MORPH_CLOSE, kernel);

        // Combinaison de l'image débruitée et des contours
        Mat result = new Mat();
        Core.addWeighted(denoised, 0.8, closed, 0.2, 0, result);

        logger.debug("Prétraitement d'image terminé - Dimensions: {}x{}", result.width(), result.height());

        return result;
    }

    /**
     * Extraction OCR avec Tesseract
     */
    private String performOCR(Mat processedImage) throws TesseractException {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(tesseractDataPath);
        tesseract.setLanguage("fra+eng"); // Support français et anglais
        tesseract.setPageSegMode(6); // Bloc de texte uniforme
        tesseract.setOcrEngineMode(1); // Neural nets LSTM engine

        // Configuration pour améliorer la précision
        // Some Tesseract bindings expose setTessVariable, others don't; use reflection fallback
        safeSetTessVariable(tesseract, "tessedit_char_whitelist", 
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789àáâãäåæçèéêëìíîïðñòóôõöøùúûüýþÿ .-/");

        // Conversion Mat vers BufferedImage pour Tesseract
        MatOfByte matOfByte = new MatOfByte();
        Imgcodecs.imencode(".png", processedImage, matOfByte);
        byte[] byteArray = matOfByte.toArray();

        try {
            java.awt.image.BufferedImage bufferedImage = javax.imageio.ImageIO.read(
                new java.io.ByteArrayInputStream(byteArray));
            
            String extractedText = tesseract.doOCR(bufferedImage);
            
            logger.debug("OCR effectué - Texte extrait: {} caractères", extractedText.length());
            
            return extractedText.trim();
            
        } catch (IOException e) {
            throw new TesseractException("Erreur de conversion d'image pour OCR", e);
        }
    }

    private void safeSetTessVariable(Tesseract tesseract, String key, String value) {
        try {
            java.lang.reflect.Method m = tesseract.getClass().getMethod("setTessVariable", String.class, String.class);
            m.invoke(tesseract, key, value);
        } catch (NoSuchMethodException nsme) {
            // If binding does not support setTessVariable, ignore silently
        } catch (Exception e) {
            // Log but continue
            logger.warn("Impossible d'appliquer tess variable: {}", key, e);
        }
    }

    /**
     * Extraction de données structurées à partir du texte OCR
     */
    private ExtractedDocumentData extractStructuredData(String ocrText, Document.DocumentType documentType) {
        ExtractedDocumentData data = new ExtractedDocumentData();
        data.setRawText(ocrText);
        data.setDocumentType(documentType);

        double totalConfidence = 0.0;
        int fieldCount = 0;

        // Support both granular types and legacy aliases
        switch (documentType) {
            case PASSPORT:
                data = extractPassportData(ocrText, data);
                break;
            case ID_CARD:
            case ID_CARD_FRONT:
            case ID_CARD_BACK:
                data = extractIdCardData(ocrText, data);
                break;
            case DRIVING_LICENSE:
            case DRIVING_LICENSE_FRONT:
            case DRIVING_LICENSE_BACK:
                data = extractDrivingLicenseData(ocrText, data);
                break;
            default:
                // Other types: try a generic extraction
                data = extractIdCardData(ocrText, data);
                break;
        }

        // Calcul de la confiance globale
        if (fieldCount > 0) {
            data.setConfidenceScore(totalConfidence / fieldCount);
        } else {
            data.setConfidenceScore(0.0);
        }

        return data;
    }

    /**
     * Extraction spécifique pour passeport
     */
    private ExtractedDocumentData extractPassportData(String text, ExtractedDocumentData data) {
        Map<String, Object> fields = new HashMap<>();
        double confidence = 0.0;

        // Recherche du numéro de passeport
        Pattern passportPattern = Pattern.compile("(?:PASSPORT|PASSEPORT)\\s*[NO|N°]?\\s*([A-Z0-9]{6,12})", 
            Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = passportPattern.matcher(text);
        if (matcher.find()) {
            fields.put("passportNumber", matcher.group(1));
            confidence += 0.9;
        }

        // Recherche des noms
        Pattern namePattern = Pattern.compile("(?:NOM|SURNAME|NAME)\\s*[:/]?\\s*([A-ZÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖØÙÚÛÜÝÞŸ\\s\\-']+)", 
            Pattern.CASE_INSENSITIVE);
        matcher = namePattern.matcher(text);
        if (matcher.find()) {
            fields.put("surname", matcher.group(1).trim());
            confidence += 0.8;
        }

        // Recherche des prénoms
        Pattern givenNamesPattern = Pattern.compile("(?:PRENOM|GIVEN NAMES|PRÉNOMS)\\s*[:/]?\\s*([A-ZÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖØÙÚÛÜÝÞŸ\\s\\-']+)", 
            Pattern.CASE_INSENSITIVE);
        matcher = givenNamesPattern.matcher(text);
        if (matcher.find()) {
            fields.put("givenNames", matcher.group(1).trim());
            confidence += 0.8;
        }

        // Recherche de la date de naissance
        Pattern birthDatePattern = Pattern.compile("(?:BIRTH|NAISSANCE).*?(\\d{2}/\\d{2}/\\d{4})", 
            Pattern.CASE_INSENSITIVE);
        matcher = birthDatePattern.matcher(text);
        if (matcher.find()) {
            fields.put("dateOfBirth", matcher.group(1));
            confidence += 0.7;
        }

        // Recherche de la date d'expiration
        Pattern expiryPattern = Pattern.compile("(?:EXPIRY|EXPIRATION|EXPIRE).*?(\\d{2}/\\d{2}/\\d{4})", 
            Pattern.CASE_INSENSITIVE);
        matcher = expiryPattern.matcher(text);
        if (matcher.find()) {
            fields.put("expiryDate", matcher.group(1));
            confidence += 0.7;
        }

        data.setExtractedFields(fields);
        data.setConfidenceScore(confidence / 4.2);

        return data;
    }

    /**
     * Extraction spécifique pour carte d'identité
     */
    private ExtractedDocumentData extractIdCardData(String text, ExtractedDocumentData data) {
        Map<String, Object> fields = new HashMap<>();
        double confidence = 0.0;

        // Recherche du numéro de carte
        Pattern idPattern = Pattern.compile("(?:CARTE|ID|IDENTITE).*?([A-Z0-9]{8,15})", 
            Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = idPattern.matcher(text);
        if (matcher.find()) {
            fields.put("idNumber", matcher.group(1));
            confidence += 0.9;
        }

        // Extraction similaire pour nom, prénom, date de naissance
        // ... (code similaire au passeport)

        data.setExtractedFields(fields);
        data.setConfidenceScore(confidence / 3.5); // Ajustement selon les champs

        return data;
    }

    /**
     * Extraction spécifique pour permis de conduire
     */
    private ExtractedDocumentData extractDrivingLicenseData(String text, ExtractedDocumentData data) {
        Map<String, Object> fields = new HashMap<>();
        double confidence = 0.0;

        // Recherche du numéro de permis
        Pattern licensePattern = Pattern.compile("(?:PERMIS|LICENSE|LICENCE).*?([A-Z0-9]{8,20})", 
            Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = licensePattern.matcher(text);
        if (matcher.find()) {
            fields.put("licenseNumber", matcher.group(1));
            confidence += 0.9;
        }

        // Extraction des catégories
        Pattern categoryPattern = Pattern.compile("(?:CATEGORIES?|CAT)\\s*[:/]?\\s*([A-Z1-9\\s,]+)", 
            Pattern.CASE_INSENSITIVE);
        matcher = categoryPattern.matcher(text);
        if (matcher.find()) {
            fields.put("categories", matcher.group(1).trim());
            confidence += 0.6;
        }

        data.setExtractedFields(fields);
        data.setConfidenceScore(confidence / 1.5);

        return data;
    }

    /**
     * Validation des données extraites
     */
    private DocumentValidationResult validateExtractedData(ExtractedDocumentData data, Document.DocumentType documentType) {
        DocumentValidationResult result = new DocumentValidationResult();
        List<String> errors = new ArrayList<>();

        // Validation de la confiance minimale
        if (data.getConfidenceScore() < ocrConfidenceThreshold) {
            errors.add("Confiance OCR insuffisante: " + String.format("%.2f", data.getConfidenceScore()));
        }

        // Validations spécifiques par type de document
        switch (documentType) {
            case PASSPORT:
                Map<String, String> passportFields = data.getExtractedFields();
                validatePassportData(passportFields, errors);
                break;
            case ID_CARD:
            case ID_CARD_FRONT:
            case ID_CARD_BACK:
                Map<String, String> idCardFields = data.getExtractedFields();
                validateIdCardData(idCardFields, errors);
                break;
            case DRIVING_LICENSE:
            case DRIVING_LICENSE_FRONT:
            case DRIVING_LICENSE_BACK:
                Map<String, String> dlFields = data.getExtractedFields();
                validateDrivingLicenseData(dlFields, errors);
                break;
            default:
                Map<String, String> defaultFields = data.getExtractedFields();
                validateIdCardData(defaultFields, errors);
                break;
        }

        result.setValid(errors.isEmpty());
        result.setErrors(errors);
        result.setConfidenceScore(data.getConfidenceScore());

        return result;
    }

    /**
     * Validation spécifique passeport
     */
    private void validatePassportData(Map<String, String> fields, List<String> errors) {
        if (!fields.containsKey("passportNumber")) {
            errors.add("Numéro de passeport manquant");
        } else {
            String passportNumber = fields.get("passportNumber");
            if (!VALIDATION_PATTERNS.get("PASSPORT_NUMBER").matcher(passportNumber).matches()) {
                errors.add("Format du numéro de passeport invalide");
            }
        }

        if (!fields.containsKey("surname")) {
            errors.add("Nom de famille manquant");
        }

        if (!fields.containsKey("givenNames")) {
            errors.add("Prénoms manquants");
        }

        // Validation des dates
        validateDate(fields, "dateOfBirth", "Date de naissance", errors);
        validateDate(fields, "expiryDate", "Date d'expiration", errors);
    }

    /**
     * Validation d'une date
     */
    private void validateDate(Map<String, String> fields, String fieldName, String fieldLabel, List<String> errors) {
        if (fields.containsKey(fieldName)) {
            String date = fields.get(fieldName);
            if (!VALIDATION_PATTERNS.get("DATE").matcher(date).matches()) {
                errors.add(fieldLabel + " au format invalide");
            } else {
                // Validation logique de la date
                try {
                    String[] parts = date.split("/");
                    int day = Integer.parseInt(parts[0]);
                    int month = Integer.parseInt(parts[1]);
                    int year = Integer.parseInt(parts[2]);
                    
                    if (month < 1 || month > 12 || day < 1 || day > 31) {
                        errors.add(fieldLabel + " invalide");
                    }
                    
                    if ("expiryDate".equals(fieldName) && year < LocalDateTime.now().getYear()) {
                        errors.add("Document expiré");
                    }
                } catch (NumberFormatException e) {
                    errors.add(fieldLabel + " au format numérique invalide");
                }
            }
        }
    }

    /**
     * Validation du fichier uploadé
     */
    private void validateFile(MultipartFile file, String documentType) {
        if (file.isEmpty()) {
            throw new DocumentValidationException("Fichier vide");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new DocumentValidationException("Fichier trop volumineux (max 10MB)");
        }

        String mimeType = file.getContentType();
        if (mimeType == null || !SUPPORTED_MIME_TYPES.get(documentType).contains(mimeType)) {
            throw new UnsupportedDocumentTypeException("Type de fichier non supporté: " + mimeType);
        }

        // Validation de l'extension
        String filename = file.getOriginalFilename();
        if (filename == null || !isValidFileExtension(filename)) {
            throw new DocumentValidationException("Extension de fichier invalide");
        }
    }

    /**
     * Validation de l'extension de fichier
     */
    private boolean isValidFileExtension(String filename) {
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        return Arrays.asList("jpg", "jpeg", "png", "tiff", "tif", "pdf").contains(extension);
    }

    /**
     * Sauvegarde chiffrée du fichier
     */
    private String saveEncryptedFile(MultipartFile file, UUID sessionId, String documentType) throws IOException {
        // Création du répertoire de destination
        String sessionPath = documentStoragePath + "/" + sessionId.toString();
        Path directoryPath = Paths.get(sessionPath);
        Files.createDirectories(directoryPath);

        // Génération du nom de fichier unique
        String timestamp = String.valueOf(System.currentTimeMillis());
        String extension = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf('.'));
        String filename = documentType + "_" + timestamp + extension;
        
        Path filePath = directoryPath.resolve(filename);

        // Chiffrement et sauvegarde du fichier
        byte[] encryptedContent = encryptionService.encryptBytes(file.getBytes());
        Files.write(filePath, encryptedContent);

        logger.debug("Fichier sauvegardé et chiffré: {}", filePath.toString());

        return filePath.toString();
    }

    /**
     * Calcul du hash SHA-256 du fichier
     */
    @SuppressWarnings("unused")
    private String calculateFileHash(byte[] fileBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(fileBytes);
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Algorithme SHA-256 non disponible", e);
        }
    }

    /**
     * Mise à jour du statut de la session après validation
     */
    private void updateSessionStatus(UUID sessionId, boolean isValid) {
        KycSession session = kycSessionRepository.findById(sessionId).orElse(null);
        if (session != null) {
            if (isValid) {
                session.setStatus(KycSession.SessionStatus.DOCUMENT_VERIFIED);
            } else {
                session.setStatus(KycSession.SessionStatus.FAILED);
                session.setFailureReason("Validation du document échouée");
            }
            kycSessionRepository.save(session);
        }
    }

    // Méthodes utilitaires pour validation carte d'identité et permis de conduire
    private void validateIdCardData(Map<String, String> fields, List<String> errors) {
        // Implémentation similaire pour carte d'identité
    }

    private void validateDrivingLicenseData(Map<String, String> fields, List<String> errors) {
        // Implémentation similaire pour permis de conduire
    }

    /**
     * Récupération d'un document par ID
     */
    @Transactional(readOnly = true)
    public Document getDocument(UUID documentId) {
        return documentRepository.findById(documentId)
            .orElseThrow(() -> new IllegalArgumentException("Document non trouvé: " + documentId));
    }

    /**
     * Récupération de tous les documents d'une session
     */
    @Transactional(readOnly = true)
    public List<Document> getDocumentsBySession(UUID sessionId) {
        return documentRepository.findBySessionIdOrderByCreatedAtDesc(sessionId);
    }

    /**
     * Suppression d'un document (RGPD)
     */
    @Transactional
    public void deleteDocument(UUID documentId) {
        Document document = getDocument(documentId);
        
        // Suppression du fichier physique
        try {
            String encryptedPath = encryptionService.decrypt(document.getFilePath());
            Path filePath = Paths.get(encryptedPath);
            Files.deleteIfExists(filePath);
        } catch (Exception e) {
            logger.error("Erreur lors de la suppression du fichier: {}", documentId, e);
        }

        // Suppression de l'enregistrement
        documentRepository.delete(document);
        
        logger.info("Document supprimé (RGPD): {}", documentId);
    }

    // Overload to accept controller DTO
    public void deleteDocument(com.anip.kyc.dto.document.DocumentDeletionRequest req) {
        if (req == null || req.getDocumentId() == null) throw new IllegalArgumentException("DocumentId requis");
        deleteDocument(req.getDocumentId());
    }
}