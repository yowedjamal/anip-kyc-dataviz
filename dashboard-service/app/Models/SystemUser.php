<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\SoftDeletes;
use Illuminate\Support\Str;

/**
 * Modèle SystemUser pour les utilisateurs du système dashboard
 * 
 * Champs chiffrés selon constitution.md:
 * - email (email utilisateur)
 * - metadata (informations sensibles utilisateur)
 * - preferences (préférences personnalisées)
 */
class SystemUser extends Model
{
    use HasFactory, SoftDeletes;

    protected $table = 'system_users';
    protected $primaryKey = 'user_id';
    public $keyType = 'string';
    public $incrementing = false;

    protected $fillable = [
        'user_id',
        'username',
        'email',
        'role',
        'department',
        'metadata',
        'preferences',
        'last_login_at',
        'is_active',
    ];

    protected $hidden = [
        'email',      // Chiffré
        'metadata',   // Chiffré
        'preferences' // Chiffré
    ];

    protected $casts = [
        'user_id' => 'string',
        'metadata' => 'array',
        'preferences' => 'array',
        'last_login_at' => 'datetime',
        'is_active' => 'boolean',
        'email_verified_at' => 'datetime',
        'created_at' => 'datetime',
        'updated_at' => 'datetime',
        'deleted_at' => 'datetime',
    ];

    protected $dates = [
        'last_login_at',
        'email_verified_at',
        'created_at',
        'updated_at',
        'deleted_at',
    ];

    // Boot method pour générer automatiquement l'UUID
    protected static function boot()
    {
        parent::boot();
        
        static::creating(function ($model) {
            if (empty($model->user_id)) {
                $model->user_id = (string) Str::uuid();
            }
        });
    }

    // Enum pour les rôles
    const ROLE_AGENT_KYC = 'AGENT_KYC';
    const ROLE_ANALYST_DATA = 'ANALYST_DATA';
    const ROLE_ADMIN_SYSTEM = 'ADMIN_SYSTEM';
    const ROLE_SUPERVISOR = 'SUPERVISOR';
    const ROLE_AUDITOR = 'AUDITOR';

    const ROLES = [
        self::ROLE_AGENT_KYC,
        self::ROLE_ANALYST_DATA,
        self::ROLE_ADMIN_SYSTEM,
        self::ROLE_SUPERVISOR,
        self::ROLE_AUDITOR,
    ];

    // Enum pour les départements
    const DEPARTMENT_KYC = 'KYC_VERIFICATION';
    const DEPARTMENT_ANALYTICS = 'DATA_ANALYTICS';
    const DEPARTMENT_COMPLIANCE = 'COMPLIANCE';
    const DEPARTMENT_IT = 'IT_ADMINISTRATION';
    const DEPARTMENT_MANAGEMENT = 'MANAGEMENT';

    const DEPARTMENTS = [
        self::DEPARTMENT_KYC,
        self::DEPARTMENT_ANALYTICS,
        self::DEPARTMENT_COMPLIANCE,
        self::DEPARTMENT_IT,
        self::DEPARTMENT_MANAGEMENT,
    ];

    // Relations
    public function auditEvents()
    {
        return $this->hasMany(AuditEvent::class, 'user_id', 'user_id');
    }

    // Scopes
    public function scopeActive($query)
    {
        return $query->where('is_active', true);
    }

    public function scopeByRole($query, $role)
    {
        return $query->where('role', $role);
    }

    public function scopeByDepartment($query, $department)
    {
        return $query->where('department', $department);
    }

    public function scopeRecentlyActive($query, $days = 30)
    {
        return $query->where('last_login_at', '>=', now()->subDays($days));
    }

    // Accessors
    public function getFullNameAttribute()
    {
        return $this->metadata['full_name'] ?? $this->username;
    }

    public function getIsOnlineAttribute()
    {
        return $this->last_login_at && 
               $this->last_login_at->diffInMinutes(now()) <= 30;
    }

