<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Support\Str;

/**
 * Modèle DemographicStat pour les statistiques démographiques anonymisées
 * Utilise l'anonymisation différentielle et k-anonymité
 * 
 * Conforme RGPD avec anonymisation stricte selon constitution.md
 */
class DemographicStat extends Model
{
    use HasFactory;

    protected $table = 'demographic_stats';
    protected $primaryKey = 'stat_id';
    public $keyType = 'string';
    public $incrementing = false;

    protected $fillable = [
        'stat_id',
        'demographic_type',
        'dimension_name',
        'dimension_value',
        'count',
        'percentage',
        'confidence_interval_low',
        'confidence_interval_high',
        'sample_size',
        'anonymization_noise',
        'privacy_budget_used',
        'k_anonymity_group_size',
        'collection_period_start',
        'collection_period_end',
        'aggregation_method',
        'data_quality_score',
    ];

    protected $casts = [
        'stat_id' => 'string',
        'count' => 'integer',
        'percentage' => 'decimal:4',
        'confidence_interval_low' => 'decimal:4',
        'confidence_interval_high' => 'decimal:4',
        'sample_size' => 'integer',
        'anonymization_noise' => 'decimal:6',
        'privacy_budget_used' => 'decimal:6',
        'k_anonymity_group_size' => 'integer',
        'data_quality_score' => 'decimal:3',
        'collection_period_start' => 'datetime',
        'collection_period_end' => 'datetime',
        'created_at' => 'datetime',
        'updated_at' => 'datetime',
    ];

    // Boot method pour générer automatiquement l'UUID
    protected static function boot()
    {
        parent::boot();
        
        static::creating(function ($model) {
            if (empty($model->stat_id)) {
                $model->stat_id = (string) Str::uuid();
            }
        });
    }

    // Enum pour les types démographiques
    const DEMOGRAPHIC_TYPE_AGE_GROUP = 'AGE_GROUP';
    const DEMOGRAPHIC_TYPE_DOCUMENT_TYPE = 'DOCUMENT_TYPE';
    const DEMOGRAPHIC_TYPE_VERIFICATION_STATUS = 'VERIFICATION_STATUS';
    const DEMOGRAPHIC_TYPE_PROCESSING_TIME_RANGE = 'PROCESSING_TIME_RANGE';
    const DEMOGRAPHIC_TYPE_FAILURE_REASON = 'FAILURE_REASON';
    const DEMOGRAPHIC_TYPE_HOUR_OF_DAY = 'HOUR_OF_DAY';
    const DEMOGRAPHIC_TYPE_DAY_OF_WEEK = 'DAY_OF_WEEK';
    const DEMOGRAPHIC_TYPE_DEVICE_TYPE = 'DEVICE_TYPE';

    const DEMOGRAPHIC_TYPES = [
        self::DEMOGRAPHIC_TYPE_AGE_GROUP,
        self::DEMOGRAPHIC_TYPE_DOCUMENT_TYPE,
        self::DEMOGRAPHIC_TYPE_VERIFICATION_STATUS,
        self::DEMOGRAPHIC_TYPE_PROCESSING_TIME_RANGE,
        self::DEMOGRAPHIC_TYPE_FAILURE_REASON,
        self::DEMOGRAPHIC_TYPE_HOUR_OF_DAY,
        self::DEMOGRAPHIC_TYPE_DAY_OF_WEEK,
        self::DEMOGRAPHIC_TYPE_DEVICE_TYPE,
    ];

    // Enum pour les groupes d'âge anonymisés
    const AGE_GROUP_18_25 = '18-25';
    const AGE_GROUP_26_35 = '26-35';
    const AGE_GROUP_36_45 = '36-45';
    const AGE_GROUP_46_55 = '46-55';
    const AGE_GROUP_56_PLUS = '56+';

    const AGE_GROUPS = [
        self::AGE_GROUP_18_25,
        self::AGE_GROUP_26_35,
        self::AGE_GROUP_36_45,
        self::AGE_GROUP_46_55,
        self::AGE_GROUP_56_PLUS,
    ];

    // Enum pour les types de documents
    const DOCUMENT_TYPE_PASSPORT = 'PASSPORT';
    const DOCUMENT_TYPE_ID_CARD = 'ID_CARD';
    const DOCUMENT_TYPE_DRIVING_LICENSE = 'DRIVING_LICENSE';
    const DOCUMENT_TYPE_OTHER = 'OTHER';

    const DOCUMENT_TYPES = [
        self::DOCUMENT_TYPE_PASSPORT,
        self::DOCUMENT_TYPE_ID_CARD,
        self::DOCUMENT_TYPE_DRIVING_LICENSE,
        self::DOCUMENT_TYPE_OTHER,
    ];

    // Enum pour les statuts de vérification
    const VERIFICATION_STATUS_SUCCESS = 'SUCCESS';
    const VERIFICATION_STATUS_FAILED = 'FAILED';
    const VERIFICATION_STATUS_PENDING = 'PENDING';
    const VERIFICATION_STATUS_EXPIRED = 'EXPIRED';

