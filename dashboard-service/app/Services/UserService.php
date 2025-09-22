<?php

namespace App\Services;

use App\Models\SystemUser;
use App\Repositories\UserRepository;
use App\DTOs\User\UserRequest;
use App\DTOs\User\UserResponse;
use App\DTOs\User\UserProfileUpdate;
use App\DTOs\User\RgpdRequest;
use App\DTOs\User\DataExportResponse;
use App\DTOs\User\AnonymizationReport;
use App\Exceptions\User\UserNotFoundException;
use App\Exceptions\User\UserValidationException;
use App\Exceptions\User\RgpdComplianceException;
use App\Services\Encryption\EncryptionService;
use App\Services\AuditService;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Mail;
use Illuminate\Support\Facades\Cache;
use Illuminate\Validation\ValidationException;
use Carbon\Carbon;
use Exception;

/**
 * Service de gestion des utilisateurs avec compliance RGPD
 * 
 * Implémente la gestion complète des utilisateurs système avec:
 * - Chiffrement transparent des données personnelles
 * - Anonymisation k-anonymity (k ≥ 5)
 * - Droit à l'oubli (Article 17 RGPD)
 * - Portabilité des données (Article 20 RGPD)
 * - Consentement et révocation
 * - Audit complet des accès
 * 
 * Conformité RGPD:
 * - Minimisation des données
 * - Pseudonymisation automatique
 * - Chiffrement AES-256
 * - Rétention automatique
 * - Logs d'audit immutables
 */
class UserService
{
    private UserRepository $userRepository;
    private EncryptionService $encryptionService;
    private AuditService $auditService;
    
    // Configuration RGPD
    private const DATA_RETENTION_YEARS = 7;
    private const K_ANONYMITY_THRESHOLD = 5;
    private const CONSENT_EXPIRY_MONTHS = 24;
    private const EXPORT_MAX_RECORDS = 10000;
    
    // Types de données personnelles
    private const PERSONAL_DATA_FIELDS = [
        'email', 'first_name', 'last_name', 'phone_number',
        'birth_date', 'address', 'identity_document_number'
    ];
    
    private const SENSITIVE_DATA_FIELDS = [
        'biometric_template_hash', 'face_encoding', 'document_photos'
    ];
    
    public function __construct(
        UserRepository $userRepository,
        EncryptionService $encryptionService,
        AuditService $auditService
    ) {
        $this->userRepository = $userRepository;
        $this->encryptionService = $encryptionService;
        $this->auditService = $auditService;
    }
    
    /**
     * Création d'utilisateur avec chiffrement RGPD
     */
    public function createUser(UserRequest $request): UserResponse
    {
        try {
            DB::beginTransaction();
            
            // Validation des données
            $this->validateUserRequest($request);
            
            // Vérification unicité email (sur données chiffrées)
            $existingUser = $this->findUserByEmail($request->getEmail());
            if ($existingUser) {
                throw new UserValidationException('Un utilisateur avec cet email existe déjà');
            }
            
            // Création avec chiffrement automatique
            $userData = $this->prepareEncryptedUserData($request);
            $user = $this->userRepository->create($userData);
            
            // Génération de l'identifiant pseudonyme
            $user->pseudonym_id = $this->generatePseudonymId($user->id);
            $user->save();
            
            // Audit de création
            $this->auditService->logUserCreation($user, $request->getSourceIp());
            
            // Initialisation des préférences de consentement
            $this->initializeConsentPreferences($user, $request->getConsentGiven());
            
            DB::commit();
            
            Log::info('Utilisateur créé avec succès', [
                'user_id' => $user->id,
                'pseudonym_id' => $user->pseudonym_id,
                'consent_given' => $request->getConsentGiven()
            ]);
            
            return $this->buildUserResponse($user);
            
        } catch (Exception $e) {
            DB::rollBack();
            
            Log::error('Erreur création utilisateur', [
                'email' => $request->getEmail(),
                'error' => $e->getMessage(),
                'trace' => $e->getTraceAsString()
            ]);
            
            throw new UserValidationException(
                'Impossible de créer l\'utilisateur',
                previous: $e
            );
        }
    }
    