    public function getRoleDisplayNameAttribute()
    {
        return match($this->role) {
            self::ROLE_AGENT_KYC => 'Agent KYC',
            self::ROLE_ANALYST_DATA => 'Analyste de Données',
            self::ROLE_ADMIN_SYSTEM => 'Administrateur Système',
            self::ROLE_SUPERVISOR => 'Superviseur',
            self::ROLE_AUDITOR => 'Auditeur',
            default => 'Rôle Inconnu'
        };
    }

    public function getDepartmentDisplayNameAttribute()
    {
        return match($this->department) {
            self::DEPARTMENT_KYC => 'Vérification KYC',
            self::DEPARTMENT_ANALYTICS => 'Analyse de Données',
            self::DEPARTMENT_COMPLIANCE => 'Conformité',
            self::DEPARTMENT_IT => 'Administration IT',
            self::DEPARTMENT_MANAGEMENT => 'Direction',
            default => 'Département Inconnu'
        };
    }

    // Mutators
    public function setEmailAttribute($value)
    {
        // Le chiffrement sera géré par les accessors/mutators personnalisés
        $this->attributes['email'] = encrypt($value);
    }

    public function setMetadataAttribute($value)
    {
        $this->attributes['metadata'] = encrypt(json_encode($value));
    }

    public function setPreferencesAttribute($value)
    {
        $this->attributes['preferences'] = encrypt(json_encode($value));
    }

    // Accessors pour les champs chiffrés
    public function getEmailAttribute($value)
    {
        return $value ? decrypt($value) : null;
    }

    public function getMetadataAttribute($value)
    {
        if (!$value) return [];
        
        try {
            return json_decode(decrypt($value), true) ?? [];
        } catch (\Exception $e) {
            return [];
        }
    }

    public function getPreferencesAttribute($value)
    {
        if (!$value) return [];
        
        try {
            return json_decode(decrypt($value), true) ?? [];
        } catch (\Exception $e) {
            return [];
        }
    }

    // Méthodes utilitaires
    public function hasRole($role)
    {
        return $this->role === $role;
    }

    public function hasAnyRole(array $roles)
    {
        return in_array($this->role, $roles);
    }

    public function belongsToDepartment($department)
    {
        return $this->department === $department;
    }

    public function canAccessAnalytics()
    {
        return $this->hasAnyRole([
            self::ROLE_ANALYST_DATA,
            self::ROLE_ADMIN_SYSTEM,
            self::ROLE_SUPERVISOR
        ]);
    }

    public function canAccessKycData()
    {
        return $this->hasAnyRole([
            self::ROLE_AGENT_KYC,
            self::ROLE_SUPERVISOR,
            self::ROLE_ADMIN_SYSTEM
        ]);
    }

    public function canManageUsers()
    {
        return $this->hasAnyRole([
            self::ROLE_ADMIN_SYSTEM,
            self::ROLE_SUPERVISOR
        ]);
    }

    public function canViewAuditLogs()
    {
        return $this->hasAnyRole([
            self::ROLE_AUDITOR,
            self::ROLE_ADMIN_SYSTEM,
            self::ROLE_SUPERVISOR
        ]);
    }

    public function updateLastLogin()
    {
        $this->update(['last_login_at' => now()]);
    }

    public function activate()
    {
        $this->update(['is_active' => true]);
    }

    public function deactivate()
    {
        $this->update(['is_active' => false]);
    }

    public function updatePreferences(array $preferences)
    {
        $currentPreferences = $this->preferences;
        $this->preferences = array_merge($currentPreferences, $preferences);
        $this->save();
    }

    // Validation rules
    public static function validationRules()
    {
        return [
            'username' => 'required|string|max:255|unique:system_users',
            'email' => 'required|email|max:255|unique:system_users',
            'role' => 'required|in:' . implode(',', self::ROLES),
            'department' => 'required|in:' . implode(',', self::DEPARTMENTS),
            'is_active' => 'boolean',
        ];
    }

    public static function updateValidationRules($userId)
    {
        return [
            'username' => 'required|string|max:255|unique:system_users,username,' . $userId . ',user_id',
            'email' => 'required|email|max:255|unique:system_users,email,' . $userId . ',user_id',
            'role' => 'required|in:' . implode(',', self::ROLES),
            'department' => 'required|in:' . implode(',', self::DEPARTMENTS),
            'is_active' => 'boolean',
        ];
    }
}