<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Support\Str;

/**
 * Modèle AuditEvent pour traçabilité et audit de sécurité
 * 
 * Champs chiffrés selon constitution.md:
 * - user_id (identifiant utilisateur)
 * - event_data (données sensibles de l'événement)
 * - ip_address (adresse IP pour protection vie privée)
 */
class AuditEvent extends Model
{
    use HasFactory;

    protected $table = 'audit_events';
    protected $primaryKey = 'event_id';
    public $keyType = 'string';
    public $incrementing = false;

    protected $fillable = [
        'event_id',
        'user_id',
        'event_type',
        'resource_type',
        'resource_id',
        'action',
        'event_data',
        'ip_address',
        'user_agent',
        'status',
        'severity_level',
    ];

    protected $hidden = [
        'user_id',    // Chiffré
        'event_data', // Chiffré
        'ip_address', // Chiffré
    ];

    protected $casts = [
        'event_id' => 'string',
        'event_data' => 'array',
        'created_at' => 'datetime',
        'updated_at' => 'datetime',
    ];

    // Boot method pour générer automatiquement l'UUID
    protected static function boot()
    {
        parent::boot();
        
        static::creating(function ($model) {
            if (empty($model->event_id)) {
                $model->event_id = (string) Str::uuid();
            }
        });
    }

    // Enum pour les types d'événements
    const EVENT_TYPE_USER_LOGIN = 'USER_LOGIN';
    const EVENT_TYPE_USER_LOGOUT = 'USER_LOGOUT';
    const EVENT_TYPE_DATA_ACCESS = 'DATA_ACCESS';
    const EVENT_TYPE_DATA_EXPORT = 'DATA_EXPORT';
    const EVENT_TYPE_SYSTEM_CONFIG = 'SYSTEM_CONFIG';
    const EVENT_TYPE_SECURITY_ALERT = 'SECURITY_ALERT';
    const EVENT_TYPE_API_CALL = 'API_CALL';
    const EVENT_TYPE_REPORT_GENERATION = 'REPORT_GENERATION';

    const EVENT_TYPES = [
        self::EVENT_TYPE_USER_LOGIN,
        self::EVENT_TYPE_USER_LOGOUT,
        self::EVENT_TYPE_DATA_ACCESS,
        self::EVENT_TYPE_DATA_EXPORT,
        self::EVENT_TYPE_SYSTEM_CONFIG,
        self::EVENT_TYPE_SECURITY_ALERT,
        self::EVENT_TYPE_API_CALL,
        self::EVENT_TYPE_REPORT_GENERATION,
    ];

    // Enum pour les types de ressources
    const RESOURCE_TYPE_USER = 'USER';
    const RESOURCE_TYPE_SESSION = 'SESSION';
    const RESOURCE_TYPE_DOCUMENT = 'DOCUMENT';
    const RESOURCE_TYPE_ANALYTICS = 'ANALYTICS';
    const RESOURCE_TYPE_REPORT = 'REPORT';
    const RESOURCE_TYPE_SYSTEM = 'SYSTEM';

    const RESOURCE_TYPES = [
        self::RESOURCE_TYPE_USER,
        self::RESOURCE_TYPE_SESSION,
        self::RESOURCE_TYPE_DOCUMENT,
        self::RESOURCE_TYPE_ANALYTICS,
        self::RESOURCE_TYPE_REPORT,
        self::RESOURCE_TYPE_SYSTEM,
    ];

    // Enum pour les actions
    const ACTION_CREATE = 'CREATE';
    const ACTION_READ = 'READ';
    const ACTION_UPDATE = 'UPDATE';
    const ACTION_DELETE = 'DELETE';
    const ACTION_EXPORT = 'EXPORT';
    const ACTION_LOGIN = 'LOGIN';
    const ACTION_LOGOUT = 'LOGOUT';
    const ACTION_ACCESS_DENIED = 'ACCESS_DENIED';

    const ACTIONS = [
        self::ACTION_CREATE,
        self::ACTION_READ,
        self::ACTION_UPDATE,
        self::ACTION_DELETE,
        self::ACTION_EXPORT,
        self::ACTION_LOGIN,
        self::ACTION_LOGOUT,
        self::ACTION_ACCESS_DENIED,
    ];