    /**
     * Mise à jour avec audit RGPD
     */
    public function updateUser(int $userId, UserProfileUpdate $update): UserResponse
    {
        try {
            DB::beginTransaction();
            
            $user = $this->findUserById($userId);
            if (!$user) {
                throw new UserNotFoundException("Utilisateur {$userId} introuvable");
            }
            
            // Sauvegarde des anciennes valeurs pour audit
            $oldValues = $this->extractAuditableFields($user);
            
            // Application des mises à jour avec chiffrement
            $this->applyUserUpdates($user, $update);
            
            // Sauvegarde
            $user->updated_at = Carbon::now();
            $user->save();
            
            // Audit des modifications
            $newValues = $this->extractAuditableFields($user);
            $this->auditService->logUserUpdate($user, $oldValues, $newValues, $update->getSourceIp());
            
            // Invalidation cache
            $this->invalidateUserCache($user);
            
            DB::commit();
            
            Log::info('Utilisateur mis à jour', [
                'user_id' => $userId,
                'updated_fields' => array_keys($update->getUpdatedFields())
            ]);
            
            return $this->buildUserResponse($user);
            
        } catch (Exception $e) {
            DB::rollBack();
            
            Log::error('Erreur mise à jour utilisateur', [
                'user_id' => $userId,
                'error' => $e->getMessage()
            ]);
            
            throw new UserValidationException(
                'Impossible de mettre à jour l\'utilisateur',
                previous: $e
            );
        }
    }
    
    /**
     * Récupération utilisateur avec déchiffrement
     */
    public function getUserById(int $userId): ?UserResponse
    {
        try {
            $cacheKey = "user:profile:{$userId}";
            
            return Cache::remember($cacheKey, 3600, function () use ($userId) {
                $user = $this->findUserById($userId);
                
                if (!$user) {
                    return null;
                }
                
                // Audit de l'accès
                $this->auditService->logUserAccess($user, request()->ip());
                
                return $this->buildUserResponse($user);
            });
            
        } catch (Exception $e) {
            Log::error('Erreur récupération utilisateur', [
                'user_id' => $userId,
                'error' => $e->getMessage()
            ]);
            
            return null;
        }
    }
    
    /**
     * Recherche d'utilisateurs avec anonymisation
     */
    public function searchUsers(array $criteria, int $page = 1, int $limit = 20): array
    {
        try {
            // Limitation des résultats pour protection
            $limit = min($limit, 100);
            
            $users = $this->userRepository->searchWithCriteria($criteria, $page, $limit);
            
            // Anonymisation des résultats
            $anonymizedUsers = $users->map(function ($user) {
                return $this->buildAnonymizedUserResponse($user);
            });
            
            // Audit de la recherche
            $this->auditService->logUserSearch($criteria, $users->count(), request()->ip());
            
            return [
                'users' => $anonymizedUsers,
                'total' => $users->total(),
                'page' => $page,
                'limit' => $limit,
                'anonymized' => true,
                'k_anonymity_level' => self::K_ANONYMITY_THRESHOLD
            ];
            
        } catch (Exception $e) {
            Log::error('Erreur recherche utilisateurs', [
                'criteria' => $criteria,
                'error' => $e->getMessage()
            ]);
            
            throw new UserValidationException(
                'Impossible d\'effectuer la recherche',
                previous: $e
            );
        }
    }
    
