<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Support\Str;

/**
 * Modèle AnalyticsData pour stockage des données analytiques anonymisées
 * Utilise TimescaleDB pour les séries temporelles
 * 
 * Champs anonymisés selon constitution.md avec k-anonymité (k≥5)
 */
class AnalyticsData extends Model
{
    use HasFactory;

    protected $table = 'analytics_data';
    protected $primaryKey = 'analytics_id';
    public $keyType = 'string';
    public $incrementing = false;

    protected $fillable = [
        'analytics_id',
        'timestamp',
        'metric_type',
        'metric_value',
        'dimensions',
        'aggregation_level',
        'anonymization_method',
        'k_anonymity_value',
        'time_bucket',
    ];

    protected $casts = [
        'analytics_id' => 'string',
        'timestamp' => 'datetime',
        'metric_value' => 'decimal:4',
        'dimensions' => 'array',
        'k_anonymity_value' => 'integer',
        'created_at' => 'datetime',
        'updated_at' => 'datetime',
    ];

    // Boot method pour générer automatiquement l'UUID
    protected static function boot()
    {
        parent::boot();
        
        static::creating(function ($model) {
            if (empty($model->analytics_id)) {
                $model->analytics_id = (string) Str::uuid();
            }
        });
    }

    // Enum pour les types de métriques
    const METRIC_TYPE_SESSION_COUNT = 'SESSION_COUNT';
    const METRIC_TYPE_SUCCESS_RATE = 'SUCCESS_RATE';
    const METRIC_TYPE_PROCESSING_TIME = 'PROCESSING_TIME';
    const METRIC_TYPE_DOCUMENT_TYPE_DISTRIBUTION = 'DOCUMENT_TYPE_DISTRIBUTION';
    const METRIC_TYPE_FAILURE_RATE = 'FAILURE_RATE';
    const METRIC_TYPE_GEOGRAPHIC_DISTRIBUTION = 'GEOGRAPHIC_DISTRIBUTION';
    const METRIC_TYPE_HOURLY_VOLUME = 'HOURLY_VOLUME';
    const METRIC_TYPE_USER_DEMOGRAPHICS = 'USER_DEMOGRAPHICS';

    const METRIC_TYPES = [
        self::METRIC_TYPE_SESSION_COUNT,
        self::METRIC_TYPE_SUCCESS_RATE,
        self::METRIC_TYPE_PROCESSING_TIME,
        self::METRIC_TYPE_DOCUMENT_TYPE_DISTRIBUTION,
        self::METRIC_TYPE_FAILURE_RATE,
        self::METRIC_TYPE_GEOGRAPHIC_DISTRIBUTION,
        self::METRIC_TYPE_HOURLY_VOLUME,
        self::METRIC_TYPE_USER_DEMOGRAPHICS,
    ];

    // Enum pour les niveaux d'agrégation
    const AGGREGATION_LEVEL_MINUTE = 'MINUTE';
    const AGGREGATION_LEVEL_HOUR = 'HOUR';
    const AGGREGATION_LEVEL_DAY = 'DAY';
    const AGGREGATION_LEVEL_WEEK = 'WEEK';
    const AGGREGATION_LEVEL_MONTH = 'MONTH';

    const AGGREGATION_LEVELS = [
        self::AGGREGATION_LEVEL_MINUTE,
        self::AGGREGATION_LEVEL_HOUR,
        self::AGGREGATION_LEVEL_DAY,
        self::AGGREGATION_LEVEL_WEEK,
        self::AGGREGATION_LEVEL_MONTH,
    ];

    // Enum pour les méthodes d'anonymisation
    const ANONYMIZATION_K_ANONYMITY = 'K_ANONYMITY';
    const ANONYMIZATION_DIFFERENTIAL_PRIVACY = 'DIFFERENTIAL_PRIVACY';
    const ANONYMIZATION_SUPPRESSION = 'SUPPRESSION';
    const ANONYMIZATION_GENERALIZATION = 'GENERALIZATION';

    const ANONYMIZATION_METHODS = [
        self::ANONYMIZATION_K_ANONYMITY,
        self::ANONYMIZATION_DIFFERENTIAL_PRIVACY,
        self::ANONYMIZATION_SUPPRESSION,
        self::ANONYMIZATION_GENERALIZATION,
    ];

    // Enum pour les buckets temporels
    const TIME_BUCKET_1MIN = '1_MINUTE';
    const TIME_BUCKET_5MIN = '5_MINUTES';
    const TIME_BUCKET_15MIN = '15_MINUTES';
    const TIME_BUCKET_1HOUR = '1_HOUR';
    const TIME_BUCKET_1DAY = '1_DAY';

    const TIME_BUCKETS = [
        self::TIME_BUCKET_1MIN,
        self::TIME_BUCKET_5MIN,
        self::TIME_BUCKET_15MIN,
        self::TIME_BUCKET_1HOUR,
        self::TIME_BUCKET_1DAY,
    ];

    // Scopes
    public function scopeByMetricType($query, $metricType)
    {
        return $query->where('metric_type', $metricType);
    }

    public function scopeByTimeRange($query, $startTime, $endTime)
    {
        return $query->whereBetween('timestamp', [$startTime, $endTime]);
    }

