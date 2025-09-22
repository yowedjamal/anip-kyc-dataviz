<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;
use Illuminate\Support\Facades\DB;

/**
 * Migration pour les tables du service Dashboard
 * Utilise TimescaleDB pour les données de séries temporelles
 * Chiffrement AES-256 pour conformité RGPD selon constitution.md
 */
return new class extends Migration
{
    /**
     * Run the migrations.
     */
    public function up(): void
    {
        // Activation de l'extension TimescaleDB
        DB::statement('CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;');
        DB::statement('CREATE EXTENSION IF NOT EXISTS "uuid-ossp";');
        DB::statement('CREATE EXTENSION IF NOT EXISTS pgcrypto;');

        // Schema pour le dashboard
        DB::statement('CREATE SCHEMA IF NOT EXISTS dashboard;');
        DB::statement('SET search_path TO dashboard, public;');

        // Table: system_users
        // Gestion des utilisateurs avec données chiffrées
        Schema::create('system_users', function (Blueprint $table) {
            $table->uuid('user_id')->primary()->default(DB::raw('uuid_generate_v4()'));
            $table->string('username', 100)->unique();
            $table->binary('email_encrypted'); // Email chiffré AES-256
            $table->string('password_hash'); // Hash bcrypt
            $table->enum('role', ['ADMIN', 'ANALYST', 'VIEWER', 'AUDITOR'])->default('VIEWER');
            $table->enum('status', ['ACTIVE', 'INACTIVE', 'SUSPENDED', 'PENDING'])->default('PENDING');
            $table->binary('personal_data_encrypted')->nullable(); // Données personnelles chiffrées (JSON)
            $table->timestamp('last_login_at')->nullable();
            $table->binary('last_login_ip_encrypted')->nullable(); // IP dernière connexion chiffrée
            $table->json('permissions')->nullable(); // Permissions spécifiques
            $table->json('preferences')->nullable(); // Préférences utilisateur
            $table->timestamp('password_changed_at')->nullable();
            $table->boolean('must_change_password')->default(false);
            $table->integer('failed_login_attempts')->default(0);
            $table->timestamp('locked_until')->nullable();
            $table->timestamps();
            
            // Index
            $table->index(['username']);
            $table->index(['role']);
            $table->index(['status']);
            $table->index(['last_login_at']);
        });

        // Table: audit_events
        // Journal d'audit avec données anonymisées
        Schema::create('audit_events', function (Blueprint $table) {
            $table->uuid('event_id')->primary()->default(DB::raw('uuid_generate_v4()'));
            $table->uuid('user_id')->nullable(); // Référence chiffrée
            $table->binary('user_id_encrypted')->nullable(); // ID utilisateur chiffré
            $table->string('event_type', 100); // LOGIN, LOGOUT, ACCESS_DENIED, DATA_EXPORT, etc.
            $table->string('resource_type', 100)->nullable(); // TABLE, REPORT, DASHBOARD, etc.
            $table->string('resource_id', 255)->nullable();
            $table->enum('severity', ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'])->default('LOW');
            $table->binary('event_data_encrypted')->nullable(); // Données événement chiffrées
            $table->binary('ip_address_encrypted')->nullable(); // IP chiffrée
            $table->string('user_agent_hash', 64)->nullable(); // Hash SHA-256 du User-Agent
            $table->enum('result', ['SUCCESS', 'FAILED', 'WARNING'])->default('SUCCESS');
            $table->text('failure_reason')->nullable();
            $table->json('metadata')->nullable(); // Métadonnées non sensibles
            $table->timestamp('occurred_at')->default(DB::raw('CURRENT_TIMESTAMP'));
            $table->timestamps();
            
            // Index pour recherche
            $table->index(['event_type']);
            $table->index(['severity']);
            $table->index(['result']);
            $table->index(['occurred_at']);
            $table->index(['user_id']);
        });

        // Conversion en hypertable TimescaleDB pour audit_events
        DB::statement("SELECT create_hypertable('audit_events', 'occurred_at', if_not_exists => TRUE);");

        // Table: analytics_data
        // Données analytiques anonymisées pour dashboard
        Schema::create('analytics_data', function (Blueprint $table) {
            $table->uuid('analytics_id')->primary()->default(DB::raw('uuid_generate_v4()'));
            $table->timestamp('timestamp')->default(DB::raw('CURRENT_TIMESTAMP'));
            $table->enum('metric_type', [
                'SESSION_COUNT',
                'SUCCESS_RATE', 
                'PROCESSING_TIME',
                'DOCUMENT_TYPE_DISTRIBUTION',
                'FAILURE_RATE',
                'GEOGRAPHIC_DISTRIBUTION',
                'HOURLY_VOLUME',
                'USER_DEMOGRAPHICS'
            ]);
            $table->decimal('metric_value', 15, 4);
            $table->json('dimensions')->nullable(); // Dimensions d'analyse
            $table->enum('aggregation_level', ['MINUTE', 'HOUR', 'DAY', 'WEEK', 'MONTH']);
            $table->enum('anonymization_method', [
                'K_ANONYMITY',
                'DIFFERENTIAL_PRIVACY', 
                'SUPPRESSION',
                'GENERALIZATION'
            ]);
            $table->integer('k_anonymity_value')->default(5); // Minimum k=5 RGPD
            $table->enum('time_bucket', ['1_MINUTE', '5_MINUTES', '15_MINUTES', '1_HOUR', '1_DAY']);
            $table->timestamps();
            
            // Index pour recherche temporelle
            $table->index(['timestamp']);
            $table->index(['metric_type']);
            $table->index(['aggregation_level']);
            $table->index(['k_anonymity_value']);
        });

        // Conversion en hypertable TimescaleDB pour analytics_data
        DB::statement("SELECT create_hypertable('analytics_data', 'timestamp', if_not_exists => TRUE);");

        // Table: demographic_stats
        // Statistiques démographiques avec confidentialité différentielle
        Schema::create('demographic_stats', function (Blueprint $table) {
            $table->uuid('stat_id')->primary()->default(DB::raw('uuid_generate_v4()'));
            $table->enum('demographic_type', [
                'AGE_GROUP',
                'DOCUMENT_TYPE',
                'VERIFICATION_STATUS',
                'PROCESSING_TIME_RANGE',
                'FAILURE_REASON',
                'HOUR_OF_DAY',
                'DAY_OF_WEEK',
                'DEVICE_TYPE'
            ]);
            $table->string('dimension_name', 100);
            $table->string('dimension_value', 255);
            $table->integer('count');
            $table->decimal('percentage', 8, 4);
            $table->decimal('confidence_interval_low', 8, 4);
            $table->decimal('confidence_interval_high', 8, 4);
            $table->integer('sample_size');
            $table->decimal('anonymization_noise', 10, 6)->default(0); // Bruit différentiel
            $table->decimal('privacy_budget_used', 8, 6)->default(0); // Budget de confidentialité
            $table->integer('k_anonymity_group_size')->default(5);
            $table->timestamp('collection_period_start');
            $table->timestamp('collection_period_end');
            $table->enum('aggregation_method', [
                'COUNT',
                'PERCENTAGE',
                'AVERAGE',
                'MEDIAN',
                'DIFFERENTIAL_PRIVACY'
            ]);
            $table->decimal('data_quality_score', 5, 3)->default(1.0);
            $table->timestamps();
            
            // Index
            $table->index(['demographic_type']);
            $table->index(['collection_period_start']);
            $table->index(['collection_period_end']);
            $table->index(['k_anonymity_group_size']);
        });

        // Table: geographic_data
        // Données géographiques avec anonymisation spatiale
        Schema::create('geographic_data', function (Blueprint $table) {
            $table->uuid('geo_id')->primary()->default(DB::raw('uuid_generate_v4()'));
            $table->enum('region_level', [
                'COUNTRY',
                'STATE', 
                'REGION',
                'DEPARTMENT',
                'CITY',
                'DISTRICT',
                'GRID'
            ]);
            $table->string('region_code', 50);
            $table->string('region_name', 255);
            $table->char('country_code', 2); // ISO 3166-1 alpha-2
            $table->decimal('latitude_centroid', 10, 6); // Centroïde anonymisé
            $table->decimal('longitude_centroid', 10, 6); // Centroïde anonymisé
            $table->integer('session_count')->default(0);
            $table->decimal('success_rate', 8, 4)->default(0);
            $table->decimal('avg_processing_time', 8, 2)->default(0);
            $table->enum('population_density_category', ['URBAN', 'SUBURBAN', 'RURAL', 'REMOTE']);
            $table->integer('anonymization_grid_size')->default(1000); // Taille grille en mètres
            $table->integer('k_anonymity_value')->default(5);
            $table->integer('geohash_level')->default(5); // Précision geohash
            $table->enum('spatial_aggregation_method', [
                'GRID_AGGREGATION',
                'ADMINISTRATIVE_BOUNDARY',
                'GEOHASH',
                'VORONOI_DIAGRAM',
                'CENTROID_CLUSTERING'
            ]);
            $table->timestamp('collection_period_start');
            $table->timestamp('collection_period_end');
            $table->decimal('data_quality_score', 5, 3)->default(1.0);
            $table->enum('privacy_level', ['HIGH', 'MEDIUM', 'LOW', 'PUBLIC']);
            $table->timestamps();
            
            // Index géospatiaux
            $table->index(['country_code']);
            $table->index(['region_level']);
            $table->index(['population_density_category']);
            $table->index(['k_anonymity_value']);
            $table->index(['collection_period_start']);
        });

        // Table: encryption_keys
        // Gestion centralisée des clés de chiffrement
        Schema::create('encryption_keys', function (Blueprint $table) {
            $table->uuid('id')->primary()->default(DB::raw('uuid_generate_v4()'));
            $table->string('key_name', 100)->unique();
            $table->text('encryption_key'); // Clé chiffrée par clé maître
            $table->string('algorithm', 50)->default('AES-256-CBC');
            $table->boolean('is_active')->default(true);
            $table->timestamp('created_at')->default(DB::raw('CURRENT_TIMESTAMP'));
            $table->timestamp('expires_at')->nullable();
            $table->timestamp('last_used_at')->nullable();
            $table->integer('usage_count')->default(0);
            
            $table->index(['key_name']);
            $table->index(['is_active']);
            $table->index(['expires_at']);
        });

        // Relations de clés étrangères
        Schema::table('audit_events', function (Blueprint $table) {
            $table->foreign('user_id')->references('user_id')->on('system_users')->nullOnDelete();
        });

        // Politiques Row Level Security
        DB::statement('ALTER TABLE system_users ENABLE ROW LEVEL SECURITY;');
        DB::statement('ALTER TABLE audit_events ENABLE ROW LEVEL SECURITY;');
        DB::statement('ALTER TABLE analytics_data ENABLE ROW LEVEL SECURITY;');

        // Vues matérialisées pour performances
        DB::statement('
            CREATE MATERIALIZED VIEW hourly_analytics AS
            SELECT 
                date_trunc(\'hour\', timestamp) as hour_bucket,
                metric_type,
                AVG(metric_value) as avg_value,
                COUNT(*) as data_points,
                MIN(metric_value) as min_value,
                MAX(metric_value) as max_value
            FROM analytics_data 
            WHERE timestamp >= CURRENT_TIMESTAMP - INTERVAL \'7 days\'
            AND k_anonymity_value >= 5
            GROUP BY date_trunc(\'hour\', timestamp), metric_type;
        ');

        DB::statement('
            CREATE MATERIALIZED VIEW daily_geographic_summary AS
            SELECT 
                date_trunc(\'day\', collection_period_start) as day_bucket,
                country_code,
                region_level,
                SUM(session_count) as total_sessions,
                AVG(success_rate) as avg_success_rate,
                COUNT(*) as region_count
            FROM geographic_data 
            WHERE collection_period_start >= CURRENT_TIMESTAMP - INTERVAL \'30 days\'
            AND k_anonymity_value >= 5
            GROUP BY date_trunc(\'day\', collection_period_start), country_code, region_level;
        ');

        // Index sur les vues matérialisées
        DB::statement('CREATE INDEX idx_hourly_analytics_hour ON hourly_analytics(hour_bucket);');
        DB::statement('CREATE INDEX idx_daily_geographic_day ON daily_geographic_summary(day_bucket);');

        // Politiques de rétention des données TimescaleDB
        DB::statement("
            SELECT add_retention_policy('analytics_data', INTERVAL '1 year');
        ");

        DB::statement("
            SELECT add_retention_policy('audit_events', INTERVAL '7 years');
        ");

        // Compression automatique des données anciennes
        DB::statement("
            SELECT add_compression_policy('analytics_data', INTERVAL '30 days');
        ");

        DB::statement("
            SELECT add_compression_policy('audit_events', INTERVAL '90 days');
        ");

        // Fonctions pour chiffrement/déchiffrement
        DB::statement('
            CREATE OR REPLACE FUNCTION encrypt_personal_data(data TEXT, key_name TEXT DEFAULT \'default\')
            RETURNS BYTEA AS $$
            DECLARE
                encryption_key TEXT;
            BEGIN
                SELECT ek.encryption_key INTO encryption_key 
                FROM encryption_keys ek 
                WHERE ek.key_name = $2 AND ek.is_active = true;
                
                IF encryption_key IS NULL THEN
                    RAISE EXCEPTION \'Encryption key not found: %\', key_name;
                END IF;
                
                -- Mise à jour du compteur d\'utilisation
                UPDATE encryption_keys 
                SET usage_count = usage_count + 1, last_used_at = CURRENT_TIMESTAMP
                WHERE key_name = $2;
                
                RETURN pgp_sym_encrypt(data, encryption_key);
            END;
            $$ LANGUAGE plpgsql SECURITY DEFINER;
        ');

        DB::statement('
            CREATE OR REPLACE FUNCTION decrypt_personal_data(encrypted_data BYTEA, key_name TEXT DEFAULT \'default\')
            RETURNS TEXT AS $$
            DECLARE
                encryption_key TEXT;
            BEGIN
                SELECT ek.encryption_key INTO encryption_key 
                FROM encryption_keys ek 
                WHERE ek.key_name = $2 AND ek.is_active = true;
                
                IF encryption_key IS NULL THEN
                    RAISE EXCEPTION \'Encryption key not found: %\', key_name;
                END IF;
                
                RETURN pgp_sym_decrypt(encrypted_data, encryption_key);
            END;
            $$ LANGUAGE plpgsql SECURITY DEFINER;
        ');

        // Trigger pour audit automatique
        DB::statement('
            CREATE OR REPLACE FUNCTION audit_user_access()
            RETURNS TRIGGER AS $$
            BEGIN
                INSERT INTO audit_events (
                    user_id,
                    event_type,
                    resource_type,
                    resource_id,
                    severity,
                    occurred_at
                ) VALUES (
                    NEW.user_id,
                    TG_OP,
                    TG_TABLE_NAME,
                    NEW.user_id::TEXT,
                    \'LOW\',
                    CURRENT_TIMESTAMP
                );
                RETURN NEW;
            END;
            $$ LANGUAGE plpgsql;
        ');

        DB::statement('
            CREATE TRIGGER audit_system_users_access
            AFTER INSERT OR UPDATE ON system_users
            FOR EACH ROW EXECUTE FUNCTION audit_user_access();
        ');

        // Rôles et permissions
        DB::statement('CREATE ROLE IF NOT EXISTS dashboard_application_role;');
        DB::statement('CREATE ROLE IF NOT EXISTS dashboard_readonly_role;');
        DB::statement('CREATE ROLE IF NOT EXISTS dashboard_analyst_role;');

        // Permissions application
        DB::statement('GRANT USAGE ON SCHEMA dashboard TO dashboard_application_role;');
        DB::statement('GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA dashboard TO dashboard_application_role;');
        DB::statement('GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA dashboard TO dashboard_application_role;');
        DB::statement('GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA dashboard TO dashboard_application_role;');

        // Permissions lecture seule
        DB::statement('GRANT USAGE ON SCHEMA dashboard TO dashboard_readonly_role;');
        DB::statement('GRANT SELECT ON ALL TABLES IN SCHEMA dashboard TO dashboard_readonly_role;');

        // Permissions analyste
        DB::statement('GRANT USAGE ON SCHEMA dashboard TO dashboard_analyst_role;');
        DB::statement('GRANT SELECT ON ALL TABLES IN SCHEMA dashboard TO dashboard_analyst_role;');
        DB::statement('GRANT SELECT ON hourly_analytics TO dashboard_analyst_role;');
        DB::statement('GRANT SELECT ON daily_geographic_summary TO dashboard_analyst_role;');

        // Configuration par défaut
        DB::statement('SET search_path TO dashboard, public;');
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        // Suppression des vues matérialisées
        DB::statement('DROP MATERIALIZED VIEW IF EXISTS hourly_analytics;');
        DB::statement('DROP MATERIALIZED VIEW IF EXISTS daily_geographic_summary;');

        // Suppression des fonctions
        DB::statement('DROP FUNCTION IF EXISTS encrypt_personal_data(TEXT, TEXT);');
        DB::statement('DROP FUNCTION IF EXISTS decrypt_personal_data(BYTEA, TEXT);');
        DB::statement('DROP FUNCTION IF EXISTS audit_user_access();');

        // Suppression des tables
        Schema::dropIfExists('encryption_keys');
        Schema::dropIfExists('geographic_data');
        Schema::dropIfExists('demographic_stats');
        Schema::dropIfExists('analytics_data');
        Schema::dropIfExists('audit_events');
        Schema::dropIfExists('system_users');

        // Suppression des rôles
        DB::statement('DROP ROLE IF EXISTS dashboard_application_role;');
        DB::statement('DROP ROLE IF EXISTS dashboard_readonly_role;');
        DB::statement('DROP ROLE IF EXISTS dashboard_analyst_role;');

        // Suppression du schéma
        DB::statement('DROP SCHEMA IF EXISTS dashboard CASCADE;');
    }
};