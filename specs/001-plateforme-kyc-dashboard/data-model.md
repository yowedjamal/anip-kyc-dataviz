# Data Model: Plateforme KYC & Dashboard Analytique ANIP

**Generated**: 19 septembre 2025  
**For**: Implementation planning Phase 1  
**Context**: Microservices architecture with PostgreSQL + TimescaleDB

## Entity Relationship Overview

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  Document       │    │   KycSession    │    │  SystemUser     │
│  Identité       │────│                 │────│                 │
│                 │    │                 │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  FaceMatch      │    │  LivenessResult │    │  AuditEvent     │
│                 │    │                 │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         │                       │                       │
         └───────────┬───────────┘                       │
                     ▼                                   ▼
         ┌─────────────────┐                ┌─────────────────┐
         │ AnalyticsData   │                │ DemographicStat │
         │ (Anonymized)    │────────────────│                 │
         └─────────────────┘                └─────────────────┘
                     │
                     ▼
         ┌─────────────────┐
         │ GeographicData  │
         │ (Anonymized)    │
         └─────────────────┘
```

## Core Business Entities

### Document (Document d'Identité)
**Purpose**: Représente les pièces officielles soumises pour vérification KYC  
**Service**: KYC Service (Spring Boot)  
**Storage**: PostgreSQL kyc_operational schema

```sql
CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES kyc_sessions(id),
    document_type VARCHAR(50) NOT NULL, -- 'NATIONAL_ID', 'PASSPORT', 'VOTER_CARD'
    document_number VARCHAR(100), -- Extracted by OCR
    issue_date DATE, -- Extracted by OCR
    expiry_date DATE, -- Extracted by OCR
    issuing_authority VARCHAR(200), -- Extracted by OCR
    document_image_path VARCHAR(500), -- MinIO path to encrypted image
    ocr_raw_text TEXT, -- Full OCR output encrypted
    ocr_confidence DECIMAL(3,2), -- 0.00 to 1.00
    validation_status VARCHAR(20) DEFAULT 'PENDING', -- 'PENDING', 'VALID', 'INVALID', 'MANUAL_REVIEW'
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_documents_session_id ON documents(session_id);
CREATE INDEX idx_documents_type ON documents(document_type);
CREATE INDEX idx_documents_status ON documents(validation_status);
```

**Validation Rules**:
- `document_type` must be in approved list from constitution
- `document_number` encrypted at rest (AES-256)
- `ocr_confidence` must be ≥ 0.70 for auto-validation
- `document_image_path` points to encrypted MinIO object

**State Transitions**:
```
PENDING → [OCR Processing] → {VALID, INVALID, MANUAL_REVIEW}
MANUAL_REVIEW → [Agent Review] → {VALID, INVALID}
```

### KycSession (Session KYC)
**Purpose**: Représente une tentative complète de vérification d'identité  
**Service**: KYC Service (Spring Boot)  
**Storage**: PostgreSQL kyc_operational schema

```sql
CREATE TABLE kyc_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES system_users(id),
    citizen_id VARCHAR(100), -- Encrypted reference to citizen
    session_status VARCHAR(20) DEFAULT 'INITIATED', -- 'INITIATED', 'DOCUMENTS_UPLOADED', 'FACE_VERIFIED', 'COMPLETED', 'FAILED'
    overall_confidence DECIMAL(3,2), -- Combined confidence score
    processing_duration_ms INTEGER, -- For performance monitoring
    failure_reason TEXT, -- If status = 'FAILED'
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_kyc_sessions_user_id ON kyc_sessions(user_id);
CREATE INDEX idx_kyc_sessions_status ON kyc_sessions(session_status);
CREATE INDEX idx_kyc_sessions_created_at ON kyc_sessions(created_at);
```

**Validation Rules**:
- Must have at least one `Document` before moving to 'DOCUMENTS_UPLOADED'
- `processing_duration_ms` must be ≤ 5000 (5 seconds per constitution)
- `overall_confidence` calculated from document OCR + face match + liveness scores
- `citizen_id` encrypted with AES-256

### FaceMatch (Correspondance Faciale)
**Purpose**: Résultats de vérification correspondance selfie vs document  
**Service**: KYC Service (Spring Boot)  
**Storage**: PostgreSQL kyc_operational schema

```sql
CREATE TABLE face_matches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES kyc_sessions(id),
    document_id UUID NOT NULL REFERENCES documents(id),
    selfie_image_path VARCHAR(500), -- MinIO path to encrypted selfie
    similarity_score DECIMAL(5,3), -- 0.000 to 1.000
    match_status VARCHAR(20) DEFAULT 'PENDING', -- 'PENDING', 'MATCH', 'NO_MATCH', 'MANUAL_REVIEW'
    processing_algorithm VARCHAR(50), -- 'DEEPFACE_VGG', 'DEEPFACE_FACENET', etc.
    face_detection_confidence DECIMAL(3,2), -- Face detection confidence
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_face_matches_session_id ON face_matches(session_id);
CREATE INDEX idx_face_matches_status ON face_matches(match_status);
```

**Validation Rules**:
- `similarity_score` ≥ 0.85 = automatic 'MATCH'
- `similarity_score` 0.70-0.84 = 'MANUAL_REVIEW'
- `similarity_score` < 0.70 = 'NO_MATCH'
- `selfie_image_path` points to encrypted MinIO object

### LivenessResult (Résultat Détection Vivacité)
**Purpose**: Résultats de détection anti-spoofing liveness  
**Service**: KYC Service (Spring Boot)  
**Storage**: PostgreSQL kyc_operational schema

```sql
CREATE TABLE liveness_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES kyc_sessions(id),
    liveness_video_path VARCHAR(500), -- MinIO path to encrypted video
    liveness_score DECIMAL(3,2), -- 0.00 to 1.00
    liveness_status VARCHAR(20) DEFAULT 'PENDING', -- 'PENDING', 'LIVE', 'SPOOF', 'MANUAL_REVIEW'
    detection_method VARCHAR(100), -- 'INSIGHTFACE_ANTI_SPOOFING', 'TEXTURE_ANALYSIS', etc.
    eye_blink_detected BOOLEAN DEFAULT FALSE,
    head_movement_detected BOOLEAN DEFAULT FALSE,
    texture_analysis_score DECIMAL(3,2),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_liveness_results_session_id ON liveness_results(session_id);
