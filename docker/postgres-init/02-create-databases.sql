#!/bin/bash
# 02-create-databases.sql
# Création des bases de données et utilisateurs pour les microservices

\echo 'Création des bases de données et utilisateurs...'

-- Base de données pour le service KYC
CREATE DATABASE kyc_db WITH 
    OWNER = postgres
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE = 'en_US.utf8'
    TEMPLATE = template0;

-- Base de données pour le service Dashboard
CREATE DATABASE dashboard_db WITH 
    OWNER = postgres
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE = 'en_US.utf8'
    TEMPLATE = template0;

-- Utilisateur pour le service KYC
CREATE USER kyc_user WITH 
    PASSWORD 'kyc_secure_password_2024!';

-- Utilisateur pour le service Dashboard
CREATE USER dashboard_user WITH 
    PASSWORD 'dashboard_secure_password_2024!';

-- Utilisateur en lecture seule pour monitoring
CREATE USER monitoring_user WITH 
    PASSWORD 'monitoring_readonly_2024!';

-- Utilisateur pour les sauvegardes
CREATE USER backup_user WITH 
    PASSWORD 'backup_secure_2024!'
    REPLICATION;

\echo 'Bases de données et utilisateurs créés.'

-- Connexion à la base KYC pour configuration
\c kyc_db;

-- Attribution des permissions sur kyc_db
GRANT CONNECT ON DATABASE kyc_db TO kyc_user;
GRANT CONNECT ON DATABASE kyc_db TO monitoring_user;
GRANT CONNECT ON DATABASE kyc_db TO backup_user;

-- Création du schéma et permissions
CREATE SCHEMA IF NOT EXISTS kyc AUTHORIZATION kyc_user;
GRANT CREATE ON SCHEMA kyc TO kyc_user;
GRANT USAGE ON SCHEMA kyc TO monitoring_user;

-- Permissions par défaut pour nouvelles tables
ALTER DEFAULT PRIVILEGES IN SCHEMA kyc 
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO kyc_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA kyc 
    GRANT SELECT ON TABLES TO monitoring_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA kyc 
    GRANT USAGE, SELECT ON SEQUENCES TO kyc_user;

\echo 'Permissions KYC configurées.'

-- Connexion à la base Dashboard pour configuration
\c dashboard_db;

-- Attribution des permissions sur dashboard_db
GRANT CONNECT ON DATABASE dashboard_db TO dashboard_user;
GRANT CONNECT ON DATABASE dashboard_db TO monitoring_user;
GRANT CONNECT ON DATABASE dashboard_db TO backup_user;

-- Création du schéma et permissions
CREATE SCHEMA IF NOT EXISTS dashboard AUTHORIZATION dashboard_user;
GRANT CREATE ON SCHEMA dashboard TO dashboard_user;
GRANT USAGE ON SCHEMA dashboard TO monitoring_user;

-- Activation TimescaleDB sur dashboard_db
CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;

-- Permissions par défaut pour nouvelles tables
ALTER DEFAULT PRIVILEGES IN SCHEMA dashboard 
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO dashboard_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA dashboard 
    GRANT SELECT ON TABLES TO monitoring_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA dashboard 
    GRANT USAGE, SELECT ON SEQUENCES TO dashboard_user;

\echo 'Permissions Dashboard configurées.'

-- Retour à la base postgres pour configuration globale
\c postgres;

-- Rôles applicatifs
CREATE ROLE kyc_application_role;
CREATE ROLE dashboard_application_role;
CREATE ROLE readonly_role;
CREATE ROLE backup_role;

-- Attribution des rôles
GRANT kyc_application_role TO kyc_user;
GRANT dashboard_application_role TO dashboard_user;
GRANT readonly_role TO monitoring_user;
GRANT backup_role TO backup_user;

-- Permissions globales de monitoring
GRANT pg_monitor TO monitoring_user;
GRANT pg_read_all_stats TO monitoring_user;
GRANT pg_read_all_settings TO monitoring_user;

\echo 'Configuration des rôles terminée.'

-- Configuration de sécurité avancée
-- Limitation des connexions par utilisateur
ALTER USER kyc_user CONNECTION LIMIT 50;
ALTER USER dashboard_user CONNECTION LIMIT 50;
ALTER USER monitoring_user CONNECTION LIMIT 10;
ALTER USER backup_user CONNECTION LIMIT 5;

-- Timeout de session
ALTER USER kyc_user SET statement_timeout = '30s';
ALTER USER dashboard_user SET statement_timeout = '30s';
ALTER USER monitoring_user SET statement_timeout = '10s';

-- Configuration search_path par défaut
ALTER USER kyc_user SET search_path = 'kyc, public';
ALTER USER dashboard_user SET search_path = 'dashboard, public';

\echo 'Configuration de sécurité appliquée.'

-- Vérification des extensions installées
SELECT extname, extversion FROM pg_extension WHERE extname IN (
    'uuid-ossp', 'pgcrypto', 'timescaledb', 'pg_trgm', 'btree_gist'
);

-- Vérification des bases créées
SELECT datname, encoding, datcollate, datctype FROM pg_database 
WHERE datname IN ('kyc_db', 'dashboard_db');

-- Vérification des utilisateurs créés
SELECT usename, usesuper, usecreatedb, usecanlogin, valuntil, useconnlimit 
FROM pg_user 
WHERE usename IN ('kyc_user', 'dashboard_user', 'monitoring_user', 'backup_user');

\echo 'Initialisation PostgreSQL terminée avec succès!'
\echo 'Bases de données: kyc_db, dashboard_db'
\echo 'Utilisateurs: kyc_user, dashboard_user, monitoring_user, backup_user'
\echo 'Extensions: uuid-ossp, pgcrypto, timescaledb, pg_trgm, btree_gist'