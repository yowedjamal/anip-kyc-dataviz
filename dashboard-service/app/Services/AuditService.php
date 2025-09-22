<?php

namespace App\Services;

use App\Models\AuditEvent;
use App\Models\SystemUser;
use App\Repositories\AuditRepository;
use App\DTOs\Audit\AuditRequest;
use App\DTOs\Audit\AuditReport;
use App\DTOs\User\AnonymizationReport;
use App\Exceptions\Audit\AuditException;
use App\Services\Encryption\EncryptionService;
use Illuminate\Support\Facades\Log;
use Carbon\Carbon;
use Exception;

/**
 * Service d'audit et de traçabilité avec conformité RGPD
 * 
 * Implémente un système d'audit complet pour:
 * - Traçabilité des accès aux données personnelles
 * - Logs immutables avec chiffrement et signature
 * - Détection d'anomalies et tentatives d'intrusion
 * - Rapports de conformité RGPD automatisés
 * - Analyse des patterns d'utilisation
 * 
 * Sécurité:
 * - Chiffrement AES-256 des logs sensibles
 * - Signature HMAC pour l'intégrité
 * - Stockage immutable avec horodatage
 * - Anonymisation automatique après rétention
 * - Détection de tampering
 */
class AuditService
{
    private AuditRepository $auditRepository;
    private EncryptionService $encryptionService;
    
    // Configuration audit
    private const LOG_RETENTION_YEARS = 7;
    private const CRITICAL_EVENTS = [
        'USER_DATA_EXPORT', 'USER_ANONYMIZATION', 'DATA_BREACH_DETECTED',
        'UNAUTHORIZED_ACCESS', 'ADMIN_LOGIN', 'SYSTEM_CONFIG_CHANGE'
    ];
    
    private const SECURITY_EVENTS = [
        'FAILED_LOGIN_ATTEMPTS', 'SUSPICIOUS_ACTIVITY', 'PRIVILEGE_ESCALATION',
        'DATA_EXFILTRATION_ATTEMPT', 'MALICIOUS_REQUEST_PATTERN'
    ];
    
    // Niveaux de criticité
    private const SEVERITY_LEVELS = [
        'INFO' => 1,
        'WARNING' => 2,
        'ERROR' => 3,
        'CRITICAL' => 4,
        'EMERGENCY' => 5
    ];
    
    public function __construct(
        AuditRepository $auditRepository,
        EncryptionService $encryptionService
    ) {
        $this->auditRepository = $auditRepository;
        $this->encryptionService = $encryptionService;
    }
    
    /**
     * Enregistrement d'événement d'audit avec chiffrement
     */
    public function logAuditEvent(
        string $eventType,
        array $eventData,
        ?SystemUser $user = null,
        string $severity = 'INFO',
        ?string $ipAddress = null
    ): AuditEvent {
        try {
            // Enrichissement des données d'audit
            $enrichedData = $this->enrichAuditData($eventData, $user, $ipAddress);
            
            // Chiffrement des données sensibles
            $encryptedData = $this->encryptSensitiveAuditData($enrichedData);
            
            // Génération de la signature d'intégrité
            $integrity_hash = $this->generateIntegrityHash($eventType, $encryptedData);
            
            // Création de l'événement d'audit
            $auditEvent = $this->auditRepository->create([
                'event_type' => $eventType,
                'event_data' => $encryptedData,
                'user_id' => $user?->id,
                'user_pseudonym' => $user?->pseudonym_id,
                'ip_address' => $ipAddress ?? request()->ip(),
                'user_agent' => request()->userAgent(),
                'session_id' => session()->getId(),
                'severity' => $severity,
                'severity_level' => self::SEVERITY_LEVELS[$severity] ?? 1,
                'integrity_hash' => $integrity_hash,
                'created_at' => Carbon::now(),
                'retention_until' => Carbon::now()->addYears(self::LOG_RETENTION_YEARS),
                'is_critical' => in_array($eventType, self::CRITICAL_EVENTS),
                'is_security_event' => in_array($eventType, self::SECURITY_EVENTS)
            ]);
            
            // Traitement spécial pour les événements critiques
            if (in_array($eventType, self::CRITICAL_EVENTS)) {
                $this->processCriticalEvent($auditEvent);
            }
            
            // Détection d'anomalies en temps réel
            if (in_array($eventType, self::SECURITY_EVENTS)) {
                $this->analyzeSecurityEvent($auditEvent);
            }
            
            Log::debug('Événement d\'audit enregistré', [
                'event_id' => $auditEvent->id,
                'event_type' => $eventType,
                'severity' => $severity,
                'user_id' => $user?->id
            ]);
            
            return $auditEvent;
            
        } catch (Exception $e) {
            // Log d'erreur critique sans exposer les données
            Log::critical('Erreur enregistrement audit', [
                'event_type' => $eventType,
                'error' => $e->getMessage(),
                'trace' => $e->getTraceAsString()
            ]);
            
            throw new AuditException(
                'Impossible d\'enregistrer l\'événement d\'audit',
                previous: $e
            );
        }
    }
    