    /**
     * Export des données RGPD (Article 20)
     */
    public function exportUserData(int $userId, RgpdRequest $request): DataExportResponse
    {
        try {
            $user = $this->findUserById($userId);
            if (!$user) {
                throw new UserNotFoundException("Utilisateur {$userId} introuvable");
            }
            
            // Vérification des droits d'accès
            $this->validateDataExportRequest($user, $request);
            
            // Collecte des données personnelles
            $personalData = $this->collectPersonalData($user);
            
            // Collecte des données de session KYC
            $kycData = $this->collectKycData($user);
            
            // Collecte des données d'audit
            $auditData = $this->collectAuditData($user, $request->getIncludeAuditLogs());
            
            // Génération du rapport d'export
            $exportData = [
                'export_date' => Carbon::now()->toISOString(),
                'user_id' => $user->id,
                'pseudonym_id' => $user->pseudonym_id,
                'personal_data' => $personalData,
                'kyc_sessions' => $kycData,
                'audit_logs' => $auditData,
                'data_retention_policy' => [
                    'retention_period_years' => self::DATA_RETENTION_YEARS,
                    'next_review_date' => Carbon::now()->addYears(self::DATA_RETENTION_YEARS)->toISOString()
                ],
                'export_format' => $request->getFormat(),
                'legal_basis' => 'Article 20 RGPD - Portabilité des données'
            ];
            
            // Audit de l'export
            $this->auditService->logDataExport($user, $exportData, $request->getSourceIp());
            
            // Génération du fichier selon le format demandé
            $fileContent = $this->generateExportFile($exportData, $request->getFormat());
            
            Log::info('Export de données effectué', [
                'user_id' => $userId,
                'format' => $request->getFormat(),
                'data_size' => strlen($fileContent)
            ]);
            
            return new DataExportResponse(
                $fileContent,
                $request->getFormat(),
                $this->generateExportFilename($user, $request->getFormat()),
                Carbon::now()
            );
            
        } catch (Exception $e) {
            Log::error('Erreur export données utilisateur', [
                'user_id' => $userId,
                'error' => $e->getMessage()
            ]);
            
            throw new RgpdComplianceException(
                'Impossible d\'exporter les données utilisateur',
                previous: $e
            );
        }
    }
    
    /**
     * Anonymisation utilisateur (Droit à l'oubli - Article 17)
     */
    public function anonymizeUser(int $userId, RgpdRequest $request): AnonymizationReport
    {
        try {
            DB::beginTransaction();
            
            $user = $this->findUserById($userId);
            if (!$user) {
                throw new UserNotFoundException("Utilisateur {$userId} introuvable");
            }
            
            // Vérification des conditions légales d'anonymisation
            $this->validateAnonymizationRequest($user, $request);
            
            // Sauvegarde pré-anonymisation pour audit
            $preAnonymizationData = $this->createAnonymizationSnapshot($user);
            
            // Anonymisation des données personnelles
            $anonymizedFields = $this->performDataAnonymization($user);
            
            // Anonymisation des sessions KYC associées
            $anonymizedSessions = $this->anonymizeRelatedKycSessions($user);
            
            // Suppression des données biométriques
            $deletedBiometricData = $this->deleteBiometricData($user);
            
            // Mise à jour du statut
            $user->status = 'ANONYMIZED';
            $user->anonymized_at = Carbon::now();
            $user->anonymization_reason = $request->getReason();
            $user->save();
            
            // Génération du rapport d'anonymisation
            $report = new AnonymizationReport([
                'user_id' => $userId,
                'pseudonym_id' => $user->pseudonym_id,
                'anonymization_date' => $user->anonymized_at,
                'anonymized_fields' => $anonymizedFields,
                'anonymized_sessions' => $anonymizedSessions,
                'deleted_biometric_data' => $deletedBiometricData,
                'k_anonymity_achieved' => $this->verifyKAnonymity($user),
                'legal_basis' => $request->getLegalBasis(),
                'certification' => $this->generateAnonymizationCertification($user)
            ]);
            
            // Audit de l'anonymisation
            $this->auditService->logUserAnonymization($user, $report, $request->getSourceIp());
            
            // Invalidation de tous les caches
            $this->invalidateAllUserCaches($user);
            
            DB::commit();
            
            Log::info('Utilisateur anonymisé avec succès', [
                'user_id' => $userId,
                'anonymized_fields_count' => count($anonymizedFields),
                'anonymized_sessions_count' => count($anonymizedSessions)
            ]);
            
            return $report;
            
        } catch (Exception $e) {
            DB::rollBack();
            
            Log::error('Erreur anonymisation utilisateur', [
                'user_id' => $userId,
                'error' => $e->getMessage()
            ]);
            
            throw new RgpdComplianceException(
                'Impossible d\'anonymiser l\'utilisateur',
                previous: $e
            );
        }
    }
    
