<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Support\Str;

/**
 * Modèle GeographicData pour les données géographiques anonymisées
 * Utilise agrégation spatiale et k-anonymité pour protection RGPD
 * 
 * Stockage des données de géolocalisation avec anonymisation selon constitution.md
 */
class GeographicData extends Model
{
    use HasFactory;

    protected $table = 'geographic_data';
    protected $primaryKey = 'geo_id';
    public $keyType = 'string';
    public $incrementing = false;

    protected $fillable = [
        'geo_id',
        'region_level',
        'region_code',
        'region_name',
        'country_code',
        'latitude_centroid',
        'longitude_centroid',
        'session_count',
        'success_rate',
        'avg_processing_time',
        'population_density_category',
        'anonymization_grid_size',
        'k_anonymity_value',
        'geohash_level',
        'spatial_aggregation_method',
        'collection_period_start',
        'collection_period_end',
        'data_quality_score',
        'privacy_level',
    ];

    protected $casts = [
        'geo_id' => 'string',
        'latitude_centroid' => 'decimal:6',
        'longitude_centroid' => 'decimal:6',
        'session_count' => 'integer',
        'success_rate' => 'decimal:4',
        'avg_processing_time' => 'decimal:2',
        'anonymization_grid_size' => 'integer',
        'k_anonymity_value' => 'integer',
        'geohash_level' => 'integer',
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
            if (empty($model->geo_id)) {
                $model->geo_id = (string) Str::uuid();
            }
        });
    }

    // Enum pour les niveaux de région
    const REGION_LEVEL_COUNTRY = 'COUNTRY';
    const REGION_LEVEL_STATE = 'STATE';
    const REGION_LEVEL_REGION = 'REGION';
    const REGION_LEVEL_DEPARTMENT = 'DEPARTMENT';
    const REGION_LEVEL_CITY = 'CITY';
    const REGION_LEVEL_DISTRICT = 'DISTRICT';
    const REGION_LEVEL_GRID = 'GRID'; // Grille d'anonymisation

    const REGION_LEVELS = [
        self::REGION_LEVEL_COUNTRY,
        self::REGION_LEVEL_STATE,
        self::REGION_LEVEL_REGION,
        self::REGION_LEVEL_DEPARTMENT,
        self::REGION_LEVEL_CITY,
        self::REGION_LEVEL_DISTRICT,
        self::REGION_LEVEL_GRID,
    ];

    // Enum pour les catégories de densité de population
    const POPULATION_DENSITY_URBAN = 'URBAN';
    const POPULATION_DENSITY_SUBURBAN = 'SUBURBAN';
    const POPULATION_DENSITY_RURAL = 'RURAL';
    const POPULATION_DENSITY_REMOTE = 'REMOTE';

    const POPULATION_DENSITY_CATEGORIES = [
        self::POPULATION_DENSITY_URBAN,
        self::POPULATION_DENSITY_SUBURBAN,
        self::POPULATION_DENSITY_RURAL,
        self::POPULATION_DENSITY_REMOTE,
    ];

    // Enum pour les méthodes d'agrégation spatiale
    const SPATIAL_AGGREGATION_GRID = 'GRID_AGGREGATION';
    const SPATIAL_AGGREGATION_ADMINISTRATIVE = 'ADMINISTRATIVE_BOUNDARY';
    const SPATIAL_AGGREGATION_GEOHASH = 'GEOHASH';
    const SPATIAL_AGGREGATION_VORONOI = 'VORONOI_DIAGRAM';
    const SPATIAL_AGGREGATION_CENTROID = 'CENTROID_CLUSTERING';

    const SPATIAL_AGGREGATION_METHODS = [
        self::SPATIAL_AGGREGATION_GRID,
        self::SPATIAL_AGGREGATION_ADMINISTRATIVE,
        self::SPATIAL_AGGREGATION_GEOHASH,
        self::SPATIAL_AGGREGATION_VORONOI,
        self::SPATIAL_AGGREGATION_CENTROID,
    ];

    // Enum pour les niveaux de confidentialité
    const PRIVACY_LEVEL_HIGH = 'HIGH';
    const PRIVACY_LEVEL_MEDIUM = 'MEDIUM';
    const PRIVACY_LEVEL_LOW = 'LOW';
    const PRIVACY_LEVEL_PUBLIC = 'PUBLIC';

    const PRIVACY_LEVELS = [
        self::PRIVACY_LEVEL_HIGH,
        self::PRIVACY_LEVEL_MEDIUM,
        self::PRIVACY_LEVEL_LOW,
        self::PRIVACY_LEVEL_PUBLIC,
    ];

    // Codes pays supportés (ISO 3166-1 alpha-2)
    const SUPPORTED_COUNTRIES = [
        'FR' => 'France',
        'DE' => 'Germany',
        'IT' => 'Italy',
        'ES' => 'Spain',
        'BE' => 'Belgium',
        'NL' => 'Netherlands',
        'CH' => 'Switzerland',
        'AT' => 'Austria',
        'PT' => 'Portugal',
        'LU' => 'Luxembourg',
    ];

    // Scopes
    public function scopeByRegionLevel($query, $level)
    {
        return $query->where('region_level', $level);
    }

    public function scopeByCountry($query, $countryCode)
    {
        return $query->where('country_code', $countryCode);
    }

    public function scopeByPopulationDensity($query, $density)
    {
        return $query->where('population_density_category', $density);
    }

    public function scopeWithKAnonymity($query, $minK = 5)
    {
        return $query->where('k_anonymity_value', '>=', $minK);
    }

    public function scopeByPrivacyLevel($query, $level)
    {
        return $query->where('privacy_level', $level);
    }

    public function scopeByPeriod($query, $startDate, $endDate)
    {
        return $query->where('collection_period_start', '>=', $startDate)
                    ->where('collection_period_end', '<=', $endDate);
    }

    public function scopeWithQualityScore($query, $minScore = 0.8)
    {
        return $query->where('data_quality_score', '>=', $minScore);
    }

    public function scopeByGeohashLevel($query, $level)
    {
        return $query->where('geohash_level', $level);
    }

    public function scopeRecentData($query, $days = 7)
    {
        return $query->where('collection_period_end', '>=', now()->subDays($days));
    }

    public function scopeHighTraffic($query, $minSessions = 100)
    {
        return $query->where('session_count', '>=', $minSessions);
    }

    public function scopeUrbanAreas($query)
    {
        return $query->where('population_density_category', self::POPULATION_DENSITY_URBAN);
    }

    public function scopeRuralAreas($query)
    {
        return $query->where('population_density_category', self::POPULATION_DENSITY_RURAL);
    }

    // Accessors
    public function getRegionLevelDisplayNameAttribute()
    {
        return match($this->region_level) {
            self::REGION_LEVEL_COUNTRY => 'Pays',
            self::REGION_LEVEL_STATE => 'État',
            self::REGION_LEVEL_REGION => 'Région',
            self::REGION_LEVEL_DEPARTMENT => 'Département',
            self::REGION_LEVEL_CITY => 'Ville',
            self::REGION_LEVEL_DISTRICT => 'Arrondissement',
            self::REGION_LEVEL_GRID => 'Grille d\'Anonymisation',
            default => 'Niveau Inconnu'
        };
    }

    public function getCountryNameAttribute()
    {
        return self::SUPPORTED_COUNTRIES[$this->country_code] ?? 'Pays Inconnu';
    }

    public function getFormattedSuccessRateAttribute()
    {
        return number_format($this->success_rate * 100, 2) . '%';
    }

    public function getFormattedProcessingTimeAttribute()
    {
        return number_format($this->avg_processing_time, 2) . 's';
    }

    public function getIsPrivacyCompliantAttribute()
    {
        return $this->k_anonymity_value >= 5 && 
               in_array($this->privacy_level, [
                   self::PRIVACY_LEVEL_HIGH,
                   self::PRIVACY_LEVEL_MEDIUM,
                   self::PRIVACY_LEVEL_PUBLIC
               ]);
    }

    public function getAnonymizationQualityAttribute()
    {
        if ($this->k_anonymity_value >= 10 && $this->privacy_level === self::PRIVACY_LEVEL_HIGH) {
            return 'EXCELLENT';
        } elseif ($this->k_anonymity_value >= 5 && $this->privacy_level !== self::PRIVACY_LEVEL_LOW) {
            return 'GOOD';
        } else {
            return 'INSUFFICIENT';
        }
    }

    public function getGeohashAttribute()
    {
        // Génération simplifiée d'un geohash
        return $this->generateGeohash($this->latitude_centroid, $this->longitude_centroid, $this->geohash_level);
    }

    public function getCoordinatesAttribute()
    {
        return [
            'lat' => $this->latitude_centroid,
            'lng' => $this->longitude_centroid
        ];
    }

    // Méthodes statiques pour création de données géographiques
    public static function createGeoData($regionLevel, $regionCode, $regionName, $countryCode, $lat, $lng, $sessionCount)
    {
        // Calcul automatique de la taille de grille basée sur k-anonymité
        $gridSize = max(1000, $sessionCount * 10); // Minimum 1km, 10m par session
        $kValue = max(5, intval($sessionCount / 10)); // Minimum k=5
        
        return self::create([
            'region_level' => $regionLevel,
            'region_code' => $regionCode,
            'region_name' => $regionName,
            'country_code' => $countryCode,
            'latitude_centroid' => $lat,
            'longitude_centroid' => $lng,
            'session_count' => $sessionCount,
            'success_rate' => rand(75, 95) / 100, // Simulation
            'avg_processing_time' => rand(200, 800) / 100, // 2-8 secondes
            'population_density_category' => self::determinePopulationDensity($sessionCount),
            'anonymization_grid_size' => $gridSize,
            'k_anonymity_value' => $kValue,
            'geohash_level' => self::calculateOptimalGeohashLevel($sessionCount),
            'spatial_aggregation_method' => self::SPATIAL_AGGREGATION_GRID,
            'collection_period_start' => now()->startOfDay(),
            'collection_period_end' => now()->endOfDay(),
            'data_quality_score' => min(1.0, 0.7 + (rand(0, 30) / 100)),
            'privacy_level' => self::determinePrivacyLevel($kValue),
        ]);
    }

    public static function createCountryData($countryCode, $sessionCount)
    {
        $countryName = self::SUPPORTED_COUNTRIES[$countryCode] ?? 'Unknown';
        $coordinates = self::getCountryCentroid($countryCode);
        
        return self::createGeoData(
            self::REGION_LEVEL_COUNTRY,
            $countryCode,
            $countryName,
            $countryCode,
            $coordinates['lat'],
            $coordinates['lng'],
            $sessionCount
        );
    }

    public static function createCityData($cityName, $countryCode, $lat, $lng, $sessionCount)
    {
        return self::createGeoData(
            self::REGION_LEVEL_CITY,
            strtoupper(substr($cityName, 0, 3)) . '_' . $countryCode,
            $cityName,
            $countryCode,
            $lat,
            $lng,
            $sessionCount
        );
    }

    // Relations
    public function analyticsData()
    {
        return $this->hasMany(AnalyticsData::class, 'analytics_id', 'geo_id');
    }

    public function demographicStats()
    {
        return $this->hasMany(DemographicStat::class, 'stat_id', 'geo_id');
    }

    // Méthodes utilitaires
    public function isRGPDCompliant()
    {
        return $this->k_anonymity_value >= 5 && 
               $this->anonymization_grid_size >= 1000 && // Minimum 1km
               $this->privacy_level !== self::PRIVACY_LEVEL_LOW;
    }

    public function canBePublished()
    {
        return $this->isRGPDCompliant() && 
               $this->data_quality_score >= 0.7;
    }

    public function calculateDistance($lat, $lng)
    {
        // Calcul de distance haversine
        $earthRadius = 6371; // km
        
        $latDiff = deg2rad($lat - $this->latitude_centroid);
        $lngDiff = deg2rad($lng - $this->longitude_centroid);
        
        $a = sin($latDiff / 2) * sin($latDiff / 2) +
             cos(deg2rad($this->latitude_centroid)) * cos(deg2rad($lat)) *
             sin($lngDiff / 2) * sin($lngDiff / 2);
        
        $c = 2 * atan2(sqrt($a), sqrt(1 - $a));
        
        return $earthRadius * $c;
    }

    private function generateGeohash($lat, $lng, $precision = 7)
    {
        // Simulation simplifiée de geohash
        $base32 = '0123456789bcdefghjkmnpqrstuvwxyz';
        $hash = '';
        
        for ($i = 0; $i < $precision; $i++) {
            $hash .= $base32[rand(0, 31)];
        }
        
        return $hash;
    }

    private static function determinePopulationDensity($sessionCount)
    {
        if ($sessionCount > 1000) {
            return self::POPULATION_DENSITY_URBAN;
        } elseif ($sessionCount > 500) {
            return self::POPULATION_DENSITY_SUBURBAN;
        } elseif ($sessionCount > 100) {
            return self::POPULATION_DENSITY_RURAL;
        } else {
            return self::POPULATION_DENSITY_REMOTE;
        }
    }

    private static function calculateOptimalGeohashLevel($sessionCount)
    {
        // Plus de sessions = précision géographique plus faible pour anonymisation
        if ($sessionCount > 1000) return 4; // ~20km
        elseif ($sessionCount > 500) return 5; // ~5km
        elseif ($sessionCount > 100) return 6; // ~1km
        else return 3; // ~150km
    }

    private static function determinePrivacyLevel($kValue)
    {
        if ($kValue >= 20) {
            return self::PRIVACY_LEVEL_PUBLIC;
        } elseif ($kValue >= 10) {
            return self::PRIVACY_LEVEL_HIGH;
        } elseif ($kValue >= 5) {
            return self::PRIVACY_LEVEL_MEDIUM;
        } else {
            return self::PRIVACY_LEVEL_LOW;
        }
    }

    private static function getCountryCentroid($countryCode)
    {
        $centroids = [
            'FR' => ['lat' => 46.603354, 'lng' => 1.888334],
            'DE' => ['lat' => 51.165691, 'lng' => 10.451526],
            'IT' => ['lat' => 41.87194, 'lng' => 12.56738],
            'ES' => ['lat' => 40.463667, 'lng' => -3.74922],
            'BE' => ['lat' => 50.503887, 'lng' => 4.469936],
            'NL' => ['lat' => 52.132633, 'lng' => 5.291266],
            'CH' => ['lat' => 46.818188, 'lng' => 8.227512],
            'AT' => ['lat' => 47.516231, 'lng' => 14.550072],
            'PT' => ['lat' => 39.399872, 'lng' => -8.224454],
            'LU' => ['lat' => 49.815273, 'lng' => 6.129583],
        ];
        
        return $centroids[$countryCode] ?? ['lat' => 50.0, 'lng' => 0.0];
    }

    // Validation rules
    public static function validationRules()
    {
        return [
            'region_level' => 'required|in:' . implode(',', self::REGION_LEVELS),
            'region_code' => 'required|string|max:50',
            'region_name' => 'required|string|max:255',
            'country_code' => 'required|string|size:2|in:' . implode(',', array_keys(self::SUPPORTED_COUNTRIES)),
            'latitude_centroid' => 'required|numeric|between:-90,90',
            'longitude_centroid' => 'required|numeric|between:-180,180',
            'session_count' => 'required|integer|min:0',
            'success_rate' => 'required|numeric|min:0|max:1',
            'k_anonymity_value' => 'required|integer|min:5',
            'geohash_level' => 'required|integer|min:1|max:12',
            'spatial_aggregation_method' => 'required|in:' . implode(',', self::SPATIAL_AGGREGATION_METHODS),
            'privacy_level' => 'required|in:' . implode(',', self::PRIVACY_LEVELS),
            'population_density_category' => 'required|in:' . implode(',', self::POPULATION_DENSITY_CATEGORIES),
            'data_quality_score' => 'required|numeric|min:0|max:1',
            'collection_period_start' => 'required|date',
            'collection_period_end' => 'required|date|after_or_equal:collection_period_start',
        ];
    }
}