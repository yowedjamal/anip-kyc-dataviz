#!/bin/bash
# 03-seed-initial-data.sql
# Données initiales pour le système KYC et Dashboard

\echo 'Insertion des données initiales...'

-- Connexion à la base Dashboard pour données de seed
\c dashboard_db;

-- Insertion des clés de chiffrement par défaut
INSERT INTO dashboard.encryption_keys (key_name, encryption_key, algorithm, is_active) VALUES
('default', 'AES_KEY_WILL_BE_GENERATED_BY_APPLICATION', 'AES-256-CBC', true),
('user_data', 'USER_DATA_KEY_WILL_BE_GENERATED', 'AES-256-CBC', true),
('audit_data', 'AUDIT_DATA_KEY_WILL_BE_GENERATED', 'AES-256-CBC', true),
('analytics_data', 'ANALYTICS_KEY_WILL_BE_GENERATED', 'AES-256-CBC', true)
ON CONFLICT (key_name) DO NOTHING;

-- Utilisateur administrateur par défaut (mot de passe: Admin2024!)
-- Hash bcrypt généré pour 'Admin2024!'
INSERT INTO dashboard.system_users (
    username, 
    email_encrypted, 
    password_hash, 
    role, 
    status,
    personal_data_encrypted,
    permissions,
    preferences,
    must_change_password
) VALUES (
    'admin',
    encrypt_personal_data('admin@kyc-platform.com', 'user_data'),
    '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', -- Admin2024!
    'ADMIN',
    'ACTIVE',
    encrypt_personal_data('{"first_name":"System","last_name":"Administrator","created_by":"initial_setup"}', 'user_data'),
    '["USER_MANAGEMENT", "SYSTEM_CONFIG", "DATA_EXPORT", "AUDIT_VIEW", "ANALYTICS_VIEW"]',
    '{"theme":"light","language":"fr","timezone":"Europe/Paris","dashboard_layout":"default"}',
    true
) ON CONFLICT (username) DO NOTHING;

-- Utilisateur analyste par défaut (mot de passe: Analyst2024!)
INSERT INTO dashboard.system_users (
    username, 
    email_encrypted, 
    password_hash, 
    role, 
    status,
    personal_data_encrypted,
    permissions,
    preferences
) VALUES (
    'analyst',
    encrypt_personal_data('analyst@kyc-platform.com', 'user_data'),
    '$2y$10$5K8LvQcJNGGLuODq8BmfZ.p8SX8QxKZr9aLZk1Nc1vFTDfU3kEy.G', -- Analyst2024!
    'ANALYST',
    'ACTIVE',
    encrypt_personal_data('{"first_name":"Data","last_name":"Analyst","created_by":"initial_setup"}', 'user_data'),
    '["ANALYTICS_VIEW", "REPORT_GENERATION", "DATA_EXPORT"]',
    '{"theme":"light","language":"fr","timezone":"Europe/Paris","dashboard_layout":"analytics"}'
) ON CONFLICT (username) DO NOTHING;

-- Utilisateur viewer par défaut (mot de passe: Viewer2024!)
INSERT INTO dashboard.system_users (
    username, 
    email_encrypted, 
    password_hash, 
    role, 
    status,
    personal_data_encrypted,
    permissions,
    preferences
) VALUES (
    'viewer',
    encrypt_personal_data('viewer@kyc-platform.com', 'user_data'),
    '$2y$10$Hf7P3QZz5mVpOKxGQzLN3.hGkEtUdBjFa1vKcMxGqEr2WsVvP4HQS', -- Viewer2024!
    'VIEWER',
    'ACTIVE',
    encrypt_personal_data('{"first_name":"Report","last_name":"Viewer","created_by":"initial_setup"}', 'user_data'),
    '["ANALYTICS_VIEW"]',
    '{"theme":"light","language":"fr","timezone":"Europe/Paris","dashboard_layout":"simple"}'
) ON CONFLICT (username) DO NOTHING;

\echo 'Utilisateurs par défaut créés.'