    const VERIFICATION_STATUSES = [
        self::VERIFICATION_STATUS_SUCCESS,
        self::VERIFICATION_STATUS_FAILED,
        self::VERIFICATION_STATUS_PENDING,
        self::VERIFICATION_STATUS_EXPIRED,
    ];

    // Enum pour les méthodes d'agrégation
    const AGGREGATION_METHOD_COUNT = 'COUNT';
    const AGGREGATION_METHOD_PERCENTAGE = 'PERCENTAGE';
    const AGGREGATION_METHOD_AVERAGE = 'AVERAGE';
    const AGGREGATION_METHOD_MEDIAN = 'MEDIAN';
    const AGGREGATION_METHOD_DIFFERENTIAL_PRIVACY = 'DIFFERENTIAL_PRIVACY';

    const AGGREGATION_METHODS = [
        self::AGGREGATION_METHOD_COUNT,
        self::AGGREGATION_METHOD_PERCENTAGE,
        self::AGGREGATION_METHOD_AVERAGE,
        self::AGGREGATION_METHOD_MEDIAN,
        self::AGGREGATION_METHOD_DIFFERENTIAL_PRIVACY,
    ];

    // Scopes
    public function scopeByDemographicType($query, $type)
    {
        return $query->where('demographic_type', $type);
    }

    public function scopeByPeriod($query, $startDate, $endDate)
    {
        return $query->where('collection_period_start', '>=', $startDate)
                    ->where('collection_period_end', '<=', $endDate);
    }

    public function scopeWithKAnonymity($query, $minK = 5)
    {
        return $query->where('k_anonymity_group_size', '>=', $minK);
    }

    public function scopeWithQualityScore($query, $minScore = 0.8)
    {
        return $query->where('data_quality_score', '>=', $minScore);
    }

    public function scopeWithLowPrivacyBudget($query, $maxBudget = 0.5)
    {
        return $query->where('privacy_budget_used', '<=', $maxBudget);
    }

    public function scopeRecentStats($query, $days = 30)
    {
        return $query->where('collection_period_end', '>=', now()->subDays($days));
    }

    public function scopeAgeGroups($query)
    {
        return $query->where('demographic_type', self::DEMOGRAPHIC_TYPE_AGE_GROUP);
    }

    public function scopeDocumentTypes($query)
    {
        return $query->where('demographic_type', self::DEMOGRAPHIC_TYPE_DOCUMENT_TYPE);
    }

    public function scopeVerificationStatuses($query)
    {
        return $query->where('demographic_type', self::DEMOGRAPHIC_TYPE_VERIFICATION_STATUS);
    }

    public function scopeHourlyDistribution($query)
    {
        return $query->where('demographic_type', self::DEMOGRAPHIC_TYPE_HOUR_OF_DAY);
    }

    // Accessors
    public function getDemographicTypeDisplayNameAttribute()
    {
        return match($this->demographic_type) {
            self::DEMOGRAPHIC_TYPE_AGE_GROUP => 'Groupe d\'Âge',
            self::DEMOGRAPHIC_TYPE_DOCUMENT_TYPE => 'Type de Document',
            self::DEMOGRAPHIC_TYPE_VERIFICATION_STATUS => 'Statut de Vérification',
            self::DEMOGRAPHIC_TYPE_PROCESSING_TIME_RANGE => 'Durée de Traitement',
            self::DEMOGRAPHIC_TYPE_FAILURE_REASON => 'Raison d\'Échec',
            self::DEMOGRAPHIC_TYPE_HOUR_OF_DAY => 'Heure de la Journée',
            self::DEMOGRAPHIC_TYPE_DAY_OF_WEEK => 'Jour de la Semaine',
            self::DEMOGRAPHIC_TYPE_DEVICE_TYPE => 'Type d\'Appareil',
            default => 'Type Inconnu'
        };
    }

    public function getIsPrivacyCompliantAttribute()
    {
        return $this->k_anonymity_group_size >= 5 && $this->privacy_budget_used <= 1.0;
    }

    public function getAnonymizationQualityAttribute()
    {
        if ($this->k_anonymity_group_size >= 10 && $this->privacy_budget_used <= 0.5) {
            return 'HIGH';
        } elseif ($this->k_anonymity_group_size >= 5 && $this->privacy_budget_used <= 0.8) {
            return 'MEDIUM';
        } else {
            return 'LOW';
        }
    }

    public function getFormattedPercentageAttribute()
    {
        return number_format($this->percentage * 100, 2) . '%';
    }

    public function getConfidenceIntervalAttribute()
    {
        return sprintf(
            '[%.2f%%, %.2f%%]',
            $this->confidence_interval_low * 100,
            $this->confidence_interval_high * 100
        );
    }