    // Enum pour les statuts
    const STATUS_SUCCESS = 'SUCCESS';
    const STATUS_FAILED = 'FAILED';
    const STATUS_PENDING = 'PENDING';
    const STATUS_BLOCKED = 'BLOCKED';

    const STATUSES = [
        self::STATUS_SUCCESS,
        self::STATUS_FAILED,
        self::STATUS_PENDING,
        self::STATUS_BLOCKED,
    ];

    // Enum pour les niveaux de sévérité
    const SEVERITY_LOW = 'LOW';
    const SEVERITY_MEDIUM = 'MEDIUM';
    const SEVERITY_HIGH = 'HIGH';
    const SEVERITY_CRITICAL = 'CRITICAL';

    const SEVERITY_LEVELS = [
        self::SEVERITY_LOW,
        self::SEVERITY_MEDIUM,
        self::SEVERITY_HIGH,
        self::SEVERITY_CRITICAL,
    ];

    // Relations
    public function user()
    {
        return $this->belongsTo(SystemUser::class, 'user_id', 'user_id');
    }

    // Scopes
    public function scopeByEventType($query, $eventType)
    {
        return $query->where('event_type', $eventType);
    }

    public function scopeByUser($query, $userId)
    {
        return $query->where('user_id', $userId);
    }

    public function scopeByStatus($query, $status)
    {
        return $query->where('status', $status);
    }

    public function scopeBySeverity($query, $severity)
    {
        return $query->where('severity_level', $severity);
    }

    public function scopeToday($query)
    {
        return $query->whereDate('created_at', today());
    }

    public function scopeLastDays($query, $days = 7)
    {
        return $query->where('created_at', '>=', now()->subDays($days));
    }

    public function scopeSecurityEvents($query)
    {
        return $query->whereIn('event_type', [
            self::EVENT_TYPE_SECURITY_ALERT,
            self::EVENT_TYPE_USER_LOGIN,
            self::EVENT_TYPE_USER_LOGOUT,
        ]);
    }

    public function scopeDataAccessEvents($query)
    {
        return $query->whereIn('event_type', [
            self::EVENT_TYPE_DATA_ACCESS,
            self::EVENT_TYPE_DATA_EXPORT,
            self::EVENT_TYPE_REPORT_GENERATION,
        ]);
    }

    public function scopeFailedEvents($query)
    {
        return $query->where('status', self::STATUS_FAILED);
    }

    public function scopeHighPriorityEvents($query)
    {
        return $query->whereIn('severity_level', [
            self::SEVERITY_HIGH,
            self::SEVERITY_CRITICAL,
        ]);
    }

    // Accessors
    public function getEventTypeDisplayNameAttribute()
    {
        return match($this->event_type) {
            self::EVENT_TYPE_USER_LOGIN => 'Connexion Utilisateur',
            self::EVENT_TYPE_USER_LOGOUT => 'Déconnexion Utilisateur',
            self::EVENT_TYPE_DATA_ACCESS => 'Accès aux Données',
            self::EVENT_TYPE_DATA_EXPORT => 'Export de Données',
            self::EVENT_TYPE_SYSTEM_CONFIG => 'Configuration Système',
            self::EVENT_TYPE_SECURITY_ALERT => 'Alerte Sécurité',
            self::EVENT_TYPE_API_CALL => 'Appel API',
            self::EVENT_TYPE_REPORT_GENERATION => 'Génération Rapport',
            default => 'Événement Inconnu'
        };
    }

    public function getSeverityColorAttribute()
    {
        return match($this->severity_level) {
            self::SEVERITY_LOW => 'green',
            self::SEVERITY_MEDIUM => 'yellow',
            self::SEVERITY_HIGH => 'orange',
            self::SEVERITY_CRITICAL => 'red',
            default => 'gray'
        };
    }

    public function getIsSecurityEventAttribute()
    {
        return in_array($this->event_type, [
            self::EVENT_TYPE_SECURITY_ALERT,
            self::EVENT_TYPE_USER_LOGIN,
            self::EVENT_TYPE_USER_LOGOUT,
        ]);
    }

