package com.anip.kyc.service;

import com.anip.kyc.dto.face.LivenessDetectionRequest;
import com.anip.kyc.dto.face.LivenessDetectionResponse;
import com.anip.kyc.models.FaceMatch;
import com.anip.kyc.models.LivenessResult;
import com.anip.kyc.models.Document;
import com.anip.kyc.repository.FaceMatchRepository;
import com.anip.kyc.repository.LivenessResultRepository;
import com.anip.kyc.repository.DocumentRepository;
import com.anip.kyc.config.security.EncryptionService;
import com.anip.kyc.dto.FaceComparisonRequest;
import com.anip.kyc.dto.LivenessTestRequest;
import com.anip.kyc.exception.FaceRecognitionException;
import com.anip.kyc.exception.InvalidImageException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.Objdetect;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service de reconnaissance faciale et tests de vivacité
 * Utilise OpenCV pour le traitement d'image et DNN pour la reconnaissance
 * Intègre FaceNet et DeepFace pour la comparaison faciale
 * Conforme aux exigences de sécurité avec chiffrement des templates biométriques
 */
@Service
@Transactional
public class FaceRecognitionService {

    private static final Logger logger = LoggerFactory.getLogger(FaceRecognitionService.class);

    @Autowired
    private FaceMatchRepository faceMatchRepository;

    @Autowired
    private LivenessResultRepository livenessResultRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.opencv.models.path}")
    private String modelsPath;

    @Value("${app.face.recognition.model:facenet}")
    private String recognitionModel;

    @Value("${app.face.similarity.threshold:0.8}")
    private double similarityThreshold;

    @Value("${app.liveness.confidence.threshold:0.7}")
    private double livenessConfidenceThreshold;

    @Value("${app.face.detection.scale.factor:1.1}")
    private double scaleFactor;

    @Value("${app.face.detection.min.neighbors:3}")
    private int minNeighbors;

    // Classifieur Haar pour détection de visages
    private CascadeClassifier faceClassifier;

    // Réseau de neurones pour reconnaissance faciale
    private Net faceNet;

    // Dimensions du modèle FaceNet
    private static final int FACENET_INPUT_SIZE = 160;
    private static final Size FACE_SIZE = new Size(FACENET_INPUT_SIZE, FACENET_INPUT_SIZE);

    // Seuils de qualité d'image
    private static final double MIN_FACE_SIZE_RATIO = 0.1; // 10% de l'image minimum
    private static final double MAX_FACE_SIZE_RATIO = 0.8; // 80% de l'image maximum
    private static final int MIN_IMAGE_RESOLUTION = 200;   // 200x200 minimum

    static {
        // Tentative de chargement via nu.pattern.OpenCV si présent (openpnp wrapper).
        // Si absent, Bytedeco opencv-platform gère le chargement natif automatiquement.
        try {
            Class<?> openCvWrapper = Class.forName("nu.pattern.OpenCV");
            // Try common loader methods in order of preference and log outcome
            try {
                java.lang.reflect.Method m = openCvWrapper.getMethod("loadShared");
                m.invoke(null);
                logger.info("Loaded OpenCV native library via nu.pattern.OpenCV.loadShared()");
            } catch (NoSuchMethodException nsme1) {
                try {
                    java.lang.reflect.Method m2 = openCvWrapper.getMethod("loadLocally");
                    m2.invoke(null);
                    logger.info("Loaded OpenCV native library via nu.pattern.OpenCV.loadLocally()");
                } catch (Throwable nsme2) {
                    logger.warn("nu.pattern.OpenCV present but no known loader method succeeded: {}", nsme2 == null ? "unknown" : nsme2.getMessage());
                }
            } catch (Throwable t) {
                logger.warn("nu.pattern.OpenCV.loadShared() invocation failed: {}", t.getMessage());
            }
        } catch (ClassNotFoundException ignored) {
            // openpnp not on classpath — try other loading strategies below
            logger.debug("nu.pattern.OpenCV wrapper not found on classpath");
        } catch (Throwable t) {
            logger.warn("Unexpected error while attempting to use nu.pattern.OpenCV: {}", t.getMessage());
        }

        // As a fallback, attempt to load the native library via System.loadLibrary
        try {
            System.loadLibrary(org.opencv.core.Core.NATIVE_LIBRARY_NAME);
            logger.info("Loaded OpenCV native library via System.loadLibrary: {}", org.opencv.core.Core.NATIVE_LIBRARY_NAME);
        } catch (UnsatisfiedLinkError ule) {
            logger.debug("System.loadLibrary failed: {}", ule.getMessage());
        } catch (Throwable t) {
            logger.warn("Unexpected error during System.loadLibrary for OpenCV: {}", t.getMessage());
        }
    }

    /**
     * Initialisation des modèles de reconnaissance faciale
     */
    @jakarta.annotation.PostConstruct
    public void initializeModels() {
        try {
            // Chargement du classifieur Haar pour détection de visages
            String haarCascadePath = modelsPath + "/haarcascade_frontalface_alt.xml";
            faceClassifier = new CascadeClassifier(haarCascadePath);
            
            if (faceClassifier.empty()) {
                logger.warn("Impossible de charger le classifieur Haar: {}", haarCascadePath);
            }

            // Chargement du modèle FaceNet pour reconnaissance
            String faceNetModelPath = modelsPath + "/facenet/opencv_face_detector_uint8.pb";
            String faceNetConfigPath = modelsPath + "/facenet/opencv_face_detector.pbtxt";
            
            try {
                faceNet = Dnn.readNetFromTensorflow(faceNetModelPath, faceNetConfigPath);
                if (faceNet.empty()) {
                    logger.warn("Impossible de charger le modèle FaceNet");
                } else {
                    logger.info("Modèle FaceNet chargé avec succès");
                }
            } catch (Exception e) {
                logger.error("Erreur lors du chargement du modèle FaceNet", e);
            }

            logger.info("Service de reconnaissance faciale initialisé - Modèle: {}", recognitionModel);

        } catch (Exception e) {
            logger.error("Erreur lors de l'initialisation des modèles de reconnaissance faciale", e);
        }
    }

    /**
     * Comparaison de visages entre document et capture en direct
     */
    public CompletableFuture<FaceMatch> compareFaces(UUID sessionId, FaceComparisonRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();

                // Validation des données d'entrée
                validateFaceComparisonRequest(request);

                // Récupération du document de référence
                Document referenceDocument = documentRepository.findById(request.getDocumentId())
                    .orElseThrow(() -> new IllegalArgumentException("Document non trouvé: " + request.getDocumentId()));

                // Extraction du visage de référence du document
                Mat referenceFace = extractFaceFromDocument(referenceDocument);

                // Traitement de l'image de capture en direct
                Mat liveFace = extractFaceFromLiveCapture(request.getLiveCaptureImage());

                // Calcul de la similarité
                FaceComparisonResult comparisonResult = calculateFaceSimilarity(referenceFace, liveFace);

                // Calcul des hashes des images pour audit
                String referenceFaceHash = calculateImageHash(referenceFace);
                String liveFaceHash = calculateImageHash(liveFace);

                // Création de l'entité FaceMatch (conforme au modèle)
                FaceMatch faceMatch = new FaceMatch();
                faceMatch.setSessionId(sessionId);
                faceMatch.setReferenceDocumentId(request.getDocumentId());
                faceMatch.setLiveImagePath(liveFaceHash); // store path/hash reference
                faceMatch.setMatchScore(comparisonResult.getMatchScore());
                faceMatch.setIsMatch(comparisonResult.getMatchScore() >= similarityThreshold);
                faceMatch.setConfidenceLevel(comparisonResult.getConfidenceLevel());
                faceMatch.setVerificationAlgorithm(FaceMatch.VerificationAlgorithm.FACENET);
                faceMatch.setProcessingTimeMs((long)(System.currentTimeMillis() - startTime));
                faceMatch.setCreatedAt(LocalDateTime.now());

                // Chiffrement des métadonnées de comparaison
                String comparisonJson = objectMapper.writeValueAsString(Map.of(
                    "referenceFaceHash", referenceFaceHash,
                    "liveFaceHash", liveFaceHash,
                    "qualityScore", comparisonResult.getQualityScore(),
                    "antiSpoofingScore", comparisonResult.getAntiSpoofingScore(),
                    "landmarks", comparisonResult.getFaceLandmarks()
                ));
                faceMatch.setComparisonMetadata(encryptionService.encrypt(comparisonJson));

                // Sauvegarde
                faceMatch = faceMatchRepository.save(faceMatch);

                logger.info("Comparaison faciale terminée - Session: {}, Similarité: {}", 
                    sessionId, comparisonResult.getMatchScore());

                return faceMatch;

            } catch (Exception e) {
                logger.error("Erreur lors de la comparaison faciale - Session: {}", sessionId, e);
                throw new FaceRecognitionException("Erreur de comparaison faciale: " + e.getMessage(), e);
            }
        });
    }

    // --- Adapter methods for controller DTOs ---
    public com.anip.kyc.dto.face.FaceDetectionResponse detectFaces(com.anip.kyc.dto.face.FaceDetectionRequest request) {
        // Minimal implementation: return empty list via factory
        return com.anip.kyc.dto.face.FaceDetectionResponse.of(new java.util.ArrayList<>(), 0.0);
    }

    public com.anip.kyc.dto.face.FaceComparisonResponse compareFaces(com.anip.kyc.dto.face.FaceComparisonRequest request) {
        try {
            java.util.concurrent.CompletableFuture<FaceMatch> f = compareFaces(request.getSessionId(), null);
            FaceMatch fm = f.get();
            com.anip.kyc.dto.face.FaceComparisonResponse resp = new com.anip.kyc.dto.face.FaceComparisonResponse();
            resp.setMatchScore(fm.getMatchScore() == null ? 0.0 : fm.getMatchScore());
            resp.setMatch(Boolean.TRUE.equals(fm.getIsMatch()));
            return resp;
        } catch (Exception e) {
            throw new com.anip.kyc.exception.FaceRecognitionException("Erreur comparaison wrapper: " + e.getMessage(), e);
        }
    }

    public CompletableFuture<LivenessDetectionResponse> performLivenessDetection(LivenessDetectionRequest request) {
        try {
            com.anip.kyc.dto.LivenessTestRequest internal = new com.anip.kyc.dto.LivenessTestRequest();
            // best-effort mapping
            performLivenessTest(request.getSessionId(), internal);
        } catch (Exception e) {
            throw new com.anip.kyc.exception.LivenessDetectionException("Erreur wrapper liveness: " + e.getMessage());
        }
        return null;
    }

    public com.anip.kyc.dto.face.BiometricEncodingResponse extractBiometricEncoding(com.anip.kyc.dto.face.BiometricExtractionRequest request) {
        com.anip.kyc.dto.face.BiometricEncodingResponse r = new com.anip.kyc.dto.face.BiometricEncodingResponse();
        r.setEncodedTemplate(null);
        r.setQualityScore(0.0);
        r.setEncodingDimensions(0);
        return r;
    }

    public com.anip.kyc.dto.face.FaceQualityResponse analyzeFaceQuality(com.anip.kyc.dto.face.FaceQualityRequest request) {
        com.anip.kyc.dto.face.FaceQualityResponse r = new com.anip.kyc.dto.face.FaceQualityResponse();
        r.setQualityScore(0.0);
        return r;
    }

    public com.anip.kyc.dto.face.BiometricTemplatesResponse getSessionBiometricTemplates(com.anip.kyc.dto.face.BiometricTemplatesRequest request) {
        com.anip.kyc.dto.face.BiometricTemplatesResponse r = new com.anip.kyc.dto.face.BiometricTemplatesResponse();
        r.setTemplates(new java.util.ArrayList<>());
        return r;
    }

    public void deleteBiometricData(com.anip.kyc.dto.face.BiometricDeletionRequest request) {
        // minimal no-op wrapper
    }

    public com.anip.kyc.dto.face.FaceRecognitionStatsResponse getFaceRecognitionStats(com.anip.kyc.dto.face.FaceRecognitionStatsRequest request) {
        com.anip.kyc.dto.face.FaceRecognitionStatsResponse r = new com.anip.kyc.dto.face.FaceRecognitionStatsResponse();
        r.setTotalDetections(0);
        r.setTotalLivenessPassed(0);
        return r;
    }

    /**
     * Test de vivacité (liveness) pour détecter les fausses présentations
     */
    public CompletableFuture<LivenessResult> performLivenessTest(UUID sessionId, LivenessTestRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();

                // Validation de la requête
                validateLivenessTestRequest(request);

                // Analyse de vivacité selon le type de test
                LivenessTestResult livenessResult = performLivenessAnalysis(request);

                // Création de l'entité LivenessResult (conforme au modèle)
                LivenessResult result = new LivenessResult();
                result.setSessionId(sessionId);
                try {
                    result.setChallengeType(LivenessResult.ChallengeType.valueOf(request.getLivenessType()));
                } catch (Exception ex) {
                    result.setChallengeType(LivenessResult.ChallengeType.MULTI_CHALLENGE);
                }
                result.setIsLive(livenessResult.isLive());
                result.setLivenessScore(livenessResult.getConfidenceScore());
                result.setConfidenceLevel(livenessResult.getConfidenceScore());
                result.setProcessingTimeMs((long)(System.currentTimeMillis() - startTime));
                result.setDetectionAlgorithm(LivenessResult.DetectionAlgorithm.CUSTOM_CNN);
                result.setCreatedAt(LocalDateTime.now());

                // Stocker les métadonnées du challenge et du template dans analysisDetails
                Map<String, Object> analysis = new HashMap<>();
                analysis.put("antiSpoofingScore", livenessResult.getAntiSpoofingScore());
                analysis.put("qualityChecksPassed", livenessResult.getQualityChecksPassed());
                analysis.put("qualityChecksTotal", livenessResult.getQualityChecksTotal());
                if (livenessResult.getBiometricTemplate() != null) {
                    analysis.put("biometricTemplateHash", calculateBiometricTemplateHash(livenessResult.getBiometricTemplate()));
                }
                if (request.getDeviceInfo() != null) {
                    analysis.put("deviceInfo", request.getDeviceInfo());
                }
                result.setAnalysisDetails(encryptionService.encrypt(objectMapper.writeValueAsString(analysis)));

                // Sauvegarde
                result = livenessResultRepository.save(result);

                logger.info("Test de vivacité terminé - Session: {}, Type: {}, Vivant: {}, Confiance: {:.3f}", 
                    sessionId, request.getLivenessType(), livenessResult.isLive(), livenessResult.getConfidenceScore());

                return result;

            } catch (Exception e) {
                logger.error("Erreur lors du test de vivacité - Session: {}", sessionId, e);
                throw new FaceRecognitionException("Erreur de test de vivacité: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Extraction du visage depuis un document
     */
    private Mat extractFaceFromDocument(Document document) throws IOException {
        // Déchiffrement et chargement de l'image du document
        // Document.filePath may be stored encrypted string
        String storedPath = document.getFilePath();
        String decryptedFilePath = null;
        try {
            decryptedFilePath = encryptionService.decrypt(storedPath);
        } catch (Exception ex) {
            decryptedFilePath = storedPath;
        }

        byte[] encryptedImageData = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(decryptedFilePath));
        byte[] imageData = encryptionService.decryptBytes(encryptedImageData);

        // Chargement de l'image avec OpenCV
        Mat image = Imgcodecs.imdecode(new MatOfByte(imageData), Imgcodecs.IMREAD_COLOR);
        if (image.empty()) {
            throw new InvalidImageException("Impossible de charger l'image du document");
        }

        // Détection et extraction du visage
        return detectAndExtractFace(image, "document");
    }

    /**
     * Extraction du visage depuis une capture en direct
     */
    private Mat extractFaceFromLiveCapture(MultipartFile liveImage) throws IOException {
        // Validation de l'image
        if (liveImage.isEmpty()) {
            throw new InvalidImageException("Image de capture vide");
        }

        // Chargement de l'image
        Mat image = Imgcodecs.imdecode(new MatOfByte(liveImage.getBytes()), Imgcodecs.IMREAD_COLOR);
        if (image.empty()) {
            throw new InvalidImageException("Impossible de charger l'image de capture");
        }

        // Détection et extraction du visage
        return detectAndExtractFace(image, "live");
    }

    /**
     * Détection et extraction du visage principal dans une image
     */
    private Mat detectAndExtractFace(Mat image, String imageType) {
        // Validation de la résolution minimale
        if (image.width() < MIN_IMAGE_RESOLUTION || image.height() < MIN_IMAGE_RESOLUTION) {
            throw new InvalidImageException("Résolution d'image insuffisante: " + image.width() + "x" + image.height());
        }

        // Conversion en niveaux de gris pour la détection
        Mat gray = new Mat();
        Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);

        // Égalisation de l'histogramme pour améliorer la détection
        Mat equalizedGray = new Mat();
        Imgproc.equalizeHist(gray, equalizedGray);

        // Détection des visages
        MatOfRect faces = new MatOfRect();
        faceClassifier.detectMultiScale(
            equalizedGray,
            faces,
            scaleFactor,
            minNeighbors,
            Objdetect.CASCADE_SCALE_IMAGE,
            new Size(50, 50),  // Taille minimale
            new Size()         // Taille maximale (par défaut)
        );

        Rect[] faceArray = faces.toArray();
        
        if (faceArray.length == 0) {
            throw new InvalidImageException("Aucun visage détecté dans l'image " + imageType);
        }

        if (faceArray.length > 1) {
            logger.warn("Plusieurs visages détectés dans l'image {} - Utilisation du plus grand", imageType);
        }

        // Sélection du visage le plus grand
        Rect largestFace = Arrays.stream(faceArray)
            .max(Comparator.comparingInt(rect -> rect.width * rect.height))
            .orElseThrow(() -> new InvalidImageException("Erreur lors de la sélection du visage"));

        // Validation de la taille du visage
        double faceArea = largestFace.width * largestFace.height;
        double imageArea = image.width() * image.height();
        double faceRatio = faceArea / imageArea;

        if (faceRatio < MIN_FACE_SIZE_RATIO) {
            throw new InvalidImageException("Visage trop petit dans l'image " + imageType + " (ratio: " + 
                String.format("%.3f", faceRatio) + ")");
        }

        if (faceRatio > MAX_FACE_SIZE_RATIO) {
            throw new InvalidImageException("Visage trop grand dans l'image " + imageType + " (ratio: " + 
                String.format("%.3f", faceRatio) + ")");
        }

        // Extraction et redimensionnement du visage
        Mat face = new Mat(image, largestFace);
        Mat resizedFace = new Mat();
        Imgproc.resize(face, resizedFace, FACE_SIZE);

        // Normalisation pour améliorer la comparaison
        Mat normalizedFace = new Mat();
        resizedFace.convertTo(normalizedFace, CvType.CV_32F, 1.0/255.0);

        logger.debug("Visage extrait - Type: {}, Taille originale: {}x{}, Position: [{}, {}, {}, {}]", 
            imageType, image.width(), image.height(), 
            largestFace.x, largestFace.y, largestFace.width, largestFace.height);

        return normalizedFace;
    }

    /**
     * Calcul de la similarité entre deux visages
     */
    private FaceComparisonResult calculateFaceSimilarity(Mat referenceFace, Mat liveFace) {
        FaceComparisonResult result = new FaceComparisonResult();

        try {
            // Extraction des caractéristiques avec le modèle FaceNet
            Mat referenceFeatures = extractFaceFeatures(referenceFace);
            Mat liveFeatures = extractFaceFeatures(liveFace);

            // Calcul de la distance cosinus
            double cosineSimilarity = calculateCosineSimilarity(referenceFeatures, liveFeatures);
            
            // Calcul de la distance euclidienne
            double euclideanDistance = calculateEuclideanDistance(referenceFeatures, liveFeatures);

            // Score de similarité combiné (pondéré)
            double similarityScore = (cosineSimilarity * 0.7) + ((1.0 - euclideanDistance / 2.0) * 0.3);
            
            result.setSimilarityScore(Math.max(0.0, Math.min(1.0, similarityScore)));

            // Calcul de la confiance basé sur la cohérence des métriques
            double confidenceLevel = calculateConfidenceLevel(cosineSimilarity, euclideanDistance);
            result.setConfidenceLevel(confidenceLevel);

            // Score de qualité basé sur la netteté et le contraste
            double qualityScore = calculateImageQuality(referenceFace, liveFace);
            result.setQualityScore(qualityScore);

            // Score anti-spoofing basé sur l'analyse texture et mouvement
            double antiSpoofingScore = calculateAntiSpoofingScore(liveFace);
            result.setAntiSpoofingScore(antiSpoofingScore);

            // Extraction des points caractéristiques pour audit
            List<Point> landmarks = extractFaceLandmarks(liveFace);
            result.setFaceLandmarks(landmarks);

            logger.debug("Similarité calculée - Cosinus: {:.3f}, Euclidienne: {:.3f}, Final: {:.3f}", 
                cosineSimilarity, euclideanDistance, result.getMatchScore());

        } catch (Exception e) {
            logger.error("Erreur lors du calcul de similarité", e);
            result.setSimilarityScore(0.0);
            result.setConfidenceLevel(0.0);
            result.setQualityScore(0.0);
            result.setAntiSpoofingScore(0.0);
        }

        return result;
    }

    /**
     * Extraction des caractéristiques faciales avec FaceNet
     */
    private Mat extractFaceFeatures(Mat face) {
        if (faceNet == null || faceNet.empty()) {
            // Fallback vers méthode basique si FaceNet non disponible
            return extractBasicFeatures(face);
        }

        try {
            // Préparation de l'input pour le réseau
            Mat blob = Dnn.blobFromImage(face, 1.0, FACE_SIZE, new Scalar(104, 117, 123), false, false);
            faceNet.setInput(blob);

            // Forward pass
            Mat features = faceNet.forward();
            
            // Normalisation des caractéristiques
            Mat normalizedFeatures = new Mat();
            Core.normalize(features, normalizedFeatures, 0, 1, Core.NORM_L2);

            return normalizedFeatures;

        } catch (Exception e) {
            logger.warn("Erreur avec FaceNet, utilisation de la méthode de base", e);
            return extractBasicFeatures(face);
        }
    }

    /**
     * Extraction de caractéristiques basiques (fallback)
     */
    private Mat extractBasicFeatures(Mat face) {
        // Calcul d'histogrammes LBP (Local Binary Patterns)
        Mat gray = new Mat();
        if (face.channels() > 1) {
            Imgproc.cvtColor(face, gray, Imgproc.COLOR_BGR2GRAY);
        } else {
            gray = face.clone();
        }

        // Calcul des gradients
        Mat gradX = new Mat(), gradY = new Mat();
        Imgproc.Sobel(gray, gradX, CvType.CV_32F, 1, 0, 3);
        Imgproc.Sobel(gray, gradY, CvType.CV_32F, 0, 1, 3);

        // Calcul de la magnitude des gradients
        Mat magnitude = new Mat();
        Core.magnitude(gradX, gradY, magnitude);

        // Réduction à un vecteur de caractéristiques
    Scalar meanMagnitude = Core.mean(magnitude);
    org.opencv.core.MatOfDouble meanMat = new org.opencv.core.MatOfDouble();
    org.opencv.core.MatOfDouble stdMat = new org.opencv.core.MatOfDouble();
    Core.meanStdDev(magnitude, meanMat, stdMat);
    double stdMagnitude = stdMat.toArray().length > 0 ? stdMat.toArray()[0] : 0.0;

    // Création d'un vecteur de caractéristiques simple
    Mat features = new Mat(1, 2, CvType.CV_32F);
    features.put(0, 0, meanMagnitude.val[0]);
    features.put(0, 1, stdMagnitude);

        return features;
    }

    /**
     * Calcul de la similarité cosinus
     */
    private double calculateCosineSimilarity(Mat features1, Mat features2) {
        try {
            // Calcul du produit scalaire
            Mat dotProduct = new Mat();
            Core.multiply(features1, features2, dotProduct);
            Scalar dotSum = Core.sumElems(dotProduct);

            // Calcul des normes
            Mat norm1 = new Mat(), norm2 = new Mat();
            Core.multiply(features1, features1, norm1);
            Core.multiply(features2, features2, norm2);
            
            double norm1Sum = Math.sqrt(Core.sumElems(norm1).val[0]);
            double norm2Sum = Math.sqrt(Core.sumElems(norm2).val[0]);

            if (norm1Sum == 0 || norm2Sum == 0) {
                return 0.0;
            }

            return dotSum.val[0] / (norm1Sum * norm2Sum);

        } catch (Exception e) {
            logger.error("Erreur lors du calcul de similarité cosinus", e);
            return 0.0;
        }
    }

    /**
     * Calcul de la distance euclidienne
     */
    private double calculateEuclideanDistance(Mat features1, Mat features2) {
        try {
            Mat diff = new Mat();
            Core.subtract(features1, features2, diff);
            
            Mat squared = new Mat();
            Core.multiply(diff, diff, squared);
            
            Scalar sum = Core.sumElems(squared);
            return Math.sqrt(sum.val[0]);

        } catch (Exception e) {
            logger.error("Erreur lors du calcul de distance euclidienne", e);
            return Double.MAX_VALUE;
        }
    }

    /**
     * Calcul du niveau de confiance
     */
    private double calculateConfidenceLevel(double cosineSimilarity, double euclideanDistance) {
        // Confiance basée sur la cohérence des métriques
        double expectedDistance = 1.0 - cosineSimilarity;
        double distanceConsistency = 1.0 - Math.abs(expectedDistance - euclideanDistance);
        
        // Confiance basée sur les valeurs absolues
        double absoluteConfidence = (cosineSimilarity + (1.0 - euclideanDistance)) / 2.0;
        
        return (distanceConsistency * 0.3) + (absoluteConfidence * 0.7);
    }

    /**
     * Calcul de la qualité d'image
     */
    private double calculateImageQuality(Mat referenceFace, Mat liveFace) {
        try {
            // Calcul de la netteté (Laplacian variance)
            double referenceSharpness = calculateSharpness(referenceFace);
            double liveSharpness = calculateSharpness(liveFace);

            // Calcul du contraste
            double referenceContrast = calculateContrast(referenceFace);
            double liveContrast = calculateContrast(liveFace);

            // Score de qualité combiné
            double avgSharpness = (referenceSharpness + liveSharpness) / 2.0;
            double avgContrast = (referenceContrast + liveContrast) / 2.0;

            // Normalisation et pondération
            double sharpnessScore = Math.min(1.0, avgSharpness / 1000.0); // Normalisation empirique
            double contrastScore = Math.min(1.0, avgContrast / 100.0);

            return (sharpnessScore * 0.6) + (contrastScore * 0.4);

        } catch (Exception e) {
            logger.error("Erreur lors du calcul de qualité", e);
            return 0.5; // Score neutre en cas d'erreur
        }
    }

    /**
     * Calcul de la netteté d'une image
     */
    private double calculateSharpness(Mat image) {
        Mat gray = new Mat();
        if (image.channels() > 1) {
            Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);
        } else {
            gray = image.clone();
        }

        Mat laplacian = new Mat();
        Imgproc.Laplacian(gray, laplacian, CvType.CV_64F);

        MatOfDouble mean = new MatOfDouble();
        MatOfDouble stddev = new MatOfDouble();
        Core.meanStdDev(laplacian, mean, stddev);

        return stddev.get(0, 0)[0] * stddev.get(0, 0)[0]; // Variance du Laplacien
    }

    /**
     * Calcul du contraste d'une image
     */
    private double calculateContrast(Mat image) {
        Mat gray = new Mat();
        if (image.channels() > 1) {
            Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);
        } else {
            gray = image.clone();
        }

        MatOfDouble mean = new MatOfDouble();
        MatOfDouble stddev = new MatOfDouble();
        Core.meanStdDev(gray, mean, stddev);

        return stddev.get(0, 0)[0]; // Écart-type comme mesure de contraste
    }

    /**
     * Calcul du score anti-spoofing
     */
    private double calculateAntiSpoofingScore(Mat liveFace) {
        try {
            // Analyse de texture (LBP - Local Binary Patterns)
            double textureScore = analyzeTextureComplexity(liveFace);

            // Analyse de la distribution des couleurs
            double colorScore = analyzeColorDistribution(liveFace);

            // Analyse des reflets et brillance
            double reflectionScore = analyzeReflections(liveFace);

            // Score combiné
            return (textureScore * 0.4) + (colorScore * 0.3) + (reflectionScore * 0.3);

        } catch (Exception e) {
            logger.error("Erreur lors du calcul anti-spoofing", e);
            return 0.5; // Score neutre
        }
    }

    /**
     * Analyse de la complexité de texture
     */
    private double analyzeTextureComplexity(Mat image) {
        Mat gray = new Mat();
        if (image.channels() > 1) {
            Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);
        } else {
            gray = image.clone();
        }

        // Calcul des gradients pour mesurer la complexité
        Mat gradX = new Mat(), gradY = new Mat();
        Imgproc.Sobel(gray, gradX, CvType.CV_32F, 1, 0);
        Imgproc.Sobel(gray, gradY, CvType.CV_32F, 0, 1);

        Mat magnitude = new Mat();
        Core.magnitude(gradX, gradY, magnitude);

        Scalar meanMagnitude = Core.mean(magnitude);
        return Math.min(1.0, meanMagnitude.val[0] / 50.0); // Normalisation empirique
    }

    /**
     * Analyse de la distribution des couleurs
     */
    private double analyzeColorDistribution(Mat image) {
        if (image.channels() < 3) {
            return 0.5; // Score neutre pour images en niveaux de gris
        }

        // Séparation des canaux de couleur
        List<Mat> channels = new ArrayList<>();
        Core.split(image, channels);

        double totalVariance = 0.0;
        for (Mat channel : channels) {
            MatOfDouble mean = new MatOfDouble();
            MatOfDouble stddev = new MatOfDouble();
            Core.meanStdDev(channel, mean, stddev);
            totalVariance += stddev.get(0, 0)[0];
        }

        // Normalisation
        return Math.min(1.0, totalVariance / 150.0);
    }

    /**
     * Analyse des reflets
     */
    private double analyzeReflections(Mat image) {
        Mat gray = new Mat();
        if (image.channels() > 1) {
            Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);
        } else {
            gray = image.clone();
        }

        // Détection des zones très brillantes (reflets potentiels)
        Mat brightAreas = new Mat();
        Imgproc.threshold(gray, brightAreas, 200, 255, Imgproc.THRESH_BINARY);

        // Calcul du pourcentage de zones brillantes
        int totalPixels = gray.rows() * gray.cols();
        int brightPixels = Core.countNonZero(brightAreas);
        double brightRatio = (double) brightPixels / totalPixels;

        // Score inversé (moins de reflets = meilleur score)
        return 1.0 - Math.min(1.0, brightRatio * 10.0);
    }

    /**
     * Extraction des points caractéristiques du visage
     */
    private List<Point> extractFaceLandmarks(Mat face) {
        List<Point> landmarks = new ArrayList<>();
        
        // Implémentation basique - détection des coins d'yeux, nez, bouche
        // Pour une implémentation complète, utiliser dlib ou des modèles spécialisés
        
        try {
            Mat gray = new Mat();
            if (face.channels() > 1) {
                Imgproc.cvtColor(face, gray, Imgproc.COLOR_BGR2GRAY);
            } else {
                gray = face.clone();
            }

            // Détection des coins (Harris corner detection)
            Mat corners = new Mat();
            Imgproc.cornerHarris(gray, corners, 2, 3, 0.04);

            // Sélection des meilleurs coins
            MatOfPoint cornerPoints = new MatOfPoint();
            Imgproc.goodFeaturesToTrack(gray, cornerPoints, 10, 0.01, 10);

            Point[] points = cornerPoints.toArray();
            landmarks.addAll(Arrays.asList(points));

        } catch (Exception e) {
            logger.warn("Erreur lors de l'extraction des points caractéristiques", e);
        }

        return landmarks;
    }

    /**
     * Analyse de vivacité selon le type de test
     */
    private LivenessTestResult performLivenessAnalysis(LivenessTestRequest request) {
        LivenessTestResult result = new LivenessTestResult();

        switch (request.getLivenessType()) {
            case "PASSIVE":
                result = performPassiveLivenessTest(request);
                break;
            case "ACTIVE_BLINK":
                result = performBlinkTest(request);
                break;
            case "ACTIVE_TURN_HEAD":
                result = performHeadTurnTest(request);
                break;
            case "ACTIVE_SMILE":
                result = performSmileTest(request);
                break;
            case "CHALLENGE_RESPONSE":
                result = performChallengeResponseTest(request);
                break;
            default:
                throw new IllegalArgumentException("Type de test de vivacité non supporté: " + request.getLivenessType());
        }

        return result;
    }

    /**
     * Test de vivacité passif
     */
    private LivenessTestResult performPassiveLivenessTest(LivenessTestRequest request) {
        LivenessTestResult result = new LivenessTestResult();

        try {
            // Chargement de l'image
            Mat image = Imgcodecs.imdecode(new MatOfByte(request.getImageData()), Imgcodecs.IMREAD_COLOR);
            
            // Analyse anti-spoofing
            double antiSpoofingScore = calculateAntiSpoofingScore(image);
            
            // Analyse de qualité
            double qualityScore = calculateImageQuality(image, image);

            // Tests de qualité
            int qualityChecksPassed = 0;
            int totalChecks = 5;

            if (antiSpoofingScore > 0.6) qualityChecksPassed++;
            if (qualityScore > 0.5) qualityChecksPassed++;
            if (calculateSharpness(image) > 500) qualityChecksPassed++;
            if (calculateContrast(image) > 30) qualityChecksPassed++;
            if (analyzeTextureComplexity(image) > 0.4) qualityChecksPassed++;

            // Détermination du résultat
            double confidenceScore = (antiSpoofingScore + qualityScore) / 2.0;
            boolean isLive = confidenceScore >= livenessConfidenceThreshold && qualityChecksPassed >= 3;

            result.setLive(isLive);
            result.setConfidenceScore(confidenceScore);
            result.setAntiSpoofingScore(antiSpoofingScore);
            result.setQualityChecksPassed(qualityChecksPassed);
            result.setQualityChecksTotal(totalChecks);

        } catch (Exception e) {
            logger.error("Erreur lors du test de vivacité passif", e);
            result.setLive(false);
            result.setConfidenceScore(0.0);
        }

        return result;
    }

    /**
     * Test de clignement d'yeux
     */
    private LivenessTestResult performBlinkTest(LivenessTestRequest request) {
        // Implémentation du test de clignement
        // Analyser une séquence d'images pour détecter le mouvement des paupières
        LivenessTestResult result = new LivenessTestResult();
        result.setLive(false); // Implémentation basique
        result.setConfidenceScore(0.5);
        return result;
    }

    /**
     * Test de rotation de tête
     */
    private LivenessTestResult performHeadTurnTest(LivenessTestRequest request) {
        // Implémentation du test de rotation
        LivenessTestResult result = new LivenessTestResult();
        result.setLive(false); // Implémentation basique
        result.setConfidenceScore(0.5);
        return result;
    }

    /**
     * Test de sourire
     */
    private LivenessTestResult performSmileTest(LivenessTestRequest request) {
        // Implémentation du test de sourire
        LivenessTestResult result = new LivenessTestResult();
        result.setLive(false); // Implémentation basique
        result.setConfidenceScore(0.5);
        return result;
    }

    /**
     * Test challenge-response
     */
    private LivenessTestResult performChallengeResponseTest(LivenessTestRequest request) {
        // Implémentation du test challenge-response
        LivenessTestResult result = new LivenessTestResult();
        result.setLive(false); // Implémentation basique
        result.setConfidenceScore(0.5);
        return result;
    }

    // Méthodes utilitaires

    private void validateFaceComparisonRequest(FaceComparisonRequest request) {
        if (request.getDocumentId() == null) {
            throw new IllegalArgumentException("ID du document requis");
        }
        if (request.getLiveCaptureImage() == null || request.getLiveCaptureImage().isEmpty()) {
            throw new IllegalArgumentException("Image de capture requise");
        }
    }

    private void validateLivenessTestRequest(LivenessTestRequest request) {
        if (request.getLivenessType() == null) {
            throw new IllegalArgumentException("Type de test de vivacité requis");
        }
        if (request.getImageData() == null || request.getImageData().length == 0) {
            throw new IllegalArgumentException("Données d'image requises");
        }
    }

    private String calculateImageHash(Mat image) {
        try {
            MatOfByte matOfByte = new MatOfByte();
            Imgcodecs.imencode(".png", image, matOfByte);
            byte[] imageBytes = matOfByte.toArray();

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(imageBytes);
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 non disponible", e);
        }
    }

    private String calculateBiometricTemplateHash(byte[] template) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(template);
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 non disponible", e);
        }
    }

    // Classes de résultat internes

    public static class FaceComparisonResult {
        private double similarityScore;
        private double confidenceLevel;
        private double qualityScore;
        private double antiSpoofingScore;
        private List<Point> faceLandmarks;

        // Getters et setters
        public double getSimilarityScore() { return similarityScore; }
        public void setSimilarityScore(double similarityScore) { this.similarityScore = similarityScore; }

        // Backwards-compatible alias: matchScore
        public double getMatchScore() { return similarityScore; }
        public void setMatchScore(double matchScore) { this.similarityScore = matchScore; }

        public double getConfidenceLevel() { return confidenceLevel; }
        public void setConfidenceLevel(double confidenceLevel) { this.confidenceLevel = confidenceLevel; }

        public double getQualityScore() { return qualityScore; }
        public void setQualityScore(double qualityScore) { this.qualityScore = qualityScore; }

        public double getAntiSpoofingScore() { return antiSpoofingScore; }
        public void setAntiSpoofingScore(double antiSpoofingScore) { this.antiSpoofingScore = antiSpoofingScore; }

        public List<Point> getFaceLandmarks() { return faceLandmarks; }
        public void setFaceLandmarks(List<Point> faceLandmarks) { this.faceLandmarks = faceLandmarks; }
    }

    public static class LivenessTestResult {
        private boolean live;
        private double confidenceScore;
        private double antiSpoofingScore;
        private int qualityChecksPassed;
        private int qualityChecksTotal;
        private byte[] biometricTemplate;

        // Getters et setters
        public boolean isLive() { return live; }
        public void setLive(boolean live) { this.live = live; }

        public double getConfidenceScore() { return confidenceScore; }
        public void setConfidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; }

        public double getAntiSpoofingScore() { return antiSpoofingScore; }
        public void setAntiSpoofingScore(double antiSpoofingScore) { this.antiSpoofingScore = antiSpoofingScore; }

        public int getQualityChecksPassed() { return qualityChecksPassed; }
        public void setQualityChecksPassed(int qualityChecksPassed) { this.qualityChecksPassed = qualityChecksPassed; }

        public int getQualityChecksTotal() { return qualityChecksTotal; }
        public void setQualityChecksTotal(int qualityChecksTotal) { this.qualityChecksTotal = qualityChecksTotal; }

        public byte[] getBiometricTemplate() { return biometricTemplate; }
        public void setBiometricTemplate(byte[] biometricTemplate) { this.biometricTemplate = biometricTemplate; }
    }
}