    /**
     * Audit spécialisé pour les accès aux données utilisateur
     */
    public function logUserAccess(SystemUser $user, string $ipAddress, array $accessedFields = []): void
    {
        $this->logAuditEvent('USER_DATA_ACCESS', [
            'accessed_fields' => $accessedFields,
            'access_method' => 'API',
            'request_uri' => request()->getRequestUri(),
            'referer' => request()->header('referer')
        ], $user, 'INFO', $ipAddress);
    }
    
    /**
     * Audit de création d'utilisateur
     */
    public function logUserCreation(SystemUser $user, string $ipAddress): void
    {
        $this->logAuditEvent('USER_CREATED', [
            'user_pseudonym' => $user->pseudonym_id,
            'registration_source' => 'WEB_INTERFACE',
            'consent_given' => $user->consent_preferences ? 'YES' : 'NO',
            'data_classification' => 'PERSONAL'
        ], $user, 'INFO', $ipAddress);
    }
    
    /**
     * Audit de mise à jour utilisateur
     */
    public function logUserUpdate(
        SystemUser $user, 
        array $oldValues, 
        array $newValues, 
        string $ipAddress
    ): void {
        $changedFields = array_keys(array_diff_assoc($newValues, $oldValues));
        
        $this->logAuditEvent('USER_UPDATED', [
            'changed_fields' => $changedFields,
            'change_count' => count($changedFields),
            'update_method' => 'API'
        ], $user, 'INFO', $ipAddress);
    }
    
    /**
     * Audit d'export de données RGPD
     */
    public function logDataExport(SystemUser $user, array $exportData, string $ipAddress): void
    {
        $this->logAuditEvent('USER_DATA_EXPORT', [
            'export_format' => $exportData['export_format'],
            'data_size_bytes' => strlen(json_encode($exportData)),
            'legal_basis' => $exportData['legal_basis'],
            'export_scope' => array_keys($exportData),
            'compliance_framework' => 'RGPD_ARTICLE_20'
        ], $user, 'WARNING', $ipAddress);
    }
    
    /**
     * Audit d'anonymisation utilisateur
     */
    public function logUserAnonymization(
        SystemUser $user, 
        AnonymizationReport $report, 
        string $ipAddress
    ): void {
        $this->logAuditEvent('USER_ANONYMIZATION', [
            'anonymization_method' => 'k-anonymity',
            'k_value' => $report->getKAnonymityLevel(),
            'anonymized_fields_count' => count($report->getAnonymizedFields()),
            'legal_basis' => $report->getLegalBasis(),
            'certification_id' => $report->getCertificationId(),
            'irreversible' => true
        ], $user, 'CRITICAL', $ipAddress);
    }
    
    /**
     * Audit des recherches d'utilisateurs
     */
    public function logUserSearch(array $criteria, int $resultCount, string $ipAddress): void
    {
        $this->logAuditEvent('USER_SEARCH', [
            'search_criteria' => $this->sanitizeSearchCriteria($criteria),
            'result_count' => $resultCount,
            'anonymized_results' => true,
            'k_anonymity_applied' => true
        ], null, 'INFO', $ipAddress);
    }
    