    public function getIsFailedAttribute()
    {
        return $this->status === self::STATUS_FAILED;
    }

    public function getIsHighPriorityAttribute()
    {
        return in_array($this->severity_level, [
            self::SEVERITY_HIGH,
            self::SEVERITY_CRITICAL,
        ]);
    }

    // Mutators pour chiffrement
    public function setUserIdAttribute($value)
    {
        $this->attributes['user_id'] = encrypt($value);
    }

    public function setEventDataAttribute($value)
    {
        $this->attributes['event_data'] = encrypt(json_encode($value));
    }

    public function setIpAddressAttribute($value)
    {
        $this->attributes['ip_address'] = encrypt($value);
    }

    // Accessors pour déchiffrement
    public function getUserIdAttribute($value)
    {
        return $value ? decrypt($value) : null;
    }

    public function getEventDataAttribute($value)
    {
        if (!$value) return [];
        
        try {
            return json_decode(decrypt($value), true) ?? [];
        } catch (\Exception $e) {
            return [];
        }
    }

    public function getIpAddressAttribute($value)
    {
        return $value ? decrypt($value) : null;
    }

    // Méthodes statiques pour logging
    public static function logUserLogin($userId, $ipAddress, $userAgent)
    {
        return self::create([
            'user_id' => $userId,
            'event_type' => self::EVENT_TYPE_USER_LOGIN,
            'resource_type' => self::RESOURCE_TYPE_USER,
            'resource_id' => $userId,
            'action' => self::ACTION_LOGIN,
            'ip_address' => $ipAddress,
            'user_agent' => $userAgent,
            'status' => self::STATUS_SUCCESS,
            'severity_level' => self::SEVERITY_LOW,
        ]);
    }

    public static function logDataAccess($userId, $resourceType, $resourceId, $ipAddress)
    {
        return self::create([
            'user_id' => $userId,
            'event_type' => self::EVENT_TYPE_DATA_ACCESS,
            'resource_type' => $resourceType,
            'resource_id' => $resourceId,
            'action' => self::ACTION_READ,
            'ip_address' => $ipAddress,
            'status' => self::STATUS_SUCCESS,
            'severity_level' => self::SEVERITY_LOW,
        ]);
    }

    public static function logSecurityAlert($userId, $alertData, $ipAddress, $severity = self::SEVERITY_HIGH)
    {
        return self::create([
            'user_id' => $userId,
            'event_type' => self::EVENT_TYPE_SECURITY_ALERT,
            'resource_type' => self::RESOURCE_TYPE_SYSTEM,
            'action' => self::ACTION_ACCESS_DENIED,
            'event_data' => $alertData,
            'ip_address' => $ipAddress,
            'status' => self::STATUS_FAILED,
            'severity_level' => $severity,
        ]);
    }

    public static function logDataExport($userId, $exportType, $recordCount, $ipAddress)
    {
        return self::create([
            'user_id' => $userId,
            'event_type' => self::EVENT_TYPE_DATA_EXPORT,
            'resource_type' => self::RESOURCE_TYPE_REPORT,
            'action' => self::ACTION_EXPORT,
            'event_data' => [
                'export_type' => $exportType,
                'record_count' => $recordCount,
            ],
            'ip_address' => $ipAddress,
            'status' => self::STATUS_SUCCESS,
            'severity_level' => self::SEVERITY_MEDIUM,
        ]);
    }

    // Validation rules
    public static function validationRules()
    {
        return [
            'user_id' => 'required|string',
            'event_type' => 'required|in:' . implode(',', self::EVENT_TYPES),
            'resource_type' => 'required|in:' . implode(',', self::RESOURCE_TYPES),
            'resource_id' => 'nullable|string',
            'action' => 'required|in:' . implode(',', self::ACTIONS),
            'status' => 'required|in:' . implode(',', self::STATUSES),
            'severity_level' => 'required|in:' . implode(',', self::SEVERITY_LEVELS),
            'ip_address' => 'required|ip',
            'user_agent' => 'nullable|string|max:500',
        ];
    }
}