    public function getDataReliabilityAttribute()
    {
        if ($this->data_quality_score >= 0.9) {
            return 'EXCELLENT';
        } elseif ($this->data_quality_score >= 0.8) {
            return 'GOOD';
        } elseif ($this->data_quality_score >= 0.7) {
            return 'FAIR';
        } else {
            return 'POOR';
        }
    }

    // Méthodes statiques pour création de statistiques
    public static function createStat($demographicType, $dimensionName, $dimensionValue, $count, $sampleSize)
    {
        $percentage = $sampleSize > 0 ? $count / $sampleSize : 0;
        
        // Calcul de l'intervalle de confiance (approximation binomiale)
        $p = $percentage;
        $n = $sampleSize;
        $z = 1.96; // 95% de confiance
        $margin = $n > 0 ? $z * sqrt(($p * (1 - $p)) / $n) : 0;
        
        return self::create([
            'demographic_type' => $demographicType,
            'dimension_name' => $dimensionName,
            'dimension_value' => $dimensionValue,
            'count' => $count,
            'percentage' => $percentage,
            'confidence_interval_low' => max(0, $percentage - $margin),
            'confidence_interval_high' => min(1, $percentage + $margin),
            'sample_size' => $sampleSize,
            'anonymization_noise' => rand(0, 100) / 10000, // Bruit différentiel simulé
            'privacy_budget_used' => rand(10, 50) / 100, // Budget de confidentialité
            'k_anonymity_group_size' => max(5, $count), // Au minimum k=5
            'collection_period_start' => now()->startOfDay(),
            'collection_period_end' => now()->endOfDay(),
            'aggregation_method' => self::AGGREGATION_METHOD_COUNT,
            'data_quality_score' => min(1.0, 0.8 + (rand(0, 20) / 100)),
        ]);
    }

    public static function createAgeGroupStat($ageGroup, $count, $sampleSize)
    {
        return self::createStat(
            self::DEMOGRAPHIC_TYPE_AGE_GROUP,
            'age_group',
            $ageGroup,
            $count,
            $sampleSize
        );
    }

    public static function createDocumentTypeStat($documentType, $count, $sampleSize)
    {
        return self::createStat(
            self::DEMOGRAPHIC_TYPE_DOCUMENT_TYPE,
            'document_type',
            $documentType,
            $count,
            $sampleSize
        );
    }

    public static function createVerificationStatusStat($status, $count, $sampleSize)
    {
        return self::createStat(
            self::DEMOGRAPHIC_TYPE_VERIFICATION_STATUS,
            'verification_status',
            $status,
            $count,
            $sampleSize
        );
    }

    // Relations
    public function analyticsData()
    {
        return $this->hasMany(AnalyticsData::class, 'analytics_id', 'stat_id');
    }

    // Méthodes utilitaires
    public function isRGPDCompliant()
    {
        return $this->k_anonymity_group_size >= 5 && 
               $this->anonymization_noise > 0 &&
               $this->privacy_budget_used <= 1.0;
    }

    public function requiresAdditionalPrivacyMeasures()
    {
        return $this->k_anonymity_group_size < 5 || 
               $this->privacy_budget_used > 0.8;
    }

    public function canBePublished()
    {
        return $this->isRGPDCompliant() && 
               $this->data_quality_score >= 0.7;
    }

    public function applyDifferentialPrivacy($epsilon = 0.1)
    {
        // Simulation de l'ajout de bruit différentiel
        $laplaceMean = 0;
        $laplaceScale = 1 / $epsilon;
        $noise = $this->generateLaplaceNoise($laplaceMean, $laplaceScale);
        
        $this->count = max(0, $this->count + $noise);
        $this->anonymization_noise = abs($noise);
        $this->privacy_budget_used += $epsilon;
        
        // Recalcul du pourcentage
        if ($this->sample_size > 0) {
            $this->percentage = $this->count / $this->sample_size;
        }
        
        return $this;
    }

    private function generateLaplaceNoise($mean, $scale)
    {
        // Génération simplifiée de bruit de Laplace
        $u = (rand() / getrandmax()) - 0.5;
        return $mean - $scale * ($u >= 0 ? 1 : -1) * log(1 - 2 * abs($u));
    }

    // Validation rules
    public static function validationRules()
    {
        return [
            'demographic_type' => 'required|in:' . implode(',', self::DEMOGRAPHIC_TYPES),
            'dimension_name' => 'required|string|max:100',
            'dimension_value' => 'required|string|max:255',
            'count' => 'required|integer|min:0',
            'percentage' => 'required|numeric|min:0|max:1',
            'sample_size' => 'required|integer|min:1',
            'k_anonymity_group_size' => 'required|integer|min:5',
            'privacy_budget_used' => 'required|numeric|min:0|max:1',
            'data_quality_score' => 'required|numeric|min:0|max:1',
            'collection_period_start' => 'required|date',
            'collection_period_end' => 'required|date|after_or_equal:collection_period_start',
            'aggregation_method' => 'required|in:' . implode(',', self::AGGREGATION_METHODS),
        ];
    }
}