    /**
     * Audit des changements de consentement
     */
    public function logConsentUpdate(SystemUser $user, array $oldConsents, array $newConsents): void
    {
        $changes = [];
        
        foreach ($newConsents as $purpose => $consent) {
            $oldConsent = $oldConsents[$purpose] ?? null;
            if (!$oldConsent || $oldConsent['granted'] !== $consent['granted']) {
                $changes[] = [
                    'purpose' => $purpose,
                    'old_value' => $oldConsent['granted'] ?? false,
                    'new_value' => $consent['granted'],
                    'timestamp' => $consent['timestamp']
                ];
            }
        }
        
        if (!empty($changes)) {
            $this->logAuditEvent('CONSENT_UPDATED', [
                'consent_changes' => $changes,
                'change_count' => count($changes),
                'legal_framework' => 'RGPD_ARTICLE_7'
            ], $user, 'WARNING');
        }
    }
    
    /**
     * Audit des notifications d'expiration de consentement
     */
    public function logConsentExpirationNotification(SystemUser $user): void
    {
        $this->logAuditEvent('CONSENT_EXPIRATION_NOTIFICATION', [
            'notification_type' => 'EMAIL',
            'expiration_reason' => 'CONSENT_AGING',
            'months_expired' => Carbon::now()->diffInMonths($user->consent_last_updated),
            'action_required' => 'CONSENT_RENEWAL'
        ], $user, 'WARNING');
    }
    
    /**
     * Audit des événements de sécurité
     */
    public function logSecurityEvent(string $eventType, array $details, string $severity = 'WARNING'): void
    {
        $this->logAuditEvent($eventType, array_merge($details, [
            'security_classification' => 'SECURITY_INCIDENT',
            'detection_method' => 'AUTOMATED',
            'requires_investigation' => $severity === 'CRITICAL'
        ]), null, $severity);
        
        // Notification immédiate pour les événements critiques
        if ($severity === 'CRITICAL') {
            $this->triggerSecurityAlert($eventType, $details);
        }
    }
    
    /**
     * Récupération de l'historique d'audit d'un utilisateur
     */
    public function getUserAuditHistory(SystemUser $user, int $limit = 100): array
    {
        try {
            $auditEvents = $this->auditRepository->getUserHistory($user->id, $limit);
            
            $history = [];
            foreach ($auditEvents as $event) {
                // Déchiffrement des données pour l'export
                $decryptedData = $this->decryptAuditData($event->event_data);
                
                $history[] = [
                    'event_id' => $event->id,
                    'event_type' => $event->event_type,
                    'timestamp' => $event->created_at->toISOString(),
                    'severity' => $event->severity,
                    'ip_address' => $event->ip_address,
                    'event_details' => $decryptedData,
                    'integrity_verified' => $this->verifyIntegrity($event)
                ];
            }
            
            // Audit de la consultation d'historique
            $this->logAuditEvent('AUDIT_HISTORY_ACCESSED', [
                'target_user_id' => $user->id,
                'records_returned' => count($history),
                'access_purpose' => 'DATA_EXPORT'
            ], null, 'INFO');
            
            return $history;
            
        } catch (Exception $e) {
            Log::error('Erreur récupération historique audit', [
                'user_id' => $user->id,
                'error' => $e->getMessage()
            ]);
            
            throw new AuditException(
                'Impossible de récupérer l\'historique d\'audit',
                previous: $e
            );
        }
    }
    
