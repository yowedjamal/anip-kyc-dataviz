#!/bin/bash
# 01-init-extensions.sql
# Script d'initialisation PostgreSQL avec extensions nécessaires
# Exécuté automatiquement par le container PostgreSQL au premier démarrage

# Configuration de base
\echo 'Initialisation des extensions PostgreSQL pour KYC & Dashboard...'

-- Configuration des logs
SET log_statement = 'all';
SET log_duration = on;

-- Extension UUID pour génération d'identifiants
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Extension pour le chiffrement symétrique
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Extension TimescaleDB pour les séries temporelles
CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;

-- Extension pour recherche textuelle
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Extension pour fonctions de hachage
CREATE EXTENSION IF NOT EXISTS "btree_gist";

-- Extension pour support JSON avancé
CREATE EXTENSION IF NOT EXISTS "hstore";

-- Extension PostGIS si nécessaire pour géolocalisation avancée
-- CREATE EXTENSION IF NOT EXISTS postgis;

\echo 'Extensions PostgreSQL installées avec succès.'

-- Configuration des paramètres de performance
ALTER SYSTEM SET shared_preload_libraries = 'timescaledb';
ALTER SYSTEM SET max_connections = 200;
ALTER SYSTEM SET shared_buffers = '256MB';
ALTER SYSTEM SET effective_cache_size = '1GB';
ALTER SYSTEM SET maintenance_work_mem = '64MB';
ALTER SYSTEM SET checkpoint_completion_target = 0.9;
ALTER SYSTEM SET wal_buffers = '16MB';
ALTER SYSTEM SET default_statistics_target = 100;
ALTER SYSTEM SET random_page_cost = 1.1;
ALTER SYSTEM SET effective_io_concurrency = 200;
ALTER SYSTEM SET work_mem = '4MB';

-- Configuration spécifique TimescaleDB
ALTER SYSTEM SET timescaledb.max_background_workers = 8;
ALTER SYSTEM SET timescaledb.restoring = 'off';

-- Configuration de sécurité
ALTER SYSTEM SET ssl = 'on';
ALTER SYSTEM SET log_connections = 'on';
ALTER SYSTEM SET log_disconnections = 'on';
ALTER SYSTEM SET log_checkpoints = 'on';
ALTER SYSTEM SET log_lock_waits = 'on';
ALTER SYSTEM SET log_temp_files = 0;
ALTER SYSTEM SET track_activity_query_size = 2048;

\echo 'Configuration PostgreSQL optimisée pour KYC et TimescaleDB.'