    public function scopeByAggregationLevel($query, $level)
    {
        return $query->where('aggregation_level', $level);
    }

    public function scopeByTimeBucket($query, $bucket)
    {
        return $query->where('time_bucket', $bucket);
    }

    public function scopeWithKAnonymity($query, $minK = 5)
    {
        return $query->where('k_anonymity_value', '>=', $minK);
    }

    public function scopeToday($query)
    {
        return $query->whereDate('timestamp', today());
    }

    public function scopeLastDays($query, $days = 7)
    {
        return $query->where('timestamp', '>=', now()->subDays($days));
    }

    public function scopeLastHours($query, $hours = 24)
    {
        return $query->where('timestamp', '>=', now()->subHours($hours));
    }

    public function scopeHourlyData($query)
    {
        return $query->where('aggregation_level', self::AGGREGATION_LEVEL_HOUR);
    }

    public function scopeDailyData($query)
    {
        return $query->where('aggregation_level', self::AGGREGATION_LEVEL_DAY);
    }

    // Accessors
    public function getMetricTypeDisplayNameAttribute()
    {
        return match($this->metric_type) {
            self::METRIC_TYPE_SESSION_COUNT => 'Nombre de Sessions',
            self::METRIC_TYPE_SUCCESS_RATE => 'Taux de Succès',
            self::METRIC_TYPE_PROCESSING_TIME => 'Temps de Traitement',
            self::METRIC_TYPE_DOCUMENT_TYPE_DISTRIBUTION => 'Distribution des Types de Documents',
            self::METRIC_TYPE_FAILURE_RATE => 'Taux d\'Échec',
            self::METRIC_TYPE_GEOGRAPHIC_DISTRIBUTION => 'Distribution Géographique',
            self::METRIC_TYPE_HOURLY_VOLUME => 'Volume Horaire',
            self::METRIC_TYPE_USER_DEMOGRAPHICS => 'Démographie Utilisateurs',
            default => 'Métrique Inconnue'
        };
    }

    public function getIsAnonymizedAttribute()
    {
        return $this->k_anonymity_value >= 5;
    }

    public function getAnonymizationQualityAttribute()
    {
        if ($this->k_anonymity_value >= 10) {
            return 'HIGH';
        } elseif ($this->k_anonymity_value >= 5) {
            return 'MEDIUM';
        } else {
            return 'LOW';
        }
    }

    public function getFormattedValueAttribute()
    {
        return match($this->metric_type) {
            self::METRIC_TYPE_SUCCESS_RATE, self::METRIC_TYPE_FAILURE_RATE => 
                number_format($this->metric_value * 100, 2) . '%',
            self::METRIC_TYPE_PROCESSING_TIME => 
                number_format($this->metric_value, 2) . 's',
            default => number_format($this->metric_value, 2)
        };
    }

    // Méthodes statiques pour création de données analytiques
    public static function createMetric($metricType, $value, $dimensions = [], $aggregationLevel = self::AGGREGATION_LEVEL_HOUR)
    {
        return self::create([
            'metric_type' => $metricType,
            'metric_value' => $value,
            'dimensions' => $dimensions,
            'aggregation_level' => $aggregationLevel,
            'anonymization_method' => self::ANONYMIZATION_K_ANONYMITY,
            'k_anonymity_value' => 5, // Valeur par défaut conforme RGPD
            'timestamp' => now(),
            'time_bucket' => self::TIME_BUCKET_1HOUR,
        ]);
    }

    public static function aggregateHourlySessionCount($timestamp, $count, $dimensions = [])
    {
        return self::createMetric(
            self::METRIC_TYPE_SESSION_COUNT,
            $count,
            $dimensions,
            self::AGGREGATION_LEVEL_HOUR
        );
    }

    public static function aggregateSuccessRate($timestamp, $rate, $dimensions = [])
    {
        return self::createMetric(
            self::METRIC_TYPE_SUCCESS_RATE,
            $rate,
            $dimensions,
            self::AGGREGATION_LEVEL_HOUR
        );
    }

    public static function aggregateProcessingTime($timestamp, $avgTime, $dimensions = [])
    {
        return self::createMetric(
            self::METRIC_TYPE_PROCESSING_TIME,
            $avgTime,
            $dimensions,
            self::AGGREGATION_LEVEL_HOUR
        );
    }

    // Méthodes utilitaires
    public function isCompliantWithRGPD()
    {
        return $this->k_anonymity_value >= 5;
    }

    public function requiresAdditionalAnonymization()
    {
        return $this->k_anonymity_value < 5;
    }

    public function canBePublished()
    {
        return $this->isCompliantWithRGPD() && 
               $this->anonymization_method !== null;
    }

    // Validation rules
    public static function validationRules()
    {
        return [
            'metric_type' => 'required|in:' . implode(',', self::METRIC_TYPES),
            'metric_value' => 'required|numeric|min:0',
            'dimensions' => 'nullable|array',
            'aggregation_level' => 'required|in:' . implode(',', self::AGGREGATION_LEVELS),
            'anonymization_method' => 'required|in:' . implode(',', self::ANONYMIZATION_METHODS),
            'k_anonymity_value' => 'required|integer|min:1',
            'time_bucket' => 'required|in:' . implode(',', self::TIME_BUCKETS),
            'timestamp' => 'required|date',
        ];
    }
}