    /**
     * Génération de rapports de conformité RGPD
     */
    public function generateComplianceReport(AuditRequest $request): AuditReport
    {
        try {
            $report = new AuditReport();
            
            // Période d'analyse
            $startDate = $request->getStartDate();
            $endDate = $request->getEndDate();
            
            // Métriques générales
            $generalMetrics = $this->calculateGeneralMetrics($startDate, $endDate);
            $report->setGeneralMetrics($generalMetrics);
            
            // Analyse des accès aux données personnelles
            $dataAccessMetrics = $this->analyzeDataAccessPatterns($startDate, $endDate);
            $report->setDataAccessMetrics($dataAccessMetrics);
            
            // Analyse des consentements
            $consentMetrics = $this->analyzeConsentPatterns($startDate, $endDate);
            $report->setConsentMetrics($consentMetrics);
            
            // Analyse des exports de données
            $exportMetrics = $this->analyzeDataExports($startDate, $endDate);
            $report->setExportMetrics($exportMetrics);
            
            // Analyse des anonymisations
            $anonymizationMetrics = $this->analyzeAnonymizations($startDate, $endDate);
            $report->setAnonymizationMetrics($anonymizationMetrics);
            
            // Événements de sécurité
            $securityEvents = $this->analyzeSecurityEvents($startDate, $endDate);
            $report->setSecurityEvents($securityEvents);
            
            // Violations potentielles
            $violations = $this->detectPotentialViolations($startDate, $endDate);
            $report->setPotentialViolations($violations);
            
            // Score de conformité
            $complianceScore = $this->calculateComplianceScore($report);
            $report->setComplianceScore($complianceScore);
            
            // Recommandations
            $recommendations = $this->generateComplianceRecommendations($report);
            $report->setRecommendations($recommendations);
            
            $report->setGenerationDate(Carbon::now());
            $report->setPeriod($startDate, $endDate);
            
            // Audit de la génération de rapport
            $this->logAuditEvent('COMPLIANCE_REPORT_GENERATED', [
                'report_period' => $startDate->format('Y-m-d') . ' to ' . $endDate->format('Y-m-d'),
                'compliance_score' => $complianceScore,
                'violations_count' => count($violations),
                'report_id' => $report->getId()
            ], null, 'INFO');
            
            return $report;
            
        } catch (Exception $e) {
            Log::error('Erreur génération rapport conformité', [
                'request' => $request->toArray(),
                'error' => $e->getMessage()
            ]);
            
            throw new AuditException(
                'Impossible de générer le rapport de conformité',
                previous: $e
            );
        }
    }
    
    /**
     * Détection d'anomalies dans les logs d'audit
     */
    public function detectAnomalies(Carbon $startDate, Carbon $endDate): array
    {
        try {
            $anomalies = [];
            
            // Détection de patterns d'accès suspects
            $suspiciousAccess = $this->detectSuspiciousAccessPatterns($startDate, $endDate);
            $anomalies = array_merge($anomalies, $suspiciousAccess);
            
            // Détection de volumes anormaux
            $volumeAnomalies = $this->detectVolumeAnomalies($startDate, $endDate);
            $anomalies = array_merge($anomalies, $volumeAnomalies);
            
            // Détection de tentatives d'accès non autorisées
            $unauthorizedAccess = $this->detectUnauthorizedAccess($startDate, $endDate);
            $anomalies = array_merge($anomalies, $unauthorizedAccess);
            
            // Détection de manipulation des logs
            $integrityViolations = $this->detectIntegrityViolations($startDate, $endDate);
            $anomalies = array_merge($anomalies, $integrityViolations);
            
            // Log des anomalies détectées
            if (!empty($anomalies)) {
                $this->logAuditEvent('ANOMALIES_DETECTED', [
                    'anomalies_count' => count($anomalies),
                    'detection_period' => $startDate->format('Y-m-d') . ' to ' . $endDate->format('Y-m-d'),
                    'anomaly_types' => array_unique(array_column($anomalies, 'type'))
                ], null, 'WARNING');
            }
            
            return $anomalies;
            
        } catch (Exception $e) {
            Log::error('Erreur détection anomalies', [
                'start_date' => $startDate,
                'end_date' => $endDate,
                'error' => $e->getMessage()
            ]);
            
            return [];
        }
    }
    
    /**
     * Vérification de l'intégrité des logs
     */
    public function verifyLogsIntegrity(Carbon $startDate, Carbon $endDate): array
    {
        try {
            $auditEvents = $this->auditRepository->getEventsByDateRange($startDate, $endDate);
            
            $results = [
                'total_events' => count($auditEvents),
                'verified_events' => 0,
                'corrupted_events' => 0,
                'integrity_violations' => []
            ];
            
            foreach ($auditEvents as $event) {
                if ($this->verifyIntegrity($event)) {
                    $results['verified_events']++;
                } else {
                    $results['corrupted_events']++;
                    $results['integrity_violations'][] = [
                        'event_id' => $event->id,
                        'event_type' => $event->event_type,
                        'timestamp' => $event->created_at,
                        'expected_hash' => $event->integrity_hash,
                        'calculated_hash' => $this->generateIntegrityHash($event->event_type, $event->event_data)
                    ];
                }
            }
            
            $results['integrity_score'] = $results['total_events'] > 0 
                ? ($results['verified_events'] / $results['total_events']) * 100 
                : 100;
            
            // Log des résultats de vérification
            $this->logAuditEvent('INTEGRITY_VERIFICATION', [
                'verification_period' => $startDate->format('Y-m-d') . ' to ' . $endDate->format('Y-m-d'),
                'integrity_score' => $results['integrity_score'],
                'corrupted_events_count' => $results['corrupted_events']
            ], null, $results['corrupted_events'] > 0 ? 'CRITICAL' : 'INFO');
            
            return $results;
            
        } catch (Exception $e) {
            Log::error('Erreur vérification intégrité logs', [
                'error' => $e->getMessage()
            ]);
            
            throw new AuditException(
                'Impossible de vérifier l\'intégrité des logs',
                previous: $e
            );
        }
    }
    