CREATE INDEX idx_liveness_results_status ON liveness_results(liveness_status);
```

**Validation Rules**:
- `liveness_score` ≥ 0.80 AND (`eye_blink_detected` OR `head_movement_detected`) = 'LIVE'
- `liveness_score` < 0.60 = 'SPOOF'
- Between 0.60-0.79 = 'MANUAL_REVIEW'

## User Management Entities

### SystemUser (Utilisateur Système)
**Purpose**: Agents, analystes et administrateurs ANIP avec rôles/permissions  
**Service**: Dashboard Service (Laravel)  
**Storage**: PostgreSQL kyc_operational schema

```sql
CREATE TABLE system_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_user_id VARCHAR(100) UNIQUE NOT NULL, -- Reference to Keycloak
    email VARCHAR(255) UNIQUE NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    role VARCHAR(50) NOT NULL, -- 'AGENT_KYC', 'ANALYST_DATA', 'ADMIN_SYSTEM'
    department VARCHAR(100),
    is_active BOOLEAN DEFAULT TRUE,
    last_login_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_system_users_keycloak_id ON system_users(keycloak_user_id);
CREATE INDEX idx_system_users_role ON system_users(role);
CREATE INDEX idx_system_users_active ON system_users(is_active);
```

**Validation Rules**:
- `role` must be one of constitutional roles
- `keycloak_user_id` synchronized with Keycloak realm
- `email` must be unique across system

## Audit & Compliance Entities

### AuditEvent (Événement d'Audit)
**Purpose**: Traçabilité complète actions utilisateurs pour conformité  
**Service**: Dashboard Service (Laravel)  
**Storage**: PostgreSQL kyc_operational schema + TimescaleDB extension

```sql
CREATE TABLE audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES system_users(id),
    event_type VARCHAR(100) NOT NULL, -- 'LOGIN', 'KYC_SESSION_CREATED', 'DOCUMENT_REVIEWED', etc.
    entity_type VARCHAR(50), -- 'KYC_SESSION', 'DOCUMENT', 'USER', etc.
    entity_id UUID, -- Reference to affected entity
    action VARCHAR(50) NOT NULL, -- 'CREATE', 'READ', 'UPDATE', 'DELETE'
    old_values JSONB, -- Previous state for updates
    new_values JSONB, -- New state for creates/updates
    ip_address INET,
    user_agent TEXT,
    session_id VARCHAR(100), -- HTTP session ID
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Convert to TimescaleDB hypertable for time-series optimization
SELECT create_hypertable('audit_events', 'created_at', chunk_time_interval => INTERVAL '1 day');