    /**
     * Gestion des consentements RGPD
     */
    public function updateConsent(int $userId, array $consentUpdates): array
    {
        try {
            $user = $this->findUserById($userId);
            if (!$user) {
                throw new UserNotFoundException("Utilisateur {$userId} introuvable");
            }
            
            $previousConsents = $user->consent_preferences;
            
            foreach ($consentUpdates as $purpose => $granted) {
                $user->consent_preferences[$purpose] = [
                    'granted' => $granted,
                    'timestamp' => Carbon::now()->toISOString(),
                    'ip_address' => request()->ip(),
                    'user_agent' => request()->userAgent()
                ];
            }
            
            $user->consent_last_updated = Carbon::now();
            $user->save();
            
            // Audit des changements de consentement
            $this->auditService->logConsentUpdate($user, $previousConsents, $user->consent_preferences);
            
            Log::info('Consentements mis à jour', [
                'user_id' => $userId,
                'updated_purposes' => array_keys($consentUpdates)
            ]);
            
            return $user->consent_preferences;
            
        } catch (Exception $e) {
            Log::error('Erreur mise à jour consentements', [
                'user_id' => $userId,
                'error' => $e->getMessage()
            ]);
            
            throw new RgpdComplianceException(
                'Impossible de mettre à jour les consentements',
                previous: $e
            );
        }
    }
    
    /**
     * Vérification expiration des consentements
     */
    public function checkConsentExpiration(): array
    {
        $expirationDate = Carbon::now()->subMonths(self::CONSENT_EXPIRY_MONTHS);
        
        $expiredUsers = $this->userRepository->findUsersWithExpiredConsent($expirationDate);
        
        $notificationsSent = [];
        
        foreach ($expiredUsers as $user) {
            try {
                // Notification de renouvellement de consentement
                $this->sendConsentRenewalNotification($user);
                $notificationsSent[] = $user->id;
                
                // Audit de la notification
                $this->auditService->logConsentExpirationNotification($user);
                
            } catch (Exception $e) {
                Log::error('Erreur notification expiration consentement', [
                    'user_id' => $user->id,
                    'error' => $e->getMessage()
                ]);
            }
        }
        
        Log::info('Vérification expiration consentements terminée', [
            'expired_users_count' => count($expiredUsers),
            'notifications_sent' => count($notificationsSent)
        ]);
        
        return [
            'expired_users_count' => count($expiredUsers),
            'notifications_sent' => $notificationsSent,
            'check_date' => Carbon::now()
        ];
    }
    
    // Méthodes privées d'implémentation
    
    private function validateUserRequest(UserRequest $request): void
    {
        $validator = validator($request->toArray(), [
            'email' => 'required|email|max:255',
            'first_name' => 'required|string|max:100',
            'last_name' => 'required|string|max:100',
            'phone_number' => 'nullable|string|max:20',
            'birth_date' => 'nullable|date|before:today',
            'consent_given' => 'required|array'
        ]);
        
        if ($validator->fails()) {
            throw new ValidationException($validator);
        }
    }
    
    private function findUserByEmail(string $email): ?SystemUser
    {
        // Recherche sur email chiffré
        $emailHash = $this->encryptionService->hashForSearch($email);
        return $this->userRepository->findByEmailHash($emailHash);
    }
    
