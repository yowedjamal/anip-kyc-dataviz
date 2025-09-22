-- Script d'initialisation PostgreSQL + TimescaleDB pour ANIP
-- Ce script crée les bases de données et utilisateurs nécessaires

-- Créer les bases de données
CREATE DATABASE anip_kyc_operational;
CREATE DATABASE anip_analytics_anonymous;

-- Créer les utilisateurs avec privilèges appropriés
CREATE USER kyc_service_user WITH PASSWORD 'kyc_service_password_secure';
CREATE USER dashboard_service_user WITH PASSWORD 'dashboard_service_password_secure';

-- Accorder les privilèges
GRANT ALL PRIVILEGES ON DATABASE anip_kyc_operational TO kyc_service_user;
GRANT ALL PRIVILEGES ON DATABASE anip_analytics_anonymous TO dashboard_service_user;

-- Activer TimescaleDB sur les bases de données
\c anip_kyc_operational;
CREATE EXTENSION IF NOT EXISTS timescaledb;

\c anip_analytics_anonymous;
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- Extensions additionnelles pour l'analytique
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements";

-- Revenir à la base par défaut
\c anip_platform;