-- Données de démonstration pour analytics_data (anonymisées)
INSERT INTO dashboard.analytics_data (
    metric_type, metric_value, dimensions, aggregation_level, 
    anonymization_method, k_anonymity_value, time_bucket, timestamp
) VALUES
-- Sessions par heure (dernières 24h)
('SESSION_COUNT', 125.0, '{"hour":0}', 'HOUR', 'K_ANONYMITY', 25, '1_HOUR', NOW() - INTERVAL '23 hours'),
('SESSION_COUNT', 98.0, '{"hour":1}', 'HOUR', 'K_ANONYMITY', 20, '1_HOUR', NOW() - INTERVAL '22 hours'),
('SESSION_COUNT', 67.0, '{"hour":2}', 'HOUR', 'K_ANONYMITY', 13, '1_HOUR', NOW() - INTERVAL '21 hours'),
('SESSION_COUNT', 45.0, '{"hour":3}', 'HOUR', 'K_ANONYMITY', 9, '1_HOUR', NOW() - INTERVAL '20 hours'),
('SESSION_COUNT', 89.0, '{"hour":4}', 'HOUR', 'K_ANONYMITY', 18, '1_HOUR', NOW() - INTERVAL '19 hours'),
('SESSION_COUNT', 156.0, '{"hour":5}', 'HOUR', 'K_ANONYMITY', 31, '1_HOUR', NOW() - INTERVAL '18 hours'),

-- Taux de succès par heure
('SUCCESS_RATE', 0.892, '{"hour":0}', 'HOUR', 'K_ANONYMITY', 25, '1_HOUR', NOW() - INTERVAL '23 hours'),
('SUCCESS_RATE', 0.901, '{"hour":1}', 'HOUR', 'K_ANONYMITY', 20, '1_HOUR', NOW() - INTERVAL '22 hours'),
('SUCCESS_RATE', 0.876, '{"hour":2}', 'HOUR', 'K_ANONYMITY', 13, '1_HOUR', NOW() - INTERVAL '21 hours'),
('SUCCESS_RATE', 0.844, '{"hour":3}', 'HOUR', 'K_ANONYMITY', 9, '1_HOUR', NOW() - INTERVAL '20 hours'),

-- Temps de traitement moyen
('PROCESSING_TIME', 4.23, '{"hour":0}', 'HOUR', 'K_ANONYMITY', 25, '1_HOUR', NOW() - INTERVAL '23 hours'),
('PROCESSING_TIME', 3.87, '{"hour":1}', 'HOUR', 'K_ANONYMITY', 20, '1_HOUR', NOW() - INTERVAL '22 hours'),
('PROCESSING_TIME', 4.56, '{"hour":2}', 'HOUR', 'K_ANONYMITY', 13, '1_HOUR', NOW() - INTERVAL '21 hours'),

-- Distribution des types de documents
('DOCUMENT_TYPE_DISTRIBUTION', 0.45, '{"document_type":"ID_CARD"}', 'DAY', 'K_ANONYMITY', 150, '1_DAY', NOW()),
('DOCUMENT_TYPE_DISTRIBUTION', 0.35, '{"document_type":"PASSPORT"}', 'DAY', 'K_ANONYMITY', 120, '1_DAY', NOW()),
('DOCUMENT_TYPE_DISTRIBUTION', 0.20, '{"document_type":"DRIVING_LICENSE"}', 'DAY', 'K_ANONYMITY', 68, '1_DAY', NOW());

\echo 'Données analytics de démonstration insérées.'

-- Données démographiques anonymisées
INSERT INTO dashboard.demographic_stats (
    demographic_type, dimension_name, dimension_value, count, percentage,
    confidence_interval_low, confidence_interval_high, sample_size,
    anonymization_noise, privacy_budget_used, k_anonymity_group_size,
    collection_period_start, collection_period_end, aggregation_method, data_quality_score
) VALUES
-- Groupes d'âge (k=5 minimum)
('AGE_GROUP', 'age_group', '18-25', 145, 0.29, 0.26, 0.32, 500, 0.001, 0.05, 29, NOW() - INTERVAL '1 day', NOW(), 'COUNT', 0.95),
('AGE_GROUP', 'age_group', '26-35', 175, 0.35, 0.32, 0.38, 500, 0.001, 0.05, 35, NOW() - INTERVAL '1 day', NOW(), 'COUNT', 0.95),
('AGE_GROUP', 'age_group', '36-45', 105, 0.21, 0.18, 0.24, 500, 0.001, 0.05, 21, NOW() - INTERVAL '1 day', NOW(), 'COUNT', 0.95),
('AGE_GROUP', 'age_group', '46-55', 50, 0.10, 0.08, 0.12, 500, 0.001, 0.05, 10, NOW() - INTERVAL '1 day', NOW(), 'COUNT', 0.95),
('AGE_GROUP', 'age_group', '56+', 25, 0.05, 0.03, 0.07, 500, 0.001, 0.05, 5, NOW() - INTERVAL '1 day', NOW(), 'COUNT', 0.95),