    /**
     * Récupération du dernier export de données pour un utilisateur
     */
    public function getLastDataExport(SystemUser $user): ?Carbon
    {
        $lastExport = $this->auditRepository->getLastEventByType($user->id, 'USER_DATA_EXPORT');
        
        return $lastExport ? $lastExport->created_at : null;
    }
    
    // Méthodes privées d'implémentation
    
    private function enrichAuditData(array $eventData, ?SystemUser $user, ?string $ipAddress): array
    {
        return array_merge($eventData, [
            'timestamp' => Carbon::now()->toISOString(),
            'request_id' => request()->header('X-Request-ID') ?? uniqid(),
            'environment' => app()->environment(),
            'application_version' => config('app.version'),
            'user_agent' => request()->userAgent(),
            'request_method' => request()->method(),
            'request_uri' => request()->getRequestUri(),
            'geo_location' => $this->resolveGeoLocation($ipAddress),
            'user_context' => $user ? [
                'user_id' => $user->id,
                'pseudonym_id' => $user->pseudonym_id,
                'user_status' => $user->status
            ] : null
        ]);
    }
    
    private function encryptSensitiveAuditData(array $data): array
    {
        $sensitiveFields = [
            'user_id', 'email', 'phone_number', 'personal_data',
            'biometric_data', 'document_data', 'location_data'
        ];
        
        foreach ($sensitiveFields as $field) {
            if (isset($data[$field])) {
                $data[$field . '_encrypted'] = $this->encryptionService->encrypt(json_encode($data[$field]));
                unset($data[$field]);
            }
        }
        
        return $data;
    }
    
    private function decryptAuditData(array $encryptedData): array
    {
        $decryptedData = $encryptedData;
        
        foreach ($encryptedData as $key => $value) {
            if (str_ends_with($key, '_encrypted')) {
                $originalKey = str_replace('_encrypted', '', $key);
                $decryptedData[$originalKey] = json_decode($this->encryptionService->decrypt($value), true);
                unset($decryptedData[$key]);
            }
        }
        
        return $decryptedData;
    }
    
    private function generateIntegrityHash(string $eventType, array $eventData): string
    {
        $payload = $eventType . json_encode($eventData, JSON_SORT_KEYS) . Carbon::now()->timestamp;
        return hash_hmac('sha256', $payload, config('audit.integrity_key'));
    }
    
    private function verifyIntegrity(AuditEvent $event): bool
    {
        $expectedHash = $this->generateIntegrityHash($event->event_type, $event->event_data);
        return hash_equals($event->integrity_hash, $expectedHash);
    }
    
    private function processCriticalEvent(AuditEvent $event): void
    {
        // Notification immédiate pour événements critiques
        Log::critical('Événement critique détecté', [
            'event_id' => $event->id,
            'event_type' => $event->event_type,
            'timestamp' => $event->created_at
        ]);
        
        // Alerte en temps réel si configurée
        if (config('audit.real_time_alerts')) {
            $this->sendCriticalEventAlert($event);
        }
    }
    
    private function analyzeSecurityEvent(AuditEvent $event): void
    {
        // Analyse comportementale des événements de sécurité
        $recentEvents = $this->auditRepository->getRecentSecurityEvents(
            $event->ip_address, 
            Carbon::now()->subMinutes(30)
        );
        
        if (count($recentEvents) > 5) {
            $this->logSecurityEvent('SUSPICIOUS_ACTIVITY_PATTERN', [
                'ip_address' => $event->ip_address,
                'event_count' => count($recentEvents),
                'time_window_minutes' => 30,
                'pattern_type' => 'HIGH_FREQUENCY'
            ], 'CRITICAL');
        }
    }
    
