-- V1__Initial_Schema.sql
-- Migration initiale pour le service KYC avec PostgreSQL
-- Conforme aux spécifications data-model.md et constitution.md

-- Extension pour UUID
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Extension pour chiffrement
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Schema principal
CREATE SCHEMA IF NOT EXISTS kyc;
SET search_path TO kyc;

-- Enum pour les types de documents
CREATE TYPE document_type AS ENUM (
    'PASSPORT', 
    'ID_CARD', 
    'DRIVING_LICENSE'
);

-- Enum pour les statuts de session
CREATE TYPE session_status AS ENUM (
    'CREATED', 
    'DOCUMENT_UPLOADED', 
    'DOCUMENT_VERIFIED', 
    'FACE_CAPTURED', 
    'FACE_VERIFIED', 
    'COMPLETED', 
    'FAILED', 
    'EXPIRED'
);

-- Enum pour les résultats de vérification
CREATE TYPE verification_result AS ENUM (
    'SUCCESS', 
    'FAILED', 
    'PENDING', 
    'ERROR'
);

-- Enum pour les types de liveness
CREATE TYPE liveness_type AS ENUM (
    'PASSIVE', 
    'ACTIVE_BLINK', 
    'ACTIVE_TURN_HEAD', 
    'ACTIVE_SMILE', 
    'CHALLENGE_RESPONSE'
);

-- Table: kyc_sessions
-- Stockage des sessions KYC avec chiffrement des données sensibles
CREATE TABLE kyc_sessions (
    session_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id_hash BYTEA NOT NULL, -- Hash SHA-256 de l'ID utilisateur
    status session_status NOT NULL DEFAULT 'CREATED',
    client_ip_encrypted BYTEA, -- IP chiffrée AES-256
    user_agent_hash BYTEA, -- Hash du User-Agent
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    verification_result verification_result,
    confidence_score DECIMAL(5,4) CHECK (confidence_score >= 0.0 AND confidence_score <= 1.0),
    session_metadata_encrypted BYTEA, -- Métadonnées chiffrées (JSON)
    processing_time_ms INTEGER CHECK (processing_time_ms >= 0),
    failure_reason TEXT,
    
    -- Index pour recherche rapide
    CONSTRAINT kyc_sessions_valid_completion 
        CHECK ((status = 'COMPLETED' AND completed_at IS NOT NULL) 
               OR (status != 'COMPLETED' AND completed_at IS NULL))
);

-- Index sur les colonnes de recherche fréquente
CREATE INDEX idx_kyc_sessions_status ON kyc_sessions(status);
CREATE INDEX idx_kyc_sessions_created_at ON kyc_sessions(created_at);
CREATE INDEX idx_kyc_sessions_user_hash ON kyc_sessions(user_id_hash);
CREATE INDEX idx_kyc_sessions_expires_at ON kyc_sessions(expires_at);