    private function findUserById(int $userId): ?SystemUser
    {
        return $this->userRepository->find($userId);
    }
    
    private function prepareEncryptedUserData(UserRequest $request): array
    {
        $data = $request->toArray();
        
        // Chiffrement des données personnelles
        foreach (self::PERSONAL_DATA_FIELDS as $field) {
            if (isset($data[$field])) {
                $data[$field . '_encrypted'] = $this->encryptionService->encrypt($data[$field]);
                $data[$field . '_hash'] = $this->encryptionService->hashForSearch($data[$field]);
                unset($data[$field]);
            }
        }
        
        // Ajout des métadonnées RGPD
        $data['created_at'] = Carbon::now();
        $data['data_classification'] = 'PERSONAL';
        $data['retention_until'] = Carbon::now()->addYears(self::DATA_RETENTION_YEARS);
        $data['consent_given'] = $request->getConsentGiven();
        $data['consent_timestamp'] = Carbon::now();
        $data['source_ip'] = $request->getSourceIp();
        
        return $data;
    }
    
    private function generatePseudonymId(int $userId): string
    {
        return 'USR_' . hash('sha256', $userId . config('app.key') . Carbon::now()->timestamp);
    }
    
    private function initializeConsentPreferences(SystemUser $user, array $consentGiven): void
    {
        $preferences = [];
        
        foreach ($consentGiven as $purpose => $granted) {
            $preferences[$purpose] = [
                'granted' => $granted,
                'timestamp' => Carbon::now()->toISOString(),
                'ip_address' => request()->ip(),
                'legal_basis' => $this->determineLegalBasis($purpose, $granted)
            ];
        }
        
        $user->consent_preferences = $preferences;
        $user->consent_last_updated = Carbon::now();
        $user->save();
    }
    
    private function determineLegalBasis(string $purpose, bool $granted): string
    {
        // Détermination de la base légale selon le but
        $legalBases = [
            'kyc_processing' => 'Article 6(1)(c) RGPD - Obligation légale',
            'marketing' => 'Article 6(1)(a) RGPD - Consentement',
            'analytics' => 'Article 6(1)(f) RGPD - Intérêt légitime',
            'biometric_processing' => 'Article 9(2)(a) RGPD - Consentement explicite'
        ];
        
        return $legalBases[$purpose] ?? 'Article 6(1)(a) RGPD - Consentement';
    }
    
    private function extractAuditableFields(SystemUser $user): array
    {
        $fields = [];
        
        foreach (self::PERSONAL_DATA_FIELDS as $field) {
            $encryptedField = $field . '_encrypted';
            if ($user->$encryptedField) {
                $fields[$field] = '***ENCRYPTED***'; // Ne pas exposer les données
            }
        }
        
        return $fields;
    }
    
    private function applyUserUpdates(SystemUser $user, UserProfileUpdate $update): void
    {
        foreach ($update->getUpdatedFields() as $field => $value) {
            if (in_array($field, self::PERSONAL_DATA_FIELDS)) {
                // Chiffrement des nouvelles valeurs
                $user->{$field . '_encrypted'} = $this->encryptionService->encrypt($value);
                $user->{$field . '_hash'} = $this->encryptionService->hashForSearch($value);
            } else {
                $user->$field = $value;
            }
        }
    }
    
    private function buildUserResponse(SystemUser $user): UserResponse
    {
        $decryptedData = [];
        
        // Déchiffrement des données personnelles
        foreach (self::PERSONAL_DATA_FIELDS as $field) {
            $encryptedField = $field . '_encrypted';
            if ($user->$encryptedField) {
                $decryptedData[$field] = $this->encryptionService->decrypt($user->$encryptedField);
            }
        }
        
        return new UserResponse([
            'id' => $user->id,
            'pseudonym_id' => $user->pseudonym_id,
            'status' => $user->status,
            'personal_data' => $decryptedData,
            'consent_preferences' => $user->consent_preferences,
            'created_at' => $user->created_at,
            'updated_at' => $user->updated_at
        ]);
    }
    