CREATE INDEX idx_audit_events_user_id ON audit_events(user_id);
CREATE INDEX idx_audit_events_type ON audit_events(event_type);
CREATE INDEX idx_audit_events_entity ON audit_events(entity_type, entity_id);
```

**Retention Policy**:
- Legal retention: 7 years as per constitution
- Automatic compression: 30 days
- Automatic deletion: After 7 years

## Analytics Entities (Anonymized)

### AnalyticsData (Données Analytiques Anonymisées)
**Purpose**: Métriques anonymisées pour dashboard business intelligence  
**Service**: Dashboard Service (Laravel)  
**Storage**: PostgreSQL analytics_anonymous schema + TimescaleDB

```sql
CREATE SCHEMA analytics_anonymous;

CREATE TABLE analytics_anonymous.kyc_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    metric_date DATE NOT NULL,
    region_code VARCHAR(10), -- Generalized region (not specific location)
    age_group VARCHAR(20), -- '18-25', '26-35', '36-50', '51+'
    gender VARCHAR(10), -- 'M', 'F', 'OTHER' (if provided voluntarily)
    document_type VARCHAR(50),
    total_sessions INTEGER DEFAULT 0,
    successful_sessions INTEGER DEFAULT 0,
    failed_sessions INTEGER DEFAULT 0,
    avg_processing_time_ms DECIMAL(8,2),
    fraud_attempts INTEGER DEFAULT 0,
    manual_reviews INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Convert to TimescaleDB hypertable
SELECT create_hypertable('analytics_anonymous.kyc_metrics', 'metric_date', chunk_time_interval => INTERVAL '7 days');