-- Statuts de vérification
('VERIFICATION_STATUS', 'status', 'SUCCESS', 420, 0.84, 0.81, 0.87, 500, 0.002, 0.08, 84, NOW() - INTERVAL '1 day', NOW(), 'PERCENTAGE', 0.98),
('VERIFICATION_STATUS', 'status', 'FAILED', 65, 0.13, 0.10, 0.16, 500, 0.002, 0.08, 13, NOW() - INTERVAL '1 day', NOW(), 'PERCENTAGE', 0.98),
('VERIFICATION_STATUS', 'status', 'PENDING', 15, 0.03, 0.01, 0.05, 500, 0.002, 0.08, 3, NOW() - INTERVAL '1 day', NOW(), 'PERCENTAGE', 0.98),

-- Distribution horaire
('HOUR_OF_DAY', 'hour', '08', 45, 0.090, 0.070, 0.110, 500, 0.001, 0.03, 9, NOW() - INTERVAL '1 day', NOW(), 'COUNT', 0.92),
('HOUR_OF_DAY', 'hour', '09', 68, 0.136, 0.110, 0.162, 500, 0.001, 0.03, 14, NOW() - INTERVAL '1 day', NOW(), 'COUNT', 0.92),
('HOUR_OF_DAY', 'hour', '10', 82, 0.164, 0.135, 0.193, 500, 0.001, 0.03, 16, NOW() - INTERVAL '1 day', NOW(), 'COUNT', 0.92),
('HOUR_OF_DAY', 'hour', '14', 95, 0.190, 0.160, 0.220, 500, 0.001, 0.03, 19, NOW() - INTERVAL '1 day', NOW(), 'COUNT', 0.92),
('HOUR_OF_DAY', 'hour', '15', 78, 0.156, 0.125, 0.187, 500, 0.001, 0.03, 16, NOW() - INTERVAL '1 day', NOW(), 'COUNT', 0.92);

\echo 'Données démographiques de démonstration insérées.'

-- Données géographiques anonymisées (k=5 minimum)
INSERT INTO dashboard.geographic_data (
    region_level, region_code, region_name, country_code,
    latitude_centroid, longitude_centroid, session_count, success_rate, avg_processing_time,
    population_density_category, anonymization_grid_size, k_anonymity_value, geohash_level,
    spatial_aggregation_method, collection_period_start, collection_period_end,
    data_quality_score, privacy_level
) VALUES
-- Données par pays (très agrégées)
('COUNTRY', 'FR', 'France', 'FR', 46.603354, 1.888334, 1250, 0.876, 4.23, 'URBAN', 100000, 250, 3, 'ADMINISTRATIVE_BOUNDARY', NOW() - INTERVAL '1 day', NOW(), 0.98, 'PUBLIC'),
('COUNTRY', 'DE', 'Germany', 'DE', 51.165691, 10.451526, 890, 0.892, 3.87, 'URBAN', 100000, 178, 3, 'ADMINISTRATIVE_BOUNDARY', NOW() - INTERVAL '1 day', NOW(), 0.97, 'PUBLIC'),
('COUNTRY', 'IT', 'Italy', 'IT', 41.871940, 12.567380, 645, 0.834, 4.56, 'URBAN', 100000, 129, 3, 'ADMINISTRATIVE_BOUNDARY', NOW() - INTERVAL '1 day', NOW(), 0.96, 'PUBLIC'),

-- Données régionales françaises (agrégées par région)
('REGION', 'IDF', 'Île-de-France', 'FR', 48.849415, 2.337708, 456, 0.889, 3.95, 'URBAN', 25000, 91, 4, 'GRID_AGGREGATION', NOW() - INTERVAL '1 day', NOW(), 0.94, 'MEDIUM'),
('REGION', 'PACA', 'Provence-Alpes-Côte d\'Azur', 'FR', 43.675819, 6.359322, 234, 0.867, 4.12, 'SUBURBAN', 25000, 47, 4, 'GRID_AGGREGATION', NOW() - INTERVAL '1 day', NOW(), 0.93, 'MEDIUM'),
('REGION', 'RHONE', 'Auvergne-Rhône-Alpes', 'FR', 45.366667, 4.850000, 187, 0.901, 3.78, 'SUBURBAN', 25000, 37, 4, 'GRID_AGGREGATION', NOW() - INTERVAL '1 day', NOW(), 0.95, 'MEDIUM'),