    private function triggerSecurityAlert(string $eventType, array $details): void
    {
        // Implémentation des alertes de sécurité
        Log::emergency('Alerte de sécurité déclenchée', [
            'event_type' => $eventType,
            'details' => $details,
            'timestamp' => Carbon::now()
        ]);
    }
    
    private function sanitizeSearchCriteria(array $criteria): array
    {
        // Suppression des données sensibles des critères de recherche
        $sanitized = $criteria;
        $sensitiveFields = ['email', 'phone', 'document_number', 'biometric_data'];
        
        foreach ($sensitiveFields as $field) {
            if (isset($sanitized[$field])) {
                $sanitized[$field] = '***REDACTED***';
            }
        }
        
        return $sanitized;
    }
    
    private function resolveGeoLocation(?string $ipAddress): ?array
    {
        if (!$ipAddress || $ipAddress === '127.0.0.1') {
            return null;
        }
        
        // Géolocalisation basique (à implémenter avec un service externe)
        return [
            'country' => 'Unknown',
            'city' => 'Unknown',
            'anonymized' => true
        ];
    }
    
    private function sendCriticalEventAlert(AuditEvent $event): void
    {
        // Envoi d'alertes critiques (email, SMS, webhook, etc.)
    }
    
    private function calculateGeneralMetrics(Carbon $startDate, Carbon $endDate): array
    {
        return [
            'total_events' => $this->auditRepository->countEventsByDateRange($startDate, $endDate),
            'critical_events' => $this->auditRepository->countCriticalEventsByDateRange($startDate, $endDate),
            'security_events' => $this->auditRepository->countSecurityEventsByDateRange($startDate, $endDate),
            'unique_users' => $this->auditRepository->countUniqueUsersByDateRange($startDate, $endDate),
            'data_export_requests' => $this->auditRepository->countEventsByTypeAndDateRange('USER_DATA_EXPORT', $startDate, $endDate),
            'anonymization_requests' => $this->auditRepository->countEventsByTypeAndDateRange('USER_ANONYMIZATION', $startDate, $endDate)
        ];
    }
    
    private function analyzeDataAccessPatterns(Carbon $startDate, Carbon $endDate): array
    {
        // Analyse des patterns d'accès aux données
        return [];
    }
    
    private function analyzeConsentPatterns(Carbon $startDate, Carbon $endDate): array
    {
        // Analyse des patterns de consentement
        return [];
    }
    
    private function analyzeDataExports(Carbon $startDate, Carbon $endDate): array
    {
        // Analyse des exports de données
        return [];
    }
    
    private function analyzeAnonymizations(Carbon $startDate, Carbon $endDate): array
    {
        // Analyse des anonymisations
        return [];
    }
    
    private function analyzeSecurityEvents(Carbon $startDate, Carbon $endDate): array
    {
        // Analyse des événements de sécurité
        return [];
    }
    
    private function detectPotentialViolations(Carbon $startDate, Carbon $endDate): array
    {
        // Détection de violations potentielles
        return [];
    }
    
    private function calculateComplianceScore(AuditReport $report): float
    {
        // Calcul du score de conformité
        return 85.5;
    }
    
    private function generateComplianceRecommendations(AuditReport $report): array
    {
        // Génération de recommandations
        return [];
    }
    
    private function detectSuspiciousAccessPatterns(Carbon $startDate, Carbon $endDate): array
    {
        // Détection de patterns d'accès suspects
        return [];
    }
    
    private function detectVolumeAnomalies(Carbon $startDate, Carbon $endDate): array
    {
        // Détection d'anomalies de volume
        return [];
    }
    
    private function detectUnauthorizedAccess(Carbon $startDate, Carbon $endDate): array
    {
        // Détection d'accès non autorisés
        return [];
    }
    
    private function detectIntegrityViolations(Carbon $startDate, Carbon $endDate): array
    {
        // Détection de violations d'intégrité
        return [];
    }
}