-- Table: documents
-- Stockage des documents avec métadonnées de sécurité
CREATE TABLE documents (
    document_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id UUID NOT NULL REFERENCES kyc_sessions(session_id) ON DELETE CASCADE,
    document_type document_type NOT NULL,
    file_path_encrypted BYTEA NOT NULL, -- Chemin de fichier chiffré
    file_hash SHA256 NOT NULL UNIQUE, -- Hash SHA-256 du fichier original
    file_size_bytes BIGINT NOT NULL CHECK (file_size_bytes > 0),
    mime_type VARCHAR(100) NOT NULL,
    upload_timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    extraction_confidence DECIMAL(5,4) CHECK (extraction_confidence >= 0.0 AND extraction_confidence <= 1.0),
    ocr_text_encrypted BYTEA, -- Texte OCR chiffré
    extracted_data_encrypted BYTEA, -- Données extraites chiffrées (JSON)
    validation_status verification_result DEFAULT 'PENDING',
    validation_errors TEXT[], -- Array d'erreurs de validation
    processing_duration_ms INTEGER CHECK (processing_duration_ms >= 0),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index pour recherche et performances
CREATE INDEX idx_documents_session_id ON documents(session_id);
CREATE INDEX idx_documents_type ON documents(document_type);
CREATE INDEX idx_documents_validation_status ON documents(validation_status);
CREATE INDEX idx_documents_upload_timestamp ON documents(upload_timestamp);

-- Table: face_matches
-- Résultats de comparaison faciale avec métriques de confiance
CREATE TABLE face_matches (
    match_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id UUID NOT NULL REFERENCES kyc_sessions(session_id) ON DELETE CASCADE,
    document_id UUID NOT NULL REFERENCES documents(document_id) ON DELETE CASCADE,
    reference_face_hash BYTEA NOT NULL, -- Hash de l'image de référence
    live_face_hash BYTEA NOT NULL, -- Hash de l'image live
    similarity_score DECIMAL(8,6) NOT NULL CHECK (similarity_score >= 0.0 AND similarity_score <= 1.0),
    match_result verification_result NOT NULL DEFAULT 'PENDING',
    confidence_level DECIMAL(5,4) CHECK (confidence_level >= 0.0 AND confidence_level <= 1.0),
    algorithm_used VARCHAR(50) NOT NULL DEFAULT 'FaceNet',
    algorithm_version VARCHAR(20) NOT NULL DEFAULT '1.0',
    face_landmarks_encrypted BYTEA, -- Points caractéristiques chiffrés
    quality_score DECIMAL(5,4) CHECK (quality_score >= 0.0 AND quality_score <= 1.0),
    anti_spoofing_score DECIMAL(5,4) CHECK (anti_spoofing_score >= 0.0 AND anti_spoofing_score <= 1.0),
    processing_time_ms INTEGER CHECK (processing_time_ms >= 0),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- Contrainte d'unicité par session
    UNIQUE(session_id, document_id)
);

-- Index pour recherche et performances
CREATE INDEX idx_face_matches_session_id ON face_matches(session_id);
CREATE INDEX idx_face_matches_document_id ON face_matches(document_id);
CREATE INDEX idx_face_matches_result ON face_matches(match_result);
CREATE INDEX idx_face_matches_similarity_score ON face_matches(similarity_score);

-- Table: liveness_results
-- Résultats des tests de vivacité (anti-spoofing)
CREATE TABLE liveness_results (
    liveness_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id UUID NOT NULL REFERENCES kyc_sessions(session_id) ON DELETE CASCADE,
    liveness_type liveness_type NOT NULL,
    is_live BOOLEAN NOT NULL,
    confidence_score DECIMAL(8,6) NOT NULL CHECK (confidence_score >= 0.0 AND confidence_score <= 1.0),
    anti_spoofing_score DECIMAL(8,6) CHECK (anti_spoofing_score >= 0.0 AND anti_spoofing_score <= 1.0),
    quality_checks_passed INTEGER DEFAULT 0,
    quality_checks_total INTEGER DEFAULT 0,
    challenge_response_data_encrypted BYTEA, -- Données de défi chiffrées
    biometric_template_hash BYTEA, -- Hash du template biométrique
    processing_algorithm VARCHAR(50) NOT NULL DEFAULT 'DeepFace',
    algorithm_version VARCHAR(20) NOT NULL DEFAULT '1.0',
    device_info_encrypted BYTEA, -- Informations appareil chiffrées
    processing_time_ms INTEGER CHECK (processing_time_ms >= 0),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- Contrainte de cohérence qualité
    CONSTRAINT liveness_results_quality_check 
        CHECK (quality_checks_passed <= quality_checks_total)
);

-- Index pour recherche et performances  
CREATE INDEX idx_liveness_results_session_id ON liveness_results(session_id);
CREATE INDEX idx_liveness_results_type ON liveness_results(liveness_type);
CREATE INDEX idx_liveness_results_is_live ON liveness_results(is_live);
CREATE INDEX idx_liveness_results_confidence ON liveness_results(confidence_score);

-- Vue pour statistiques de performance (anonymisée)
CREATE VIEW kyc_performance_stats AS
SELECT 
    date_trunc('hour', created_at) as hour_bucket,
    status,
    COUNT(*) as session_count,
    AVG(confidence_score) as avg_confidence,
    AVG(processing_time_ms) as avg_processing_time_ms,
    COUNT(*) FILTER (WHERE verification_result = 'SUCCESS') as success_count,
    COUNT(*) FILTER (WHERE verification_result = 'FAILED') as failure_count
FROM kyc_sessions 
WHERE created_at >= CURRENT_TIMESTAMP - INTERVAL '30 days'
GROUP BY date_trunc('hour', created_at), status;

-- Trigger pour mise à jour automatique des timestamps
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_kyc_sessions_updated_at 
    BEFORE UPDATE ON kyc_sessions 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_documents_updated_at 
    BEFORE UPDATE ON documents 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Fonction pour nettoyage automatique des sessions expirées
CREATE OR REPLACE FUNCTION cleanup_expired_sessions()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM kyc_sessions 
    WHERE status NOT IN ('COMPLETED', 'FAILED') 
    AND expires_at < CURRENT_TIMESTAMP;
    
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    
    -- Log de nettoyage
    INSERT INTO maintenance_log (operation, affected_rows, executed_at)
    VALUES ('cleanup_expired_sessions', deleted_count, CURRENT_TIMESTAMP);
    
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- Table de log pour maintenance
CREATE TABLE maintenance_log (
    log_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    operation VARCHAR(100) NOT NULL,
    affected_rows INTEGER DEFAULT 0,
    executed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    details TEXT
);

-- Politique de sécurité Row Level Security (RLS)
ALTER TABLE kyc_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE face_matches ENABLE ROW LEVEL SECURITY;
ALTER TABLE liveness_results ENABLE ROW LEVEL SECURITY;

-- Politique par défaut : accès via application uniquement
CREATE POLICY kyc_sessions_app_access ON kyc_sessions
    FOR ALL TO kyc_application_role
    USING (true)
    WITH CHECK (true);

CREATE POLICY documents_app_access ON documents
    FOR ALL TO kyc_application_role
    USING (true)
    WITH CHECK (true);

CREATE POLICY face_matches_app_access ON face_matches
    FOR ALL TO kyc_application_role
    USING (true)
    WITH CHECK (true);

CREATE POLICY liveness_results_app_access ON liveness_results
    FOR ALL TO kyc_application_role
    USING (true)
    WITH CHECK (true);

-- Rôles et permissions
CREATE ROLE IF NOT EXISTS kyc_application_role;
CREATE ROLE IF NOT EXISTS kyc_readonly_role;

-- Permissions pour l'application
GRANT USAGE ON SCHEMA kyc TO kyc_application_role;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA kyc TO kyc_application_role;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA kyc TO kyc_application_role;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA kyc TO kyc_application_role;

-- Permissions lecture seule
GRANT USAGE ON SCHEMA kyc TO kyc_readonly_role;
GRANT SELECT ON ALL TABLES IN SCHEMA kyc TO kyc_readonly_role;
GRANT SELECT ON kyc_performance_stats TO kyc_readonly_role;

-- Fonction de chiffrement personnalisée pour données sensibles
CREATE OR REPLACE FUNCTION encrypt_sensitive_data(data TEXT, key_id UUID DEFAULT NULL)
RETURNS BYTEA AS $$
BEGIN
    -- Utilise une clé spécifique ou la clé par défaut
    RETURN pgp_sym_encrypt(data, 
        COALESCE(
            (SELECT encryption_key FROM encryption_keys WHERE id = key_id), 
            current_setting('app.default_encryption_key')
        )
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Fonction de déchiffrement
CREATE OR REPLACE FUNCTION decrypt_sensitive_data(encrypted_data BYTEA, key_id UUID DEFAULT NULL)
RETURNS TEXT AS $$
BEGIN
    RETURN pgp_sym_decrypt(encrypted_data, 
        COALESCE(
            (SELECT encryption_key FROM encryption_keys WHERE id = key_id), 
            current_setting('app.default_encryption_key')
        )
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Table pour gestion des clés de chiffrement
CREATE TABLE encryption_keys (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    key_name VARCHAR(100) NOT NULL UNIQUE,
    encryption_key TEXT NOT NULL, -- Stocké de manière sécurisée
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT true,
    rotation_date TIMESTAMP WITH TIME ZONE
);

-- Index pour recherche de clés
CREATE INDEX idx_encryption_keys_name ON encryption_keys(key_name);
CREATE INDEX idx_encryption_keys_active ON encryption_keys(is_active);

-- Commentaires pour documentation
COMMENT ON TABLE kyc_sessions IS 'Sessions KYC avec données utilisateur anonymisées et chiffrées';
COMMENT ON TABLE documents IS 'Documents uploadés avec validation OCR et métadonnées sécurisées';
COMMENT ON TABLE face_matches IS 'Résultats de comparaison faciale avec métriques de confiance';
COMMENT ON TABLE liveness_results IS 'Tests de vivacité anti-spoofing avec templates biométriques';
COMMENT ON VIEW kyc_performance_stats IS 'Statistiques de performance anonymisées pour monitoring';

-- Configuration par défaut
SET search_path TO kyc, public;