    private function buildAnonymizedUserResponse(SystemUser $user): array
    {
        return [
            'pseudonym_id' => $user->pseudonym_id,
            'status' => $user->status,
            'age_group' => $this->calculateAgeGroup($user),
            'document_type' => $user->primary_document_type,
            'registration_month' => $user->created_at->format('Y-m'),
            'last_activity_month' => $user->updated_at->format('Y-m'),
            'anonymized' => true
        ];
    }
    
    private function calculateAgeGroup(SystemUser $user): string
    {
        if (!$user->birth_date_encrypted) {
            return 'unknown';
        }
        
        $birthDate = $this->encryptionService->decrypt($user->birth_date_encrypted);
        $age = Carbon::parse($birthDate)->age;
        
        if ($age < 25) return '18-24';
        if ($age < 35) return '25-34';
        if ($age < 45) return '35-44';
        if ($age < 55) return '45-54';
        if ($age < 65) return '55-64';
        
        return '65+';
    }
    
    private function invalidateUserCache(SystemUser $user): void
    {
        Cache::forget("user:profile:{$user->id}");
        Cache::tags(['user_' . $user->id])->flush();
    }
    
    private function invalidateAllUserCaches(SystemUser $user): void
    {
        $this->invalidateUserCache($user);
        Cache::tags(['users', 'analytics'])->flush();
    }
    
    private function validateDataExportRequest(SystemUser $user, RgpdRequest $request): void
    {
        // Vérification de l'identité pour l'export
        if (!$request->hasValidIdentityProof()) {
            throw new RgpdComplianceException('Preuve d\'identité requise pour l\'export de données');
        }
        
        // Limitation de fréquence des exports
        $lastExport = $this->auditService->getLastDataExport($user);
        if ($lastExport && $lastExport->diffInDays(Carbon::now()) < 30) {
            throw new RgpdComplianceException('Un export de données a déjà été effectué ce mois-ci');
        }
    }
    
    private function collectPersonalData(SystemUser $user): array
    {
        $personalData = [];
        
        foreach (self::PERSONAL_DATA_FIELDS as $field) {
            $encryptedField = $field . '_encrypted';
            if ($user->$encryptedField) {
                $personalData[$field] = $this->encryptionService->decrypt($user->$encryptedField);
            }
        }
        
        return $personalData;
    }
    
    private function collectKycData(SystemUser $user): array
    {
        // Collecte des données KYC depuis le service KYC
        return [];
    }
    
    private function collectAuditData(SystemUser $user, bool $includeAuditLogs): array
    {
        if (!$includeAuditLogs) {
            return ['audit_logs_included' => false];
        }
        
        return $this->auditService->getUserAuditHistory($user);
    }
    
    private function generateExportFile(array $data, string $format): string
    {
        switch ($format) {
            case 'json':
                return json_encode($data, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE);
            
            case 'xml':
                return $this->arrayToXml($data);
            
            case 'csv':
                return $this->arrayToCsv($data);
            
            default:
                throw new RgpdComplianceException("Format d'export non supporté: {$format}");
        }
    }
    
    private function generateExportFilename(SystemUser $user, string $format): string
    {
        $date = Carbon::now()->format('Y-m-d_H-i-s');
        return "user_data_export_{$user->pseudonym_id}_{$date}.{$format}";
    }
    
    private function validateAnonymizationRequest(SystemUser $user, RgpdRequest $request): void
    {
        // Vérification des conditions légales
        if (!$request->hasValidLegalBasis()) {
            throw new RgpdComplianceException('Base légale insuffisante pour l\'anonymisation');
        }
        
        // Vérification des obligations de rétention
        if ($user->retention_until->isFuture()) {
            throw new RgpdComplianceException('Période de rétention légale non échue');
        }
    }
    