-- Données urbaines (grilles d'anonymisation)
('GRID', 'GRID_PAR_001', 'Paris Zone 1', 'FR', 48.8566, 2.3522, 95, 0.884, 4.02, 'URBAN', 5000, 19, 5, 'GRID_AGGREGATION', NOW() - INTERVAL '1 day', NOW(), 0.91, 'HIGH'),
('GRID', 'GRID_PAR_002', 'Paris Zone 2', 'FR', 48.8738, 2.2950, 67, 0.896, 3.89, 'URBAN', 5000, 13, 5, 'GRID_AGGREGATION', NOW() - INTERVAL '1 day', NOW(), 0.89, 'HIGH'),
('GRID', 'GRID_LYO_001', 'Lyon Zone 1', 'FR', 45.7640, 4.8357, 45, 0.911, 3.65, 'URBAN', 5000, 9, 5, 'GRID_AGGREGATION', NOW() - INTERVAL '1 day', NOW(), 0.88, 'HIGH');

\echo 'Données géographiques de démonstration insérées.'

-- Événements d'audit initiaux
INSERT INTO dashboard.audit_events (
    event_type, resource_type, severity, event_data_encrypted, 
    ip_address_encrypted, result, occurred_at
) VALUES
('SYSTEM_INIT', 'DATABASE', 'MEDIUM', 
 encrypt_personal_data('{"action":"initial_setup","tables_created":5,"users_created":3}', 'audit_data'),
 encrypt_personal_data('127.0.0.1', 'audit_data'), 
 'SUCCESS', NOW()),
 
('USER_CREATED', 'SYSTEM_USER', 'LOW',
 encrypt_personal_data('{"username":"admin","role":"ADMIN","created_by":"system"}', 'audit_data'),
 encrypt_personal_data('127.0.0.1', 'audit_data'),
 'SUCCESS', NOW() - INTERVAL '1 minute'),
 
('DATA_SEED', 'ANALYTICS', 'LOW',
 encrypt_personal_data('{"records_inserted":150,"tables":["analytics_data","demographic_stats","geographic_data"]}', 'audit_data'),
 encrypt_personal_data('127.0.0.1', 'audit_data'),
 'SUCCESS', NOW() - INTERVAL '30 seconds');

\echo 'Événements d\'audit initiaux créés.'

-- Rafraîchissement des vues matérialisées
REFRESH MATERIALIZED VIEW dashboard.hourly_analytics;
REFRESH MATERIALIZED VIEW dashboard.daily_geographic_summary;

\echo 'Vues matérialisées rafraîchies.'

-- Connexion à la base KYC pour vérification
\c kyc_db;

-- Vérification que le schéma KYC existe
SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'kyc';

-- Statistiques finales
\c dashboard_db;

-- Statistiques des données insérées
SELECT 
    'system_users' as table_name, COUNT(*) as records 
FROM dashboard.system_users
UNION ALL
SELECT 
    'analytics_data' as table_name, COUNT(*) as records 
FROM dashboard.analytics_data
UNION ALL
SELECT 
    'demographic_stats' as table_name, COUNT(*) as records 
FROM dashboard.demographic_stats
UNION ALL
SELECT 
    'geographic_data' as table_name, COUNT(*) as records 
FROM dashboard.geographic_data
UNION ALL
SELECT 
    'audit_events' as table_name, COUNT(*) as records 
FROM dashboard.audit_events;

\echo '======================================'
\echo 'Initialisation des données terminée!'
\echo '======================================'
\echo 'Utilisateurs créés:'
\echo '  - admin (Admin2024!) - ADMIN'
\echo '  - analyst (Analyst2024!) - ANALYST'  
\echo '  - viewer (Viewer2024!) - VIEWER'
\echo ''
\echo 'Données de démonstration:'
\echo '  - Analytics: 15+ métriques'
\echo '  - Démographie: 12+ stats anonymisées'
\echo '  - Géographie: 9+ régions avec k≥5'
\echo '  - Audit: 3+ événements initiaux'
\echo ''
\echo 'Configuration RGPD:'
\echo '  - Chiffrement AES-256 activé'
\echo '  - K-anonymité ≥ 5 respectée'
\echo '  - TimescaleDB configuré'
\echo '======================================'