CREATE INDEX idx_kyc_metrics_date ON analytics_anonymous.kyc_metrics(metric_date);
CREATE INDEX idx_kyc_metrics_region ON analytics_anonymous.kyc_metrics(region_code);
```

**Anonymization Rules**:
- k-anonymity: k≥5 (minimum 5 individuals per group)
- Differential privacy: Noise added to counts
- Geographical generalization: Region level only (no city/address)
- Temporal aggregation: Daily minimum granularity

### DemographicStat (Statistiques Démographiques)
**Purpose**: Répartition démographique anonymisée pour analyses tendances  
**Service**: Dashboard Service (Laravel)  
**Storage**: PostgreSQL analytics_anonymous schema

```sql
CREATE TABLE analytics_anonymous.demographic_stats (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporting_period VARCHAR(20), -- 'DAILY', 'WEEKLY', 'MONTHLY'
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    region_code VARCHAR(10),
    total_population INTEGER,
    age_distribution JSONB, -- {"18-25": 1500, "26-35": 2300, ...}
    gender_distribution JSONB, -- {"M": 2800, "F": 2650, "OTHER": 50}
    document_type_distribution JSONB, -- {"NATIONAL_ID": 4200, "PASSPORT": 800, ...}
    success_rate_by_age JSONB,
    success_rate_by_gender JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_demographic_stats_period ON analytics_anonymous.demographic_stats(reporting_period, period_start);
```

### GeographicData (Données Géographiques Anonymisées)
**Purpose**: Données géospatiales anonymisées pour cartes interactives  
**Service**: Dashboard Service (Laravel)  
**Storage**: PostgreSQL analytics_anonymous schema

```sql
CREATE TABLE analytics_anonymous.geographic_data (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    region_code VARCHAR(10) NOT NULL,
    region_name VARCHAR(100) NOT NULL,
    country_code VARCHAR(3) DEFAULT 'MLI', -- Mali ISO code
    center_latitude DECIMAL(10,7), -- Generalized center point
    center_longitude DECIMAL(10,7), -- Generalized center point
    total_registrations INTEGER DEFAULT 0,
    success_rate DECIMAL(5,2), -- Percentage
    fraud_rate DECIMAL(5,2), -- Percentage
    avg_processing_time_ms DECIMAL(8,2),
    last_updated DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_geographic_data_region ON analytics_anonymous.geographic_data(region_code);
```

**Geospatial Anonymization**:
- Location precision: Regional level only (no GPS coordinates)
- Minimum aggregation: 100+ individuals per region
- Noise addition: ±5km random offset for center points

## Data Flow & ETL Pipeline

### Operational → Analytics ETL
```sql
-- ETL Job runs nightly to anonymize operational data
-- Implemented in Dashboard Service (Laravel Jobs)

-- 1. Extract from operational KYC data
-- 2. Transform with anonymization:
--    - Remove all PII (names, addresses, exact locations)
--    - Generalize (specific age → age group)
--    - Add noise (differential privacy)
--    - Aggregate (k-anonymity compliance)
-- 3. Load into analytics_anonymous schema
```

## Relationships & Constraints

### Foreign Key Relationships
- `Document.session_id` → `KycSession.id` (1:N)
- `FaceMatch.session_id` → `KycSession.id` (1:1)
- `LivenessResult.session_id` → `KycSession.id` (1:1)
- `KycSession.user_id` → `SystemUser.id` (N:1)
- `AuditEvent.user_id` → `SystemUser.id` (N:1)

### Data Integrity Constraints
- All timestamps use UTC with timezone awareness
- All UUIDs generated server-side for security
- Encrypted fields marked for application-level encryption
- Cascading deletes prevented (audit trail preservation)

### Performance Considerations
- Indexes on frequently queried columns
- TimescaleDB chunks for time-series optimization
- Partitioning large tables by date ranges
- Connection pooling configured per microservice

## Security & Encryption

### Column-Level Encryption (AES-256)
- `documents.document_number`
- `documents.ocr_raw_text`
- `kyc_sessions.citizen_id`
- `face_matches.selfie_image_path`
- `liveness_results.liveness_video_path`

### Data Masking Rules
- Development/staging environments use masked data
- Production-like volume with anonymized content
- Sensitive fields replaced with synthetic data

## Migration Strategy

### Phase 1: Core Tables
1. `system_users` (foundation)
2. `kyc_sessions` (core workflow)
3. `documents` (primary entity)

### Phase 2: Verification Tables
4. `face_matches` (facial verification)
5. `liveness_results` (anti-spoofing)

### Phase 3: Analytics Tables
6. `audit_events` (compliance)
7. `analytics_anonymous.kyc_metrics` (business intelligence)
8. `analytics_anonymous.demographic_stats`
9. `analytics_anonymous.geographic_data`

### Phase 4: Indexes & Optimization
- Create performance indexes
- Setup TimescaleDB hypertables
- Configure retention policies

**Ready for**: Contract generation and API design