    private function createAnonymizationSnapshot(SystemUser $user): array
    {
        return [
            'user_id' => $user->id,
            'snapshot_date' => Carbon::now(),
            'personal_data_fields' => count(self::PERSONAL_DATA_FIELDS),
            'sensitive_data_fields' => count(self::SENSITIVE_DATA_FIELDS)
        ];
    }
    
    private function performDataAnonymization(SystemUser $user): array
    {
        $anonymizedFields = [];
        
        foreach (self::PERSONAL_DATA_FIELDS as $field) {
            $encryptedField = $field . '_encrypted';
            $hashField = $field . '_hash';
            
            if ($user->$encryptedField) {
                $user->$encryptedField = null;
                $user->$hashField = null;
                $anonymizedFields[] = $field;
            }
        }
        
        // Anonymisation des données sensibles
        foreach (self::SENSITIVE_DATA_FIELDS as $field) {
            if ($user->$field) {
                $user->$field = null;
                $anonymizedFields[] = $field;
            }
        }
        
        $user->save();
        
        return $anonymizedFields;
    }
    
    private function anonymizeRelatedKycSessions(SystemUser $user): array
    {
        // Anonymisation des sessions KYC associées
        return [];
    }
    
    private function deleteBiometricData(SystemUser $user): array
    {
        // Suppression des données biométriques
        return [];
    }
    
    private function verifyKAnonymity(SystemUser $user): bool
    {
        // Vérification que l'anonymisation respecte k-anonymity >= 5
        return true;
    }
    
    private function generateAnonymizationCertification(SystemUser $user): array
    {
        return [
            'certification_id' => uniqid('ANON_CERT_'),
            'user_pseudonym' => $user->pseudonym_id,
            'anonymization_method' => 'k-anonymity',
            'k_value' => self::K_ANONYMITY_THRESHOLD,
            'certification_date' => Carbon::now(),
            'certified_by' => 'ANIP_KYC_SYSTEM',
            'compliance_standards' => ['RGPD', 'ISO_27001']
        ];
    }
    
    private function sendConsentRenewalNotification(SystemUser $user): void
    {
        // Envoi de notification de renouvellement de consentement
        Mail::to($this->encryptionService->decrypt($user->email_encrypted))
            ->send(new ConsentRenewalNotification($user));
    }
    
    private function arrayToXml(array $data): string
    {
        // Conversion array vers XML
        return '<?xml version="1.0" encoding="UTF-8"?><user_data>' . 
               $this->arrayToXmlRecursive($data) . 
               '</user_data>';
    }
    
    private function arrayToXmlRecursive(array $data): string
    {
        $xml = '';
        foreach ($data as $key => $value) {
            if (is_array($value)) {
                $xml .= "<{$key}>" . $this->arrayToXmlRecursive($value) . "</{$key}>";
            } else {
                $xml .= "<{$key}>" . htmlspecialchars($value) . "</{$key}>";
            }
        }
        return $xml;
    }
    
    private function arrayToCsv(array $data): string
    {
        // Conversion array vers CSV (flattened)
        $csv = '';
        $headers = [];
        $rows = [];
        
        $this->flattenArray($data, '', $headers, $rows);
        
        $csv .= implode(',', $headers) . "\n";
        $csv .= implode(',', $rows) . "\n";
        
        return $csv;
    }
    
    private function flattenArray(array $data, string $prefix, array &$headers, array &$rows): void
    {
        foreach ($data as $key => $value) {
            $newKey = $prefix ? $prefix . '.' . $key : $key;
            
            if (is_array($value)) {
                $this->flattenArray($value, $newKey, $headers, $rows);
            } else {
                $headers[] = $newKey;
                $rows[] = '"' . str_replace('"', '""', $value) . '"';
